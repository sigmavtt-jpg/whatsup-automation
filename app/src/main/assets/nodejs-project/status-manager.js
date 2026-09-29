/**
 * WhatsApp Automation Engine — Status Manager
 * Handles WhatsApp Status Stories (status@broadcast), status key persistence,
 * status viewing, and strict single-JID status reactions.
 *
 * Enforces AGENTS.md Section 8 (Status & Privacy Shield):
 * - Dumb Bridge Architecture: No autonomous reactions, purely executes explicit commands.
 * - Strict Single-JID Routing: Reactions sent EXCLUSIVELY to 'status@broadcast' with { statusJidList: [targetParticipant] }.
 * - ZERO DM SIDE-EFFECTS: Under no circumstances are status reactions forwarded or fallen back to private 1-on-1 chats.
 */

import fs from 'fs';
import { jidNormalizedUser } from '@whiskeysockets/baileys';
import { CONFIG, getStatusKeysFilePath, sentMessagesCache, rawMessagesCache } from './config.js';
import { emitToAndroid } from './bridge.js';
import { resolvePhoneAndLid, getLidForPhone, isLidJidOrNumber, getContactName } from './lid-resolver.js';
import { createTrace, recordStage, printTimelineReport } from './tracer.js';

export const statusKeysMap = new Map();

/**
 * Loads cached status keys from disk.
 */
export function loadStatusKeys(authFolder) {
    if (!authFolder) return;
    try {
        const filePath = getStatusKeysFilePath(authFolder);
        if (fs.existsSync(filePath)) {
            const raw = fs.readFileSync(filePath, 'utf8');
            const data = JSON.parse(raw);
            for (const [k, v] of Object.entries(data)) {
                statusKeysMap.set(k, v);
            }
        }
    } catch (_) {}
}

/**
 * Saves status keys to disk.
 */
export function saveStatusKeys(authFolder) {
    if (!authFolder) return;
    try {
        const filePath = getStatusKeysFilePath(authFolder);
        const obj = {};
        for (const [k, v] of statusKeysMap.entries()) {
            obj[k] = v;
        }
        fs.writeFileSync(filePath, JSON.stringify(obj), 'utf8');
    } catch (_) {}
}

/**
 * Processes an incoming status story message and notifies Android.
 */
export async function processStatusMessage(msg, authFolder = CONFIG.DEFAULT_AUTH_FOLDER, sock = null) {
    if (!msg || !msg.key || !msg.message) return;
    if (msg.key.remoteJid !== 'status@broadcast') return;
    if (msg.key.fromMe) return;

    const senderJid = msg.key.participant || msg.key.remoteJid || '';
    const cleanRawPhone = senderJid.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
    if (!cleanRawPhone || cleanRawPhone === 'status') return;

    const statusId = msg.key.id;
    if (statusId) {
        const trace = createTrace(statusId, {
            participant: msg.key.participant || senderJid,
            remoteJid: msg.key.remoteJid,
            pushName: msg.pushName || ''
        });

        recordStage(statusId, 'STATUS_RECEIVED', 'status-manager.js', 'processStatusMessage', {
            statusId,
            remoteJid: msg.key.remoteJid,
            participant: msg.key.participant || senderJid,
            fromMe: msg.key.fromMe || false,
            hasMessage: !!msg.message
        });

        rawMessagesCache.set(statusId, msg.message);
        statusKeysMap.set(statusId, {
            remoteJid: msg.key.remoteJid,
            id: statusId,
            participant: msg.key.participant || senderJid,
            fromMe: msg.key.fromMe || false,
            pushName: msg.pushName || ''
        });

        // Retain at most MAX_STATUS_KEYS entries
        if (statusKeysMap.size > CONFIG.MAX_STATUS_KEYS) {
            const firstKey = statusKeysMap.keys().next().value;
            statusKeysMap.delete(firstKey);
        }
        saveStatusKeys(authFolder);

        recordStage(statusId, 'STATUS_STORED', 'status-manager.js', 'saveStatusKeys', {
            statusId,
            totalStored: statusKeysMap.size
        });
    }

    const resolved = await resolvePhoneAndLid(senderJid, msg, sock);
    const resolvedPhone = resolved.realPhone || resolved.phone;

    const text = msg.message.conversation ||
                 msg.message.extendedTextMessage?.text ||
                 msg.message.imageMessage?.caption ||
                 msg.message.videoMessage?.caption ||
                 '';
    const mediaType = msg.message.imageMessage ? 'IMAGE' : (msg.message.videoMessage ? 'VIDEO' : 'TEXT');

    // Only forward status stories younger than STATUS_MAX_AGE_HOURS (default 25 hours)
    const ts = msg.messageTimestamp ? Number(msg.messageTimestamp) * 1000 : Date.now();
    const ageHours = (Date.now() - ts) / (1000 * 60 * 60);
    if (ageHours > CONFIG.STATUS_MAX_AGE_HOURS) return;

    const senderName = msg.pushName || getContactName(resolvedPhone) || getContactName(cleanRawPhone) || '';

    emitToAndroid('INCOMING_STATUS', {
        id: statusId,
        senderPhone: resolvedPhone,
        realPhone: resolved.realPhone,
        lid: resolved.lid,
        senderName: senderName,
        isLid: resolved.isLid,
        mediaType,
        textContent: text,
        timestamp: ts,
        participant: msg.key.participant || senderJid
    });
}

