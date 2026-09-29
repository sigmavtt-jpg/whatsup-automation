/**
 * Adversarial Stress Test Suite for Status Reactions and Single-JID Routing
 * Challenger 1 — Empirical Verification & Adversarial Stress Testing
 */

import assert from 'assert';
import { jidNormalizedUser } from '@whiskeysockets/baileys';
import {
    statusKeysMap,
    processStatusMessage,
    viewStatus,
    reactStatus,
    clearStatusKeys
} from './status-manager.js';
import {
    linkLidAndPhone,
    clearLidMaps,
    lidPhoneMap,
    phoneLidMap
} from './lid-resolver.js';
import { createCommandDispatcher } from './command-dispatcher.js';

let totalTests = 0;
let passedTests = 0;
let failedTests = 0;
const testResults = [];

// Helper to intercept emitted Android events
function captureAndroidEvents(fn) {
    const originalLog = console.log;
    const events = [];
    console.log = (...args) => {
        const str = args.join(' ');
        if (str.startsWith('[WHATSAPP_EVENT]')) {
            try {
                const jsonStr = str.substring('[WHATSAPP_EVENT]'.length);
                events.push(JSON.parse(jsonStr));
            } catch (_) {
                events.push({ raw: str });
            }
        }
    };

    return {
        run: async () => {
            try {
                await fn(events);
            } finally {
                console.log = originalLog;
            }
        },
        events
    };
}

async function test(name, fn) {
    totalTests++;
    try {
        await fn();
        passedTests++;
        testResults.push({ name, status: 'PASSED' });
        console.log(`  [PASS] ${name}`);
    } catch (err) {
        failedTests++;
        testResults.push({ name, status: 'FAILED', error: err.message });
        console.error(`  [FAIL] ${name}`);
        console.error(err);
    }
}

// Mock socket generator
function createMockSocket(options = {}) {
    const calls = {
        sendMessage: [],
        sendReceipt: [],
        readMessages: [],
        sendPresenceUpdate: []
    };

    const sock = {
        calls,
        sendMessage: async (jid, content, sendOptions) => {
            calls.sendMessage.push({ jid, content, options: sendOptions, timestamp: Date.now() });
            if (options.failSendMessage) {
                if (typeof options.failSendMessage === 'function') {
                    options.failSendMessage(jid, content, sendOptions);
                } else {
                    throw options.failSendMessage;
                }
            }
            return { key: { id: 'MOCK_SENT_KEY_' + Date.now() }, message: content };
        },
        sendReceipt: async (remoteJid, participant, ids, type) => {
            calls.sendReceipt.push({ remoteJid, participant, ids, type });
            if (options.failSendReceipt) {
                throw options.failSendReceipt;
            }
        },
        readMessages: async (keys) => {
            calls.readMessages.push(keys);
            if (options.failReadMessages) {
                throw options.failReadMessages;
            }
        },
        sendPresenceUpdate: async (type, jid) => {
            calls.sendPresenceUpdate.push({ type, jid });
        }
    };

    return sock;
}

