/**
 * Test Suite for Node.js Baileys Modular Engine
 * Tests ES modules: config, bridge, lid-resolver, status-manager, command-dispatcher.
 * Specifically verifies Strict Single-JID routing and absence of DM fallback.
 */

import assert from 'assert';
import { CONFIG, msgRetryCounterCache, sentMessagesCache, rawMessagesCache } from './config.js';
import {
    linkLidAndPhone,
    resolvePhoneAndLid,
    isLidJidOrNumber,
    lidPhoneMap,
    phoneLidMap,
    contactNamesMap,
    clearLidMaps
} from './lid-resolver.js';
import {
    statusKeysMap,
    processStatusMessage,
    viewStatus,
    reactStatus,
    clearStatusKeys
} from './status-manager.js';
import { createCommandDispatcher } from './command-dispatcher.js';

let totalTests = 0;
let passedTests = 0;

function it(name, fn) {
    totalTests++;
    try {
        fn();
        console.log(`  ✓ ${name}`);
        passedTests++;
    } catch (err) {
        console.error(`  ✗ ${name}`);
        console.error(err);
    }
}

async function itAsync(name, fn) {
    totalTests++;
    try {
        await fn();
        console.log(`  ✓ ${name}`);
        passedTests++;
    } catch (err) {
        console.error(`  ✗ ${name}`);
        console.error(err);
    }
}

