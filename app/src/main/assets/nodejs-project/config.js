/**
 * WhatsApp Automation Engine — Configuration & Constants
 * Contains ports, paths, socket configurations, crypto polyfills, and cache stores.
 */

import nodeCrypto from 'crypto';
import path from 'path';
import pino from 'pino';

// Ensure WebCrypto is globally available for Baileys Signal protocols
if (!globalThis.crypto || !globalThis.crypto.subtle) {
    globalThis.crypto = nodeCrypto.webcrypto || nodeCrypto;
}

export const logger = pino({ level: 'silent' });

export const CONFIG = {
    PORT: process.env.BRIDGE_PORT ? parseInt(process.env.BRIDGE_PORT, 10) : 6789,
    ALT_PORT: 9099,
    HOST: '127.0.0.1',
    DEFAULT_AUTH_FOLDER: './auth_info_baileys',
    BROWSER_CONFIG: ['Ubuntu', 'Chrome', '20.0.04'],
    MAX_STATUS_KEYS: 1000,
    MAX_RAW_MESSAGES: 1500,
    MAX_SENT_MESSAGES: 1000,
    MAX_RETRY_COUNTERS: 2000,
    STATUS_MAX_AGE_HOURS: 25,
};

export function getStatusKeysFilePath(authFolder) {
    return path.join(authFolder, 'status_keys.json');
}

export function getLidPhoneMapFilePath(authFolder) {
    return path.join(authFolder, 'lid_phone_map.json');
}

// Global caches for PreKey retry resolving and message deduplication
export const sentMessagesCache = new Map();
export const rawMessagesCache = new Map();

// msgRetryCounterCache for Baileys socket to prevent "Waiting for this message"
const msgRetryCounterMap = new Map();
export const msgRetryCounterCache = {
    get: (key) => msgRetryCounterMap.get(key),
    set: (key, val) => {
        msgRetryCounterMap.set(key, val);
        if (msgRetryCounterMap.size > CONFIG.MAX_RETRY_COUNTERS) {
            const first = msgRetryCounterMap.keys().next().value;
            msgRetryCounterMap.delete(first);
        }
    },
    del: (key) => msgRetryCounterMap.delete(key),
    delete: (key) => msgRetryCounterMap.delete(key)
};