async function runAdversarialSuite() {
    console.log('================================================================');
    console.log('CHALLENGER 1: ADVERSARIAL STRESS TEST SUITE — STATUS REACTIONS');
    console.log('================================================================\n');

    // =========================================================================
    // CATEGORY A: Exception & Socket Failure Testing (Zero DM Side-Effects Proof)
    // =========================================================================
    console.log('--- CATEGORY A: Exception Handling & Zero DM Leaks Under Failure ---');

    await test('A1. Network Timeout (ETIMEDOUT): reactStatus MUST NOT fall back to private DM', async () => {
        clearStatusKeys();
        const targetParticipant = '967771234567@s.whatsapp.net';
        statusKeysMap.set('STATUS_TO_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_TO_01',
            participant: targetParticipant,
            fromMe: false,
            pushName: 'Network Failure User'
        });

        const netErr = new Error('connect ETIMEDOUT 157.240.22.53:443');
        netErr.code = 'ETIMEDOUT';
        const sock = createMockSocket({ failSendMessage: netErr });

        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await reactStatus(sock, 'STATUS_TO_01', '💚');
        });
        await runner.run();

        // 1. EMPIRICAL PROOF: Exactly 1 call to sendMessage, and it was to 'status@broadcast'
        assert.strictEqual(sock.calls.sendMessage.length, 1, 'sock.sendMessage must be called exactly once');
        assert.strictEqual(sock.calls.sendMessage[0].jid, 'status@broadcast');
        assert.deepStrictEqual(sock.calls.sendMessage[0].options, { statusJidList: [targetParticipant] });

        // 2. ABSOLUTE ZERO DM PROOF: Zero calls to the participant's direct chat
        const dmCalls = sock.calls.sendMessage.filter(c => c.jid !== 'status@broadcast');
        assert.strictEqual(dmCalls.length, 0, 'ZERO calls to private DM allowed!');

        // 3. Error event must be emitted to Android without unhandled exceptions
        const errEvent = capturedEvents.find(e => e.event === 'ERROR');
        assert.ok(errEvent, 'ERROR event must be emitted to Android');
        assert.ok(errEvent.data.error.includes('ETIMEDOUT'));
    });

    await test('A2. Baileys Boom Disconnect (428 Precondition Required): Zero DM fallback', async () => {
        clearStatusKeys();
        const targetLid = '99887766554433@lid';
        statusKeysMap.set('STATUS_BOOM_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_BOOM_01',
            participant: targetLid,
            fromMe: false
        });

        const boomErr = new Error('Connection Closed (428)');
        boomErr.isBoom = true;
        boomErr.output = { statusCode: 428, payload: { message: 'Precondition Required' } };
        const sock = createMockSocket({ failSendMessage: boomErr });

        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await reactStatus(sock, 'STATUS_BOOM_01', '🔥');
        });
        await runner.run();

        // Verify no DM calls
        assert.strictEqual(sock.calls.sendMessage.length, 1);
        assert.strictEqual(sock.calls.sendMessage[0].jid, 'status@broadcast');
        const dmCalls = sock.calls.sendMessage.filter(c => c.jid === targetLid || c.jid.includes('99887766554433'));
        assert.strictEqual(dmCalls.length, 0, 'ZERO DM calls to LID allowed');

        const errEvent = capturedEvents.find(e => e.event === 'ERROR');
        assert.ok(errEvent);
        assert.ok(errEvent.data.error.includes('428'));
    });

    await test('A3. Rate Limit / Overlimit (429): Zero DM fallback', async () => {
        clearStatusKeys();
        const targetParticipant = '967701122334@s.whatsapp.net';
        statusKeysMap.set('STATUS_RATE_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_RATE_01',
            participant: targetParticipant
        });

        const rateErr = new Error('rate-overlimit');
        const sock = createMockSocket({ failSendMessage: rateErr });

        const runner = captureAndroidEvents(async () => {
            await reactStatus(sock, 'STATUS_RATE_01', '👍');
        });
        await runner.run();

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        assert.strictEqual(sock.calls.sendMessage[0].jid, 'status@broadcast');
        assert.strictEqual(sock.calls.sendMessage.filter(c => c.jid !== 'status@broadcast').length, 0);
    });

    await test('A4. sendReceipt and readMessages failure: reactStatus continues safely to status@broadcast', async () => {
        clearStatusKeys();
        const targetLid = '88776655443322@lid';
        statusKeysMap.set('STATUS_RECEIPT_FAIL', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_RECEIPT_FAIL',
            participant: targetLid
        });

        const sock = createMockSocket({
            failSendReceipt: new Error('Receipt delivery failed'),
            failReadMessages: new Error('Read sync failure')
        });

        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await reactStatus(sock, 'STATUS_RECEIPT_FAIL', '❤️');
        });
        await runner.run();

        // Must still proceed to sendMessage on status@broadcast
        assert.strictEqual(sock.calls.sendMessage.length, 1);
        assert.strictEqual(sock.calls.sendMessage[0].jid, 'status@broadcast');
        assert.deepStrictEqual(sock.calls.sendMessage[0].options, { statusJidList: [targetLid] });

        // Must emit successful status reacted
        const reactEvent = capturedEvents.find(e => e.event === 'STATUS_REACTED');
        assert.ok(reactEvent);
        assert.strictEqual(reactEvent.data.success, true);
        assert.strictEqual(reactEvent.data.participant, targetLid);
    });

    await test('A5. Abrupt Null or Undefined Socket: Graceful early exit without crash', async () => {
        clearStatusKeys();
        statusKeysMap.set('STATUS_NULL_SOCK', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_NULL_SOCK',
            participant: '123456789@s.whatsapp.net'
        });

        // Must return gracefully without throwing
        await reactStatus(null, 'STATUS_NULL_SOCK', '💚');
        await viewStatus(null, 'STATUS_NULL_SOCK');
        assert.ok(true, 'Null socket handled gracefully');
    });

    // =========================================================================
    // CATEGORY B: Personal Account Participant Routing (@lid)
    // =========================================================================
    console.log('\n--- CATEGORY B: Personal Account (@lid) Strict Single-JID Routing ---');

    await test('B1. Standard @lid participant: statusJidList contains EXACTLY 1 item: [lid@lid]', async () => {
        clearStatusKeys();
        const lidParticipant = '12345678901234@lid';
        statusKeysMap.set('STATUS_LID_STD', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_LID_STD',
            participant: lidParticipant
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_LID_STD', '💚');

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.strictEqual(call.jid, 'status@broadcast');
        assert.strictEqual(Array.isArray(call.options.statusJidList), true);
        assert.strictEqual(call.options.statusJidList.length, 1, 'statusJidList MUST contain exactly 1 entry');
        assert.strictEqual(call.options.statusJidList[0], lidParticipant);
        assert.strictEqual(call.content.react.key.participant, lidParticipant);
        assert.strictEqual(call.content.react.key.remoteJid, 'status@broadcast');
    });

    await test('B2. Device-suffixed @lid (:0@lid): normalized to clean lid@lid in statusJidList', async () => {
        clearStatusKeys();
        const unnormalizedLid = '12345678901234:0@lid';
        const expectedNormalizedLid = '12345678901234@lid';

        statusKeysMap.set('STATUS_LID_SUFFIX', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_LID_SUFFIX',
            participant: unnormalizedLid
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_LID_SUFFIX', '🎉');

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.deepStrictEqual(call.options.statusJidList, [expectedNormalizedLid]);
        assert.strictEqual(call.content.react.key.participant, expectedNormalizedLid);
    });

    await test('B3. Mapped Phone to LID: participantInput empty, phone resolved to @lid', async () => {
        clearStatusKeys();
        clearLidMaps();

        const testPhone = '967711223344';
        const testLid = '55554444333322';
        linkLidAndPhone(testLid, testPhone, 'Resolved Contact');

        // Status key exists WITHOUT participant, only senderPhone
        statusKeysMap.set('STATUS_LID_RESOLV', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_LID_RESOLV',
            participant: null
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_LID_RESOLV', '❤️', testPhone);

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.strictEqual(call.jid, 'status@broadcast');
        assert.deepStrictEqual(call.options.statusJidList, [`${testLid}@lid`]);
        assert.strictEqual(call.content.react.key.participant, `${testLid}@lid`);
    });

    await test('B4. Explicit participant parameter overrides status key participant', async () => {
        clearStatusKeys();
        statusKeysMap.set('STATUS_OVERRIDE_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_OVERRIDE_01',
            participant: 'old_wrong_participant@lid'
        });

        const explicitLid = '99988877766655@lid';
        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_OVERRIDE_01', '🔥', '', explicitLid);

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.deepStrictEqual(call.options.statusJidList, [explicitLid]);
        assert.strictEqual(call.content.react.key.participant, explicitLid);
    });

    // =========================================================================
    // CATEGORY C: Business Account Participant Routing (@s.whatsapp.net)
    // =========================================================================
    console.log('\n--- CATEGORY C: Business Account (@s.whatsapp.net) Strict Single-JID Routing ---');

    await test('C1. Standard @s.whatsapp.net participant: statusJidList contains EXACTLY 1 item', async () => {
        clearStatusKeys();
        const bizJid = '967774445555@s.whatsapp.net';
        statusKeysMap.set('STATUS_BIZ_STD', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_BIZ_STD',
            participant: bizJid
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_BIZ_STD', '💚');

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.strictEqual(call.jid, 'status@broadcast');
        assert.strictEqual(Array.isArray(call.options.statusJidList), true);
        assert.strictEqual(call.options.statusJidList.length, 1);
        assert.strictEqual(call.options.statusJidList[0], bizJid);
        assert.strictEqual(call.content.react.key.participant, bizJid);
        assert.strictEqual(call.content.react.key.remoteJid, 'status@broadcast');
    });

    await test('C2. Multi-device suffixed @s.whatsapp.net (:2@s.whatsapp.net): normalized correctly', async () => {
        clearStatusKeys();
        const suffixedBizJid = '967774445555:2@s.whatsapp.net';
        const expectedBizJid = '967774445555@s.whatsapp.net';

        statusKeysMap.set('STATUS_BIZ_MULTI', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_BIZ_MULTI',
            participant: suffixedBizJid
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_BIZ_MULTI', '👏');

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.deepStrictEqual(call.options.statusJidList, [expectedBizJid]);
        assert.strictEqual(call.content.react.key.participant, expectedBizJid);
    });

    await test('C3. Business phone with unmapped LID: defaults to strictly single @s.whatsapp.net', async () => {
        clearStatusKeys();
        clearLidMaps();

        const rawPhone = '967733445566';
        statusKeysMap.set('STATUS_BIZ_UNMAPPED', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_BIZ_UNMAPPED',
            participant: null
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_BIZ_UNMAPPED', '💚', rawPhone);

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.deepStrictEqual(call.options.statusJidList, [`${rawPhone}@s.whatsapp.net`]);
    });

    // =========================================================================
    // CATEGORY D: Corrupted, Missing, and Malformed JIDs
    // =========================================================================
    console.log('\n--- CATEGORY D: Corrupted, Missing, or Malformed JIDs ---');

    await test('D1. Missing participant everywhere: Emits ERROR, ZERO socket calls, ZERO DM leaks', async () => {
        clearStatusKeys();
        // Status ID exists but has no participant and no phone is supplied
        statusKeysMap.set('STATUS_EMPTY_PARTICIPANT', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_EMPTY_PARTICIPANT',
            participant: null
        });

        const sock = createMockSocket();
        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await reactStatus(sock, 'STATUS_EMPTY_PARTICIPANT', '💚', '', '');
        });
        await runner.run();

        // Must NOT attempt to call sock.sendMessage or sock.sendReceipt
        assert.strictEqual(sock.calls.sendMessage.length, 0, 'No sendMessage calls allowed when participant missing');
        assert.strictEqual(sock.calls.sendReceipt.length, 0, 'No sendReceipt calls allowed when participant missing');

        // Must emit error
        const errEvent = capturedEvents.find(e => e.event === 'ERROR');
        assert.ok(errEvent);
        assert.ok(errEvent.data.error.includes('لم يتم العثور على ناشر الحالة'));
    });

    await test('D2. Completely unknown statusId: Emits ERROR, ZERO socket calls', async () => {
        clearStatusKeys();
        const sock = createMockSocket();

        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await reactStatus(sock, 'UNKNOWN_STATUS_9999', '💚', '', '');
        });
        await runner.run();

        assert.strictEqual(sock.calls.sendMessage.length, 0);
        const errEvent = capturedEvents.find(e => e.event === 'ERROR');
        assert.ok(errEvent);
    });

    await test('D3. Corrupted JID without domain or invalid characters: Handled gracefully without crash or DM leak', async () => {
        clearStatusKeys();
        const corruptedJid = 'invalid_corrupted_participant_123';
        statusKeysMap.set('STATUS_CORRUPTED_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_CORRUPTED_01',
            participant: corruptedJid
        });

        const sock = createMockSocket({
            // WhatsApp / Baileys socket throws when an invalid JID node is passed
            failSendMessage: (jid, content, options) => {
                if (options.statusJidList[0] === '' || !options.statusJidList[0].includes('@')) {
                    throw new Error('Baileys binary encode error: invalid JID format');
                }
            }
        });

        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await reactStatus(sock, 'STATUS_CORRUPTED_01', '💚');
        });
        await runner.run();

        // Must NOT crash
        // Zero DM calls
        const dmCalls = sock.calls.sendMessage.filter(c => c.jid !== 'status@broadcast');
        assert.strictEqual(dmCalls.length, 0, 'ZERO DM leaks under corrupted JID');

        // Caught error emitted to Android
        const errEvent = capturedEvents.find(e => e.event === 'ERROR');
        assert.ok(errEvent, 'Error event emitted to Android gracefully');
        assert.ok(errEvent.data.error.includes('invalid JID format'));
    });

    await test('D4. Empty or missing emoji defaults gracefully to green heart 💚', async () => {
        clearStatusKeys();
        const bizJid = '967770001111@s.whatsapp.net';
        statusKeysMap.set('STATUS_EMOJI_DEF', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_EMOJI_DEF',
            participant: bizJid
        });

        const sock = createMockSocket();
        await reactStatus(sock, 'STATUS_EMOJI_DEF', '');

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        assert.strictEqual(sock.calls.sendMessage[0].content.react.text, '💚');

        await reactStatus(sock, 'STATUS_EMOJI_DEF', null);
        assert.strictEqual(sock.calls.sendMessage[1].content.react.text, '💚');
    });

    // =========================================================================
    // CATEGORY E: Command Dispatcher Adversarial Integration
    // =========================================================================
    console.log('\n--- CATEGORY E: Command Dispatcher Inbound Fuzzing & Routing ---');

    await test('E1. REACT_STATUS through Command Dispatcher routes strictly to status@broadcast', async () => {
        clearStatusKeys();
        const lidParticipant = '44332211009988@lid';
        statusKeysMap.set('STATUS_CMD_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_CMD_01',
            participant: lidParticipant
        });

        const sock = createMockSocket();
        const dispatcher = createCommandDispatcher({
            getSocket: () => sock,
            startSession: async () => {},
            getAuthFolder: () => './test_auth'
        });

        await dispatcher(JSON.stringify({
            action: 'REACT_STATUS',
            statusId: 'STATUS_CMD_01',
            emoji: '🌟',
            participant: lidParticipant
        }));

        assert.strictEqual(sock.calls.sendMessage.length, 1);
        const call = sock.calls.sendMessage[0];
        assert.strictEqual(call.jid, 'status@broadcast');
        assert.deepStrictEqual(call.options.statusJidList, [lidParticipant]);
        assert.strictEqual(call.content.react.text, '🌟');
    });

    await test('E2. Dispatcher with Network Exception during REACT_STATUS: Zero DM leaks', async () => {
        clearStatusKeys();
        const bizJid = '967779990000@s.whatsapp.net';
        statusKeysMap.set('STATUS_CMD_FAIL', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_CMD_FAIL',
            participant: bizJid
        });

        const sock = createMockSocket({ failSendMessage: new Error('ECONNRESET: connection reset by peer') });
        const dispatcher = createCommandDispatcher({
            getSocket: () => sock,
            startSession: async () => {},
            getAuthFolder: () => './test_auth'
        });

        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await dispatcher(JSON.stringify({
                action: 'REACT_STATUS',
                statusId: 'STATUS_CMD_FAIL',
                emoji: '💚',
                participant: bizJid
            }));
        });
        await runner.run();

        // 1 call to status@broadcast only
        assert.strictEqual(sock.calls.sendMessage.length, 1);
        assert.strictEqual(sock.calls.sendMessage[0].jid, 'status@broadcast');
        assert.strictEqual(sock.calls.sendMessage.filter(c => c.jid !== 'status@broadcast').length, 0);

        const errEvent = capturedEvents.find(e => e.event === 'ERROR');
        assert.ok(errEvent);
        assert.ok(errEvent.data.error.includes('ECONNRESET'));
    });

    await test('E3. Command Dispatcher Fuzzing: Garbled lines, malformed JSON, truncated commands', async () => {
        const sock = createMockSocket();
        const dispatcher = createCommandDispatcher({
            getSocket: () => sock,
            startSession: async () => {},
            getAuthFolder: () => './test_auth'
        });

        // Non-JSON lines (must be silently ignored without throwing)
        await dispatcher('NOT_A_JSON_COMMAND');
        await dispatcher('');
        await dispatcher('     ');
        await dispatcher(null);
        await dispatcher(undefined);

        // Malformed JSON (must emit ERROR without throwing uncaught)
        let capturedEvents = [];
        const runner = captureAndroidEvents(async (events) => {
            capturedEvents = events;
            await dispatcher('{"action": "REACT_STATUS", "statusId": ');
            await dispatcher('{"unclosed: object');
        });
        await runner.run();

        assert.strictEqual(sock.calls.sendMessage.length, 0);
        assert.ok(capturedEvents.some(e => e.event === 'ERROR'));
    });

    await test('E4. VIEW_STATUS: Reads status and sends receipts strictly to status@broadcast (zero DM message)', async () => {
        clearStatusKeys();
        const lidParticipant = '99112233445566@lid';
        statusKeysMap.set('STATUS_VIEW_01', {
            remoteJid: 'status@broadcast',
            id: 'STATUS_VIEW_01',
            participant: lidParticipant
        });

        const sock = createMockSocket();
        const dispatcher = createCommandDispatcher({
            getSocket: () => sock,
            startSession: async () => {},
            getAuthFolder: () => './test_auth'
        });

        await dispatcher(JSON.stringify({
            action: 'VIEW_STATUS',
            statusId: 'STATUS_VIEW_01',
            participant: lidParticipant
        }));

        // VIEW_STATUS MUST NOT invoke sendMessage at all
        assert.strictEqual(sock.calls.sendMessage.length, 0, 'VIEW_STATUS must never call sock.sendMessage');

        // sendReceipt must target status@broadcast
        assert.strictEqual(sock.calls.sendReceipt.length, 1);
        assert.strictEqual(sock.calls.sendReceipt[0].remoteJid, 'status@broadcast');
        assert.strictEqual(sock.calls.sendReceipt[0].participant, lidParticipant);

        // readMessages must target status@broadcast
        assert.strictEqual(sock.calls.readMessages.length, 1);
        assert.strictEqual(sock.calls.readMessages[0][0].remoteJid, 'status@broadcast');
        assert.strictEqual(sock.calls.readMessages[0][0].participant, lidParticipant);
    });

    console.log('\n================================================================');
    console.log(`TEST SUMMARY: ${passedTests}/${totalTests} PASSED (${failedTests} FAILED)`);
    console.log('================================================================');

    if (failedTests > 0) {
        process.exit(1);
    }
}

runAdversarialSuite().catch(err => {
    console.error('Adversarial Test Suite fatal error:', err);
    process.exit(1);
});