/**
 * Marks a status as viewed and sends read receipts.
 */
export async function viewStatus(sock, statusId, senderPhone = '', participantInput = '', authFolder = CONFIG.DEFAULT_AUTH_FOLDER) {
    if (!sock || !statusId) return;

    try {
        const originalKey = statusKeysMap.get(statusId);
        let participant = originalKey?.participant || (participantInput && participantInput.includes('@') ? participantInput : null);

        if (!participant && (senderPhone || participantInput)) {
            const raw = senderPhone || participantInput;
            const clean = raw.replace(/[^0-9]/g, '');
            const mappedLid = getLidForPhone(clean);
            if (mappedLid) {
                participant = `${mappedLid}@lid`;
            } else {
                participant = isLidJidOrNumber(clean) ? `${clean}@lid` : `${clean}@s.whatsapp.net`;
            }
        }

        if (participant) {
            const targetParticipant = jidNormalizedUser(participant);
            const resolved = await resolvePhoneAndLid(targetParticipant || senderPhone, null, sock);
            
            const readKey = {
                remoteJid: 'status@broadcast',
                id: statusId,
                participant: targetParticipant,
                fromMe: false
            };

            // 1. Explicit read receipt to publisher's exact JID
            try {
                await sock.sendReceipt('status@broadcast', targetParticipant, [statusId], 'read');
            } catch (_) {}

            // 2. Read confirmation in Baileys local state
            try {
                await sock.readMessages([readKey]);
            } catch (_) {}

            const pushName = originalKey?.pushName || getContactName(resolved.phone) || '';

            emitToAndroid('STATUS_VIEWED', {
                id: statusId,
                senderPhone: resolved.phone,
                realPhone: resolved.realPhone,
                lid: resolved.lid,
                senderName: pushName,
                participant: targetParticipant,
                success: true
            });
        } else {
            emitToAndroid('ERROR', { error: `لم يتم العثور على ناشر الحالة ${statusId} لتأكيد المشاهدة` });
        }
    } catch (viewErr) {
        emitToAndroid('ERROR', { error: `فشل تسجيل مشاهدة الحالة: ${viewErr.message || viewErr}` });
    }
}

/**
 * Reacts to a status story with an emoji.
 *
 * Protocol-correct approach (discovered via Baileys source + WhatsApp protocol analysis):
 * 1. Target MUST be 'status@broadcast' (NOT the participant directly).
 * 2. statusJidList MUST include ONLY the publisher's participant JID AND our own sock.user.id (Strict Single-JID).
 * 3. The reaction key MUST use the original status message key exactly as received.
 * 4. Read receipts are sent first to register the view alongside the reaction.
 */
