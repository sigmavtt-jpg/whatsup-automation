/**
 * WhatsApp Automation Engine — Local Baileys Bridge
 * Runs locally on the Android device with zero external servers.
 * Modular entry point orchestrating lifecycle and event bindings.
 */

import makeWASocket, {
    DisconnectReason,
    useMultiFileAuthState,
    fetchLatestBaileysVersion
} from '@whiskeysockets/baileys';
import {
    CONFIG,
    logger,
    sentMessagesCache,
    rawMessagesCache,
    msgRetryCounterCache
} from './config.js';
import { initBridge, emitToAndroid } from './bridge.js';
import {
    loadLidPhoneMap,
    saveLidPhoneMap,
    linkLidAndPhone,
    resolvePhoneAndLid,
    isLidJidOrNumber,
    setContactName,
    getContactName
} from './lid-resolver.js';
import {
    loadStatusKeys,
    processStatusMessage,
    getStatusKeysCount
} from './status-manager.js';
import { attachSocketTraceHook } from './tracer.js';
import { createCommandDispatcher } from './command-dispatcher.js';

import fs from 'fs';
import path from 'path';

let sock = null;
let isStarting = false;
let currentAuthFolder = process.argv[2] || CONFIG.DEFAULT_AUTH_FOLDER;

/**
 * Self-healing: Safely logs Signal session errors without destroying all unrelated sessions.
 */
function healBadMacSession(err) {
    try {
        const errStr = String(err?.stack || err?.message || err || '');
        if (errStr.includes('Bad MAC')) {
            console.warn('[Self-Healing] Transient Bad MAC detected in Signal ratchet; relying on Baileys PreKey retry resolver.');
        }
    } catch (_) {}
}

process.on('uncaughtException', (err) => {
    console.error('Node uncaughtException:', err?.stack || err);
    healBadMacSession(err);
});
process.on('unhandledRejection', (reason) => {
    console.error('Node unhandledRejection:', reason);
    healBadMacSession(reason);
});
if (process.stdin) {
    process.stdin.on('error', () => {
        // Ignore stdin stream errors on Android
    });
}

// Keep Node.js event loop alive 24/7
setInterval(() => {}, 60000);

/**
 * Processes batches of contacts received during history sync or contact updates.
 */
async function handleContactsBatch(contactsList, authFolder = currentAuthFolder) {
    if (!contactsList || !Array.isArray(contactsList)) return;
    const validContacts = [];

    for (const c of contactsList) {
        if (!c || !c.id) continue;

        const savedName = c.name || c.verifiedName || c.notify || '';
        const rawId = String(c.id);
        const lid = c.lid ? String(c.lid) : (rawId.includes('@lid') ? rawId : null);
        const phone = c.phoneNumber || c.pn || (rawId.includes('@s.whatsapp.net') ? rawId : null);

        if (lid && phone) {
            linkLidAndPhone(lid, phone, savedName, authFolder);
        }

        const resolved = await resolvePhoneAndLid(c.id, null, sock);
        const cleanPhone = (resolved.realPhone || resolved.phone || '').replace(/[^0-9]/g, '');

        if (cleanPhone && cleanPhone.length >= 7 && !isLidJidOrNumber(cleanPhone) && savedName && savedName.trim().length >= 2) {
            const trimmed = savedName.trim();
            const isNumeric = trimmed.replace(/[^0-9]/g, '').length === trimmed.length;
            if (!isNumeric && !trimmed.includes('@') && !trimmed.includes('lid')) {
                validContacts.push({
                    phone: cleanPhone,
                    name: trimmed
                });
                setContactName(cleanPhone, trimmed);
            }
        }
    }

    if (validContacts.length > 0) {
        emitToAndroid('WHATSAPP_CONTACTS_SYNC', { contacts: validContacts });
        saveLidPhoneMap(authFolder);
    }
}

/**
 * Starts or restarts the Baileys WhatsApp session.
 */
