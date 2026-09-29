/**
 * WhatsApp Automation Engine — Command Dispatcher
 * Parses and routes inbound commands from the Android Kotlin client
 * to the appropriate Baileys socket actions and subsystem managers.
 */

import { jidNormalizedUser } from '@whiskeysockets/baileys';
import { sentMessagesCache, CONFIG } from './config.js';
import { emitToAndroid } from './bridge.js';
import { isLidJidOrNumber } from './lid-resolver.js';
import { viewStatus, reactStatus, getStatusKeysCount } from './status-manager.js';
import { recordStage } from './tracer.js';

/**
 * Creates a command processor bound to the active session and socket accessors.
 *
 * @param {object} context
 * @param {Function} context.getSocket - Returns active Baileys socket or null
 * @param {Function} context.startSession - Starts a new WhatsApp session
 * @param {Function} context.getAuthFolder - Returns current auth folder path
 * @param {Function} context.setAuthFolder - Sets current auth folder path
 * @returns {Function} processCommandLine(line: string) => Promise<void>
 */
export function createCommandDispatcher({ getSocket, startSession, getAuthFolder, setAuthFolder }) {
    return async function processCommandLine(line) {
        if (!line || typeof line !== 'string') return;
        const trimmed = line.trim();
        if (!trimmed.startsWith('{')) return;

        try {
            const command = JSON.parse(trimmed);
            const action = command.action || command.type;
            const sock = getSocket ? getSocket() : null;
            const authFolder = getAuthFolder ? getAuthFolder() : CONFIG.DEFAULT_AUTH_FOLDER;

            switch (action) {
                case 'START': {
                    if (command.authPath && setAuthFolder) {
                        setAuthFolder(command.authPath);
                    }
                    if (startSession) {
                        await startSession(command.authPath || authFolder);
                    }
                    break;
                }

                case 'PAIR_CODE': {
                    try {
                        if (!command.phone) {
                            emitToAndroid('ERROR', { error: 'يرجى إدخال رقم الهاتف' });
                            break;
                        }
                        const cleanPhone = String(command.phone).replace(/[^0-9]/g, '');
                        if (cleanPhone.length < 7) {
                            emitToAndroid('ERROR', { error: 'رقم الهاتف غير صالح' });
                            break;
                        }

                        let activeSock = getSocket ? getSocket() : null;
                        if (!activeSock && startSession) {
                            await startSession(authFolder);
                            activeSock = getSocket ? getSocket() : null;
                        }

                        if (activeSock?.authState?.creds?.registered) {
                            emitToAndroid('CONNECTION_STATE', {
                                status: 'CONNECTED',
                                user: {
                                    id: activeSock.user?.id,
                                    name: activeSock.user?.name || activeSock.user?.notify || ''
                                }
                            });
                            break;
                        }

                        // Wait for WebSocket readyState === 1 (OPEN)
                        let readyWait = 0;
                        while (activeSock && activeSock.ws && activeSock.ws.readyState !== 1 && readyWait < 24) {
                            await new Promise(r => setTimeout(r, 250));
                            readyWait++;
                        }

                        if (activeSock && typeof activeSock.requestPairingCode === 'function') {
                            const code = await activeSock.requestPairingCode(cleanPhone);
                            emitToAndroid('PAIRING_CODE', { code, phone: cleanPhone });
                        } else {
                            emitToAndroid('ERROR', { error: 'محرك واتساب غير جاهز حالياً لطلب الكود، أعد المحاولة بعد لحظات' });
                        }
                    } catch (pairErr) {
                        emitToAndroid('ERROR', { error: `فشل طلب كود الاقتران: ${pairErr.message || pairErr}` });
                    }
                    break;
                }

                case 'SEND_MESSAGE': {
                    if (sock && command.recipient && command.text) {
                        const cleanRecipient = String(command.recipient).replace(/[^0-9]/g, '');
                        let rawJid = command.recipient;
                        if (!rawJid.includes('@')) {
                            rawJid = isLidJidOrNumber(cleanRecipient) ? `${cleanRecipient}@lid` : `${cleanRecipient}@s.whatsapp.net`;
                        }
                        const jid = jidNormalizedUser(rawJid);
                        try {
                            // Typing indicator for natural interaction
                            await sock.sendPresenceUpdate('composing', jid);
                            const humanDelay = 1200 + Math.floor(Math.random() * 1500);
                            await new Promise(r => setTimeout(r, humanDelay));
                            await sock.sendPresenceUpdate('paused', jid);

                            const msgPayload = { text: command.text };
                            const sentResult = await sock.sendMessage(jid, msgPayload);
                            if (sentResult?.key?.id && sentResult?.message) {
                                sentMessagesCache.set(sentResult.key.id, sentResult.message);
                                if (sentMessagesCache.size > CONFIG.MAX_SENT_MESSAGES) {
                                    const firstKey = sentMessagesCache.keys().next().value;
                                    sentMessagesCache.delete(firstKey);
                                }
                            }

                            emitToAndroid('MESSAGE_SENT', { id: command.id, success: true });
                        } catch (sendErr) {
                            try {
                                const msgPayload = { text: command.text };
                                const sentResult = await sock.sendMessage(jid, msgPayload);
                                if (sentResult?.key?.id && sentResult?.message) {
                                    sentMessagesCache.set(sentResult.key.id, sentResult.message);
                                }
                                emitToAndroid('MESSAGE_SENT', { id: command.id, success: true });
                            } catch (err2) {
                                emitToAndroid('ERROR', { error: `فشل الإرسال: ${err2.message || err2}` });
                            }
                        }
                    }
                    break;
                }

                case 'REACT_MESSAGE': {
                    if (sock && command.messageId && command.chatJid) {
                        try {
                            const cleanPhone = String(command.chatJid).replace(/[^0-9]/g, '');
                            const rawJid = command.chatJid.includes('@') ? command.chatJid : `${cleanPhone}@s.whatsapp.net`;
                            const jid = jidNormalizedUser(rawJid);
                            const key = {
                                remoteJid: jid,
                                id: command.messageId,
                                fromMe: false
                            };
                            if (command.participant) {
                                key.participant = jidNormalizedUser(command.participant);
                            }
                            const emoji = command.emoji || '💚';
                            const reactionPayload = {
                                react: {
                                    text: emoji,
                                    key: key
                                }
                            };
                            const sentResult = await sock.sendMessage(jid, reactionPayload);
                            if (sentResult?.key?.id && sentResult?.message) {
                                sentMessagesCache.set(sentResult.key.id, sentResult.message);
                            }
                            sentMessagesCache.set(command.messageId, reactionPayload);
                            emitToAndroid('MESSAGE_REACTED', { id: command.messageId, emoji, success: true });
                        } catch (reactErr) {
                            emitToAndroid('ERROR', { error: `فشل التفاعل مع الرسالة: ${reactErr.message || reactErr}` });
                        }
                    }
                    break;
                }

                case 'FETCH_STATUSES': {
                    if (sock) {
                        const count = getStatusKeysCount();
                        emitToAndroid('STATUS_FETCH_DONE', {
                            count,
                            message: `الحالات النشطة المتوفرة: ${count}`
                        });
                    }
                    break;
                }

                case 'VIEW_STATUS': {
                    if (sock) {
                        const statusId = command.statusId || command.id;
                        await viewStatus(sock, statusId, command.senderPhone, command.participant, authFolder);
                    }
                    break;
                }

                case 'REACT_STATUS': {
                    if (sock) {
                        const statusId = command.statusId || command.id;
                        recordStage(statusId, 'DISPATCHER', 'command-dispatcher.js', 'processCommandLine', {
                            action: 'REACT_STATUS',
                            statusId,
                            senderPhone: command.senderPhone,
                            participant: command.participant,
                            emoji: command.emoji
                        });
                        await reactStatus(sock, statusId, command.emoji || '💚', command.senderPhone, command.participant, authFolder);
                    }
                    break;
                }

                case 'LOGOUT': {
                    if (sock) {
                        try {
                            await sock.logout();
                        } catch (_) {}
                        emitToAndroid('LOGGED_OUT', { success: true });
                    }
                    break;
                }

                default:
                    break;
            }
        } catch (err) {
            emitToAndroid('ERROR', { error: err.message || String(err) });
        }
    };
}
