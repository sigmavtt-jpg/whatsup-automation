/**
 * WhatsApp Automation Engine — LID & Phone Resolver
 * Manages bi-directional mapping between WhatsApp Encrypted Account Identifiers (LID)
 * and direct phone numbers, plus contact display names.
 */

import fs from 'fs';
import { getLidPhoneMapFilePath } from './config.js';

export const lidPhoneMap = new Map(); // LID -> Phone
export const phoneLidMap = new Map(); // Phone -> LID
export const contactNamesMap = new Map(); // Phone/LID -> Display Name

/**
 * Loads LID-to-phone and names mappings from disk.
 */
export function loadLidPhoneMap(authFolder) {
    if (!authFolder) return;
    try {
        const filePath = getLidPhoneMapFilePath(authFolder);
        if (fs.existsSync(filePath)) {
            const raw = fs.readFileSync(filePath, 'utf8');
            const data = JSON.parse(raw);
            if (data.lidToPhone) {
                for (const [k, v] of Object.entries(data.lidToPhone)) {
                    lidPhoneMap.set(k, v);
                    phoneLidMap.set(v, k);
                }
            }
            if (data.names) {
                for (const [k, v] of Object.entries(data.names)) {
                    contactNamesMap.set(k, v);
                }
            }
        }
    } catch (_) {}
}

/**
 * Saves LID-to-phone and names mappings to disk.
 */
export function saveLidPhoneMap(authFolder) {
    if (!authFolder) return;
    try {
        const filePath = getLidPhoneMapFilePath(authFolder);
        const lidToPhone = {};
        for (const [k, v] of lidPhoneMap.entries()) {
            lidToPhone[k] = v;
        }
        const names = {};
        for (const [k, v] of contactNamesMap.entries()) {
            names[k] = v;
        }
        fs.writeFileSync(filePath, JSON.stringify({ lidToPhone, names }), 'utf8');
    } catch (_) {}
}

/**
 * Checks whether a given string is a LID identifier or number.
 */
export function isLidJidOrNumber(jidOrPhone) {
    if (!jidOrPhone) return false;
    const str = String(jidOrPhone);
    if (str.includes('@lid') || str.includes('lid')) return true;
    const clean = str.replace(/[^0-9]/g, '');
    return clean === '97517970702387';
}

/**
 * Links a LID and a phone number, optionally associating a display name.
 */
export function linkLidAndPhone(lid, phone, name = null, authFolder = null) {
    if (!lid || !phone) return;
    const cleanLid = String(lid).split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
    const cleanPhone = String(phone).split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
    if (!cleanLid || !cleanPhone || cleanLid === cleanPhone) return;

    if (cleanLid.length >= 13 && cleanPhone.length >= 7 && cleanPhone.length <= 15) {
        let changed = false;
        if (lidPhoneMap.get(cleanLid) !== cleanPhone) {
            lidPhoneMap.set(cleanLid, cleanPhone);
            phoneLidMap.set(cleanPhone, cleanLid);
            changed = true;
        }
        if (name && typeof name === 'string' && name.trim().length >= 2) {
            const trimmedName = name.trim();
            contactNamesMap.set(cleanPhone, trimmedName);
            contactNamesMap.set(cleanLid, trimmedName);
            changed = true;
        }
        if (changed && authFolder) {
            saveLidPhoneMap(authFolder);
        }
    }
}

/**
 * Resolves phone number and LID for a given JID or phone string.
 * @param {string} jidOrPhone
 * @param {object|null} msg - Baileys message object (optional)
 * @param {object|null} sock - Baileys socket instance (optional)
 * @returns {Promise<{phone: string, realPhone: string|null, lid: string|null, isLid: boolean}>}
 */
export async function resolvePhoneAndLid(jidOrPhone, msg = null, sock = null) {
    if (!jidOrPhone) return { phone: '', realPhone: null, lid: null, isLid: false };
    const rawStr = String(jidOrPhone);
    const cleanDigits = rawStr.split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
    const isLid = rawStr.includes('@lid') || isLidJidOrNumber(rawStr) || isLidJidOrNumber(cleanDigits);

    if (isLid) {
        // 1. Check if message object directly contains participantPn or remoteJidPn
        const foundPn = msg?.key?.participantPn || msg?.key?.remoteJidPn || msg?.participantPn || msg?.pnJid;
        if (foundPn) {
            const cleanPn = String(foundPn).split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
            if (cleanPn && cleanPn.length >= 7 && cleanPn.length <= 15) {
                linkLidAndPhone(cleanDigits, cleanPn);
                return { phone: cleanPn, realPhone: cleanPn, lid: cleanDigits, isLid: true };
            }
        }

        // 2. Check local silent resolver map
        if (lidPhoneMap.has(cleanDigits)) {
            const mappedPhone = lidPhoneMap.get(cleanDigits);
            return { phone: mappedPhone, realPhone: mappedPhone, lid: cleanDigits, isLid: true };
        }

        // 3. Query Signal Session Store via Baileys socket if available
        try {
            if (sock?.signalRepository?.lidMapping?.getPNForLID) {
                const pn = await sock.signalRepository.lidMapping.getPNForLID(`${cleanDigits}@lid`);
                if (pn) {
                    const cleanPn = String(pn).split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
                    if (cleanPn && cleanPn.length >= 7) {
                        linkLidAndPhone(cleanDigits, cleanPn);
                        return { phone: cleanPn, realPhone: cleanPn, lid: cleanDigits, isLid: true };
                    }
                }
            }
        } catch (_) {}

        return { phone: cleanDigits, realPhone: null, lid: cleanDigits, isLid: true };
    } else {
        const mappedLid = phoneLidMap.get(cleanDigits) || null;
        return { phone: cleanDigits, realPhone: cleanDigits, lid: mappedLid, isLid: false };
    }
}

export function getContactName(phoneOrLid) {
    if (!phoneOrLid) return '';
    const clean = String(phoneOrLid).split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
    return contactNamesMap.get(clean) || contactNamesMap.get(phoneOrLid) || '';
}

export function setContactName(phoneOrLid, name) {
    if (!phoneOrLid || !name) return;
    const clean = String(phoneOrLid).split('@')[0].split(':')[0].replace(/[^0-9]/g, '');
    const trimmed = name.trim();
    if (clean) contactNamesMap.set(clean, trimmed);
    contactNamesMap.set(phoneOrLid, trimmed);
}

export function getLidForPhone(phone) {
    if (!phone) return null;
    const clean = String(phone).replace(/[^0-9]/g, '');
    return phoneLidMap.get(clean) || null;
}

export function getPhoneForLid(lid) {
    if (!lid) return null;
    const clean = String(lid).replace(/[^0-9]/g, '');
    return lidPhoneMap.get(clean) || null;
}

export function clearLidMaps() {
    lidPhoneMap.clear();
    phoneLidMap.clear();
    contactNamesMap.clear();
}