export async function startWhatsAppSession(authFolder = currentAuthFolder) {
    if (isStarting) return;
    isStarting = true;

    if (sock) {
        try { sock.end(); } catch (_) {}
        sock = null;
    }

    loadStatusKeys(authFolder);
    loadLidPhoneMap(authFolder);

    const { state, saveCreds } = await useMultiFileAuthState(authFolder);
    const { version } = await fetchLatestBaileysVersion();

    sock = makeWASocket({
        version,
        auth: state,
        logger,
        printQRInTerminal: false,
        browser: CONFIG.BROWSER_CONFIG,
        syncFullHistory: true,
        markOnlineOnConnect: true,
        generateHighQualityLinkPreview: false,
        msgRetryCounterCache,
        // PreKey Retry Resolver: answers decryption retries from cache to prevent "Waiting for this message"
        getMessage: async (key) => {
            if (!key?.id) return undefined;
            return sentMessagesCache.get(key.id) || rawMessagesCache.get(key.id) || undefined;
        }
    });

    attachSocketTraceHook(sock);

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', async (update) => {
        const { connection, lastDisconnect, qr } = update;

        if (qr) {
            emitToAndroid('QR_CODE', { qr });
        }

        if (connection === 'close') {
            const statusCode = (lastDisconnect?.error)?.output?.statusCode;
            const isLoggedOut = statusCode === DisconnectReason.loggedOut;
            const shouldReconnect = !isLoggedOut;

            isStarting = false;

            if (isLoggedOut) {
                emitToAndroid('LOGGED_OUT', { reason: 'session_expired_or_logged_out' });
            }

            emitToAndroid('CONNECTION_STATE', {
                status: 'DISCONNECTED',
                reconnecting: shouldReconnect,
                reason: lastDisconnect?.error?.message || (isLoggedOut ? 'logged_out' : 'closed')
            });

            if (shouldReconnect) {
                setTimeout(() => startWhatsAppSession(authFolder), 3000);
            }
        } else if (connection === 'open') {
            isStarting = false;
            const user = sock.user;

            try {
                await sock.sendPresenceUpdate('available');
            } catch (_) {}

            emitToAndroid('CONNECTION_STATE', {
                status: 'CONNECTED',
                user: {
                    id: user?.id,
                    name: user?.name || user?.notify || ''
                }
            });

            const count = getStatusKeysCount();
            emitToAndroid('STATUS_FETCH_DONE', {
                count,
                message: `جاهز لاستقبال ومعالجة الحالات (المفاتيح المخزنة: ${count})`
            });
        }
    });

    // History sync listener
    sock.ev.on('messaging-history.set', async ({ chats, contacts, messages }) => {
        if (chats && Array.isArray(chats)) {
            for (const chat of chats) {
                if (chat.id && chat.lidJid) {
                    linkLidAndPhone(chat.lidJid, chat.id, chat.name, authFolder);
                }
                if (chat.pnJid && chat.lidJid) {
                    linkLidAndPhone(chat.lidJid, chat.pnJid, chat.name, authFolder);
                }
            }
        }
        if (contacts && contacts.length > 0) {
            await handleContactsBatch(contacts, authFolder);
        }
        if (messages && messages.length > 0) {
            for (const msg of messages) {
                if (msg.key?.remoteJid === 'status@broadcast') {
                    await processStatusMessage(msg, authFolder, sock);
                }
            }
        }
    });

    // Contacts upsert listener
    sock.ev.on('contacts.upsert', async (contacts) => {
        await handleContactsBatch(contacts, authFolder);
    });

    // Chats upsert listener for immediate LID/phone linking
    sock.ev.on('chats.upsert', (chats) => {
        if (chats && Array.isArray(chats)) {
            for (const chat of chats) {
                if (chat.id && chat.lidJid) {
                    linkLidAndPhone(chat.lidJid, chat.id, chat.name, authFolder);
                }
                if (chat.pnJid && chat.lidJid) {
                    linkLidAndPhone(chat.lidJid, chat.pnJid, chat.name, authFolder);
                }
                if (chat.id && chat.id.includes('@lid') && chat.pnJid) {
                    linkLidAndPhone(chat.id, chat.pnJid, chat.name, authFolder);
                }
            }
        }
    });

    // Chats update listener
    sock.ev.on('chats.update', (updates) => {
        if (updates && Array.isArray(updates)) {
            for (const chat of updates) {
                if (chat.id && chat.lidJid) {
                    linkLidAndPhone(chat.lidJid, chat.id, chat.name, authFolder);
                }
                if (chat.pnJid && chat.lidJid) {
                    linkLidAndPhone(chat.lidJid, chat.pnJid, chat.name, authFolder);
                }
            }
        }
    });

    // Contacts update listener
    sock.ev.on('contacts.update', async (updates) => {
        if (!updates || !Array.isArray(updates)) return;
        for (const u of updates) {
            if (!u || !u.id) continue;
            const savedName = u.name || u.verifiedName || u.notify || '';
            const lid = u.lid ? String(u.lid) : (String(u.id).includes('@lid') ? String(u.id) : null);
            const phone = u.phoneNumber || u.pn || (String(u.id).includes('@s.whatsapp.net') ? String(u.id) : null);

            if (lid && phone) {
                linkLidAndPhone(lid, phone, savedName, authFolder);
            }

            const resolved = await resolvePhoneAndLid(u.id, null, sock);
            const cleanPhone = (resolved.realPhone || resolved.phone || '').replace(/[^0-9]/g, '');

            if (cleanPhone && cleanPhone.length >= 7 && !isLidJidOrNumber(cleanPhone) && savedName && savedName.trim().length >= 2) {
                const trimmed = savedName.trim();
                const isNumeric = trimmed.replace(/[^0-9]/g, '').length === trimmed.length;
                if (!isNumeric && !trimmed.includes('@')) {
                    setContactName(cleanPhone, trimmed);
                    emitToAndroid('WHATSAPP_CONTACT_UPDATED', {
                        phone: cleanPhone,
                        name: trimmed
                    });
                }
            }
        }
    });

    // Messages listener: processes incoming status stories and regular chat messages
    sock.ev.on('messages.upsert', async ({ messages }) => {
        for (const msg of messages) {
            // Cache raw message for PreKey retry decryption requests
            if (msg.key?.id && msg.message) {
                rawMessagesCache.set(msg.key.id, msg.message);
                if (rawMessagesCache.size > CONFIG.MAX_RAW_MESSAGES) {
                    const firstKey = rawMessagesCache.keys().next().value;
                    rawMessagesCache.delete(firstKey);
                }
            }

            // Status stories
            if (msg.key?.remoteJid === 'status@broadcast') {
                if (msg.key.fromMe || !msg.message) continue;
                await processStatusMessage(msg, authFolder, sock);
                continue;
            }

            if (!msg.message || msg.key.fromMe) continue;

            const remoteJid = msg.key.remoteJid || '';
            if (remoteJid.endsWith('@newsletter') || remoteJid.includes('@broadcast')) continue;

            const isGroup = remoteJid.endsWith('@g.us');
            const actualSender = isGroup ? (msg.key.participant || '') : remoteJid;

            if (actualSender.startsWith('120363')) continue;

            const resolved = await resolvePhoneAndLid(actualSender, msg, sock);
            const cleanPhone = resolved.realPhone || resolved.phone;
            if (!cleanPhone || cleanPhone.startsWith('120363')) continue;

            if (msg.pushName && cleanPhone) {
                const trimmedPush = msg.pushName.trim();
                setContactName(cleanPhone, trimmedPush);
                if (resolved.lid) {
                    setContactName(resolved.lid, trimmedPush);
                }
            }

            const text = msg.message.conversation ||
                         msg.message.extendedTextMessage?.text ||
                         msg.message.imageMessage?.caption ||
                         msg.message.videoMessage?.caption ||
                         msg.message.documentMessage?.caption ||
                         '';

            if (!text || !cleanPhone) continue;

            const senderName = msg.pushName || getContactName(cleanPhone) || '';

            emitToAndroid('INCOMING_MESSAGE', {
                id: msg.key.id,
                senderPhone: cleanPhone,
                realPhone: resolved.realPhone,
                lid: resolved.lid,
                isLid: resolved.isLid,
                senderName,
                text,
                isGroup,
                timestamp: msg.messageTimestamp ? Number(msg.messageTimestamp) * 1000 : Date.now()
            });
        }
    });
}

// Initialize command dispatcher and bridge
const commandDispatcher = createCommandDispatcher({
    getSocket: () => sock,
    startSession: (folder) => startWhatsAppSession(folder),
    getAuthFolder: () => currentAuthFolder,
    setAuthFolder: (folder) => { currentAuthFolder = folder; }
});

initBridge(commandDispatcher);

// Automatic start
startWhatsAppSession(currentAuthFolder).catch((err) => {
    emitToAndroid('FATAL_ERROR', { error: err.message || String(err) });
});