async function runTests() {
    console.log('--- Running Milestone 1 Test Suite ---');

    // 1. Config tests
    console.log('\n[1] Config & Caches:');
    it('CONFIG constants exist and have expected defaults', () => {
        assert.strictEqual(CONFIG.PORT, 6789);
        assert.strictEqual(CONFIG.ALT_PORT, 9099);
        assert.strictEqual(CONFIG.HOST, '127.0.0.1');
        assert.strictEqual(CONFIG.STATUS_MAX_AGE_HOURS, 25);
    });

    it('msgRetryCounterCache manages retry entries correctly', () => {
        msgRetryCounterCache.set('test_msg_1', 1);
        assert.strictEqual(msgRetryCounterCache.get('test_msg_1'), 1);
        msgRetryCounterCache.set('test_msg_1', 2);
        assert.strictEqual(msgRetryCounterCache.get('test_msg_1'), 2);
        msgRetryCounterCache.del('test_msg_1');
        assert.strictEqual(msgRetryCounterCache.get('test_msg_1'), undefined);
    });

    // 2. LID Resolver tests
    console.log('\n[2] LID & Phone Resolver:');
    it('isLidJidOrNumber correctly identifies LID formats', () => {
        assert.strictEqual(isLidJidOrNumber('12345678901234@lid'), true);
        assert.strictEqual(isLidJidOrNumber('lid_user_42'), true);
        assert.strictEqual(isLidJidOrNumber('967771234567@s.whatsapp.net'), false);
        assert.strictEqual(isLidJidOrNumber(''), false);
    });

    it('linkLidAndPhone links LID and phone bidirectionally', () => {
        clearLidMaps();
        const testLid = '12345678901234';
        const testPhone = '967771234567';
        linkLidAndPhone(testLid, testPhone, 'Test User');

        assert.strictEqual(lidPhoneMap.get(testLid), testPhone);
        assert.strictEqual(phoneLidMap.get(testPhone), testLid);
        assert.strictEqual(contactNamesMap.get(testPhone), 'Test User');
        assert.strictEqual(contactNamesMap.get(testLid), 'Test User');
    });

    await itAsync('resolvePhoneAndLid resolves LID from cache and message participantPn', async () => {
        clearLidMaps();
        // Link first
        linkLidAndPhone('11112222333344', '967711223344', 'Linked Contact');

        // Resolve mapped LID
        const res1 = await resolvePhoneAndLid('11112222333344@lid');
        assert.strictEqual(res1.isLid, true);
        assert.strictEqual(res1.phone, '967711223344');
        assert.strictEqual(res1.realPhone, '967711223344');
        assert.strictEqual(res1.lid, '11112222333344');

        // Resolve non-LID business phone
        const res2 = await resolvePhoneAndLid('967770001111@s.whatsapp.net');
        assert.strictEqual(res2.isLid, false);
        assert.strictEqual(res2.phone, '967770001111');
        assert.strictEqual(res2.realPhone, '967770001111');
    });

    // 3. Status Manager tests
    console.log('\n[3] Status Manager & Reactions:');
    await itAsync('processStatusMessage caches keys and handles age filtering', async () => {
        clearStatusKeys();
        const mockMsg = {
            key: {
                remoteJid: 'status@broadcast',
                id: 'STATUS_MSG_001',
                participant: '967771234567@s.whatsapp.net',
                fromMe: false
            },
            message: {
                conversation: 'Hello status world'
            },
            messageTimestamp: Math.floor(Date.now() / 1000),
            pushName: 'Alice'
        };

        await processStatusMessage(mockMsg);
        assert.strictEqual(statusKeysMap.has('STATUS_MSG_001'), true);
        const stored = statusKeysMap.get('STATUS_MSG_001');
        assert.strictEqual(stored.participant, '967771234567@s.whatsapp.net');
        assert.strictEqual(stored.pushName, 'Alice');
    });

    await itAsync('viewStatus sends read receipts to status@broadcast with correct participant', async () => {
        clearStatusKeys();
        statusKeysMap.set('STATUS_MSG_002', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_MSG_002',
            participant: '967779876543@s.whatsapp.net',
            fromMe: false,
            pushName: 'Bob'
        });

        let receiptSent = null;
        let messagesRead = null;

        const mockSock = {
            sendReceipt: async (remoteJid, participant, ids, type) => {
                receiptSent = { remoteJid, participant, ids, type };
            },
            readMessages: async (keys) => {
                messagesRead = keys;
            }
        };

        await viewStatus(mockSock, 'STATUS_MSG_002');
        assert.deepStrictEqual(receiptSent, {
            remoteJid: 'status@broadcast',
            participant: '967779876543@s.whatsapp.net',
            ids: ['STATUS_MSG_002'],
            type: 'read'
        });
        assert.strictEqual(messagesRead[0].id, 'STATUS_MSG_002');
        assert.strictEqual(messagesRead[0].participant, '967779876543@s.whatsapp.net');
    });

    await itAsync('reactStatus uses Strict Single-JID routing for Personal (LID) account', async () => {
        clearStatusKeys();
        const lidParticipant = '99887766554433@lid';
        statusKeysMap.set('STATUS_LID_001', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_LID_001',
            participant: lidParticipant,
            fromMe: false,
            pushName: 'Personal User'
        });

        let sendCalls = [];
        const mockSock = {
            sendReceipt: async () => {},
            readMessages: async () => {},
            sendMessage: async (jid, content, options) => {
                sendCalls.push({ jid, content, options });
                return { key: { id: 'REACT_SEND_KEY_1' }, message: content };
            }
        };

        await reactStatus(mockSock, 'STATUS_LID_001', '❤️');

        // MUST be sent to 'status@broadcast'
        assert.strictEqual(sendCalls.length, 1);
        assert.strictEqual(sendCalls[0].jid, 'status@broadcast');
        // MUST have statusJidList with EXACT targetParticipant
        assert.deepStrictEqual(sendCalls[0].options, { statusJidList: [lidParticipant] });
        // Reaction key must target status@broadcast with lidParticipant
        assert.strictEqual(sendCalls[0].content.react.text, '❤️');
        assert.strictEqual(sendCalls[0].content.react.key.participant, lidParticipant);
        assert.strictEqual(sendCalls[0].content.react.key.remoteJid, 'status@broadcast');
    });

    await itAsync('reactStatus uses Strict Single-JID routing for Business account', async () => {
        clearStatusKeys();
        const bizParticipant = '967773334444@s.whatsapp.net';
        statusKeysMap.set('STATUS_BIZ_001', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_BIZ_001',
            participant: bizParticipant,
            fromMe: false,
            pushName: 'Business User'
        });

        let sendCalls = [];
        const mockSock = {
            sendReceipt: async () => {},
            readMessages: async () => {},
            sendMessage: async (jid, content, options) => {
                sendCalls.push({ jid, content, options });
                return { key: { id: 'REACT_SEND_KEY_2' }, message: content };
            }
        };

        await reactStatus(mockSock, 'STATUS_BIZ_001', '💚');

        assert.strictEqual(sendCalls.length, 1);
        assert.strictEqual(sendCalls[0].jid, 'status@broadcast');
        assert.deepStrictEqual(sendCalls[0].options, { statusJidList: [bizParticipant] });
        assert.strictEqual(sendCalls[0].content.react.text, '💚');
        assert.strictEqual(sendCalls[0].content.react.key.participant, bizParticipant);
    });

    await itAsync('reactStatus NEVER falls back to private DM when status@broadcast fails', async () => {
        clearStatusKeys();
        const target = '967779998888@s.whatsapp.net';
        statusKeysMap.set('STATUS_FAIL_001', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_FAIL_001',
            participant: target,
            fromMe: false,
            pushName: 'Target User'
        });

        let sendCalls = [];
        const mockSock = {
            sendReceipt: async () => {},
            readMessages: async () => {},
            sendMessage: async (jid, content, options) => {
                sendCalls.push({ jid, content, options });
                if (jid === 'status@broadcast') {
                    throw new Error('Simulated network failure on status@broadcast');
                }
                return { key: { id: 'FALLBACK_KEY' }, message: content };
            }
        };

        // Call reactStatus - it must catch the error and NOT send to target's private chat!
        await reactStatus(mockSock, 'STATUS_FAIL_001', '💚');

        // Verify that sendCalls ONLY contains status@broadcast and NO private chat fallback
        assert.strictEqual(sendCalls.length, 1);
        assert.strictEqual(sendCalls[0].jid, 'status@broadcast');
        const fallbackCall = sendCalls.find(c => c.jid === target);
        assert.strictEqual(fallbackCall, undefined, 'CRITICAL: No private chat fallback should ever be invoked!');
    });

    // 4. Command Dispatcher tests
    console.log('\n[4] Command Dispatcher:');
    await itAsync('createCommandDispatcher routes commands accurately', async () => {
        let sentMessage = null;
        const mockSock = {
            sendMessage: async (jid, payload) => {
                sentMessage = { jid, payload };
                return { key: { id: 'MSG_SENT_1' }, message: payload };
            },
            sendPresenceUpdate: async () => {}
        };

        const dispatcher = createCommandDispatcher({
            getSocket: () => mockSock,
            startSession: async () => {},
            getAuthFolder: () => './test_auth',
            setAuthFolder: () => {}
        });

        // Test SEND_MESSAGE
        await dispatcher(JSON.stringify({
            action: 'SEND_MESSAGE',
            recipient: '96777112233',
            text: 'Hello from test',
            id: 'CMD_TEST_1'
        }));

        assert.ok(sentMessage !== null);
        assert.strictEqual(sentMessage.jid, '96777112233@s.whatsapp.net');
        assert.strictEqual(sentMessage.payload.text, 'Hello from test');
    });

    console.log(`\nResults: ${passedTests}/${totalTests} tests passed.\n`);
    if (passedTests !== totalTests) {
        process.exit(1);
    }
}

runTests().catch(err => {
    console.error('Test runner failed:', err);
    process.exit(1);
});