export async function reactStatus(sock, statusId, emoji = '💚', senderPhone = '', participantInput = '', authFolder = CONFIG.DEFAULT_AUTH_FOLDER) {
    if (!sock || !statusId) return;

    try {
        const originalKey = statusKeysMap.get(statusId);
        const participant = originalKey?.participant || (participantInput && participantInput.includes('@') ? participantInput : null);

        if (!participant) {
            emitToAndroid('ERROR', { error: `لم يتم العثور على مفتاح الحالة ${statusId} لإرسال التفاعل` });
            return;
        }

        // Exact original key without altering remoteJid, id, or participant
        const reactionKey = {
            remoteJid: originalKey?.remoteJid || 'status@broadcast',
            id: statusId,
            participant: participant,
            fromMe: false
        };

        const reactionMsg = {
            react: {
                text: emoji || '💚',
                key: reactionKey
            }
        };

        // Strict Single-JID list: target participant only
        const statusJidList = [participant];

        // 1. Send read receipt first
        let receiptSuccess = false;
        try {
            await sock.sendReceipt('status@broadcast', participant, [statusId], 'read');
            receiptSuccess = true;
        } catch (receiptErr) {
            console.error(`[reactStatus] sendReceipt failed: ${receiptErr?.message || receiptErr}`);
        }

        try {
            await sock.readMessages([reactionKey]);
        } catch (_) {}

        recordStage(statusId, 'REACTION_BUILD', 'status-manager.js', 'reactStatus', {
            statusId,
            participant,
            reactionKey,
            reactionMsg,
            statusJidList,
            receiptSuccess
        });

        // Wait 600ms protocol delay
        await new Promise(r => setTimeout(r, 600));

        recordStage(statusId, 'BAILEYS_SEND', 'status-manager.js', 'reactStatus', {
            target: 'status@broadcast',
            options: { statusJidList }
        });

        // 2. Send reaction with statusJidList = [originalKey.participant]
        let sendResult = null;
        let sendError = null;
        try {
            sendResult = await sock.sendMessage('status@broadcast', reactionMsg, { statusJidList });
        } catch (sendErr) {
            sendError = sendErr;
            console.error(`[reactStatus] sendMessage failed: ${sendErr?.message || sendErr}`);
            emitToAndroid('ERROR', { error: `فشل إرسال التفاعل: ${sendErr?.message || sendErr}` });
        }

        if (sendResult) {
            recordStage(statusId, 'WHATSAPP_ACK', 'status-manager.js', 'reactStatus', {
                sendResultKey: sendResult.key,
                status: sendResult.status,
                success: true
            }, 'PASS');
        } else {
            recordStage(statusId, 'WHATSAPP_ACK', 'status-manager.js', 'reactStatus', {
                error: sendError ? (sendError.message || String(sendError)) : 'sendMessage returned null or undefined'
            }, 'FAIL', sendError);
        }

        if (sendResult?.key?.id && sendResult?.message) {
            sentMessagesCache.set(sendResult.key.id, sendResult.message);
        }
        sentMessagesCache.set(statusId, reactionMsg);

        const resolved = await resolvePhoneAndLid(participant || senderPhone, null, sock);
        const pushName = originalKey?.pushName || getContactName(resolved.phone) || '';

        console.log(`[reactStatus] AUDIT: statusId=${statusId}, participant=${participant}, statusJidList=${JSON.stringify(statusJidList)}, receiptSuccess=${receiptSuccess}, sendSuccess=${!!sendResult}`);

        emitToAndroid('STATUS_REACTED', {
            id: statusId,
            senderPhone: resolved.phone,
            realPhone: resolved.realPhone,
            lid: resolved.lid,
            senderName: pushName,
            participant: participant,
            emoji: emoji || '💚',
            success: !!sendResult,
            receiptSuccess: receiptSuccess
        });

        // Print final trace timeline report
        printTimelineReport(statusId);
    } catch (err) {
        recordStage(statusId, 'WHATSAPP_ACK', 'status-manager.js', 'reactStatus', {
            error: err?.message || String(err)
        }, 'FAIL', err);
        printTimelineReport(statusId);
        emitToAndroid('ERROR', { error: `فشل التفاعل مع الحالة: ${err.message || err}` });
    }
}

export function getStatusKeysCount() {
    return statusKeysMap.size;
}

export function getStatusKey(statusId) {
    return statusKeysMap.get(statusId);
}

export function clearStatusKeys() {
    statusKeysMap.clear();
}
