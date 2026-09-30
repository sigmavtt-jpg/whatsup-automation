/**
 * WhatsApp Automation Engine — Forensic E2E Tracer
 * Temporary, zero-side-effect observability module for Status Reaction lifecycle.
 */

export const activeTraces = new Map();

/**
 * Creates and initializes a new trace context.
 */
export function createTrace(statusId, initialData = {}) {
    const traceId = `TRACE_${statusId.substring(0, 8)}_${Date.now()}`;
    const trace = {
        traceId,
        statusId,
        startTime: Date.now(),
        stages: [],
        data: { ...initialData },
        completed: false
    };
    activeTraces.set(traceId, trace);
    activeTraces.set(statusId, trace);
    return trace;
}

export function getTrace(idOrStatusId) {
    return activeTraces.get(idOrStatusId) || null;
}

/**
 * Records a structured forensic stage log.
 */
export function recordStage(traceIdOrStatusId, stage, file, fn, details = {}, status = 'PASS', error = null) {
    let trace = getTrace(traceIdOrStatusId);
    if (!trace) {
        // Create fallback trace if not yet tracked
        const id = String(traceIdOrStatusId);
        trace = createTrace(id, { note: 'Auto-created in ' + stage });
    }

    const now = Date.now();
    const durationMs = now - trace.startTime;

    const entry = {
        timestamp: new Date(now).toISOString(),
        traceId: trace.traceId,
        statusId: trace.statusId,
        stage,
        file,
        function: fn,
        status,
        durationMs,
        details: sanitizeDetails(details),
        error: error ? (error.message || String(error)) : null,
        stack: error?.stack ? error.stack.split('\n').slice(0, 3).join(' | ') : null
    };

    trace.stages.push(entry);

    // Print structured JSON log to stdout (captured by Android logcat)
    console.log(`[FORENSIC_TRACE] ${JSON.stringify(entry)}`);

    return entry;
}

/**
 * Strips secrets, keys, and tokens from details.
 */
function sanitizeDetails(obj) {
    if (!obj || typeof obj !== 'object') return obj;
    const sanitized = {};
    for (const [k, v] of Object.entries(obj)) {
        if (k.toLowerCase().includes('key') && typeof v === 'object' && v !== null) {
            // keep message key structure (id, remoteJid, participant)
            sanitized[k] = {
                remoteJid: v.remoteJid,
                id: v.id,
                participant: v.participant,
                fromMe: v.fromMe
            };
        } else if (k.toLowerCase().includes('secret') || k.toLowerCase().includes('token') || k.toLowerCase().includes('password')) {
            sanitized[k] = '[REDACTED]';
        } else {
            sanitized[k] = v;
        }
    }
    return sanitized;
}

/**
 * Hooks into Baileys WebSocket and event stream safely.
 */
export function attachSocketTraceHook(sock) {
    if (!sock) return;

    // 1. Hook WebSocket write if available
    try {
        if (sock.ws && typeof sock.ws.send === 'function' && !sock.ws._isTraceHooked) {
            const originalSend = sock.ws.send.bind(sock.ws);
            sock.ws.send = function (data, ...args) {
                try {
                    const dataLength = data ? (data.length || data.byteLength || 0) : 0;
                    console.log(`[FORENSIC_TRACE] {"stage":"WEBSOCKET_FRAME_OUT","bytes":${dataLength},"timestamp":"${new Date().toISOString()}"}`);
                } catch (_) {}
                return originalSend(data, ...args);
            };
            sock.ws._isTraceHooked = true;
        }
    } catch (_) {}

    // 2. Hook receipts and reaction events
    try {
        sock.ev.on('message-receipt.update', (receipts) => {
            for (const r of receipts || []) {
                console.log(`[FORENSIC_TRACE] {"stage":"RECEIPT_UPDATE","keyId":"${r.key?.id}","type":"${r.receipt?.readTimestamp ? 'READ' : 'DELIVERY'}","timestamp":"${new Date().toISOString()}"}`);
            }
        });
        sock.ev.on('messages.reaction', (reactions) => {
            for (const r of reactions || []) {
                console.log(`[FORENSIC_TRACE] {"stage":"REACTION_EVENT_RECV","keyId":"${r.key?.id}","text":"${r.reaction?.text}","timestamp":"${new Date().toISOString()}"}`);
            }
        });
    } catch (_) {}
}

/**
 * Prints the final ASCII Event Timeline report for a trace.
 */
export function printTimelineReport(traceIdOrStatusId) {
    const trace = getTrace(traceIdOrStatusId);
    if (!trace) return;

    const stagesMap = new Map();
    for (const s of trace.stages) {
        stagesMap.set(s.stage, s);
    }

    const order = [
        ['01', 'STATUS_RECEIVED'],
        ['02', 'STATUS_STORED'],
        ['03', 'KOTLIN_RECEIVED'],
        ['04', 'KOTLIN_COMMAND'],
        ['05', 'DISPATCHER'],
        ['06', 'REACTION_BUILD'],
        ['07', 'BAILEYS_SEND'],
        ['08', 'BAILEYS_SEND_RETURN'],
        ['09', 'WEBSOCKET_FRAME_OUT'],
        ['10', 'WHATSAPP_ACK_RECEIVED'],
        ['11', 'DELIVERY_RECEIPT'],
        ['12', 'FINAL_RESULT']
    ];

    let firstFailure = 'NONE';
    let firstUnconfirmed = 'NONE';
    let boundary = 'WHATSAPP_SERVER_ACK';

    const lines = [];
    lines.push('\n==================== TRACE TIMELINE ====================');
    lines.push(`traceId: ${trace.traceId}`);
    lines.push(`statusId: ${trace.statusId}`);
    lines.push('--------------------------------------------------------');

    for (const [num, stageName] of order) {
        const found = stagesMap.get(stageName);
        if (found) {
            const statusStr = found.status.padEnd(7, ' ');
            const dur = `(${found.durationMs}ms)`.padStart(10, ' ');
            lines.push(`${num} ${stageName.padEnd(20, '.')} ${statusStr} ${dur}`);
            if (found.status === 'FAIL' && firstFailure === 'NONE') {
                firstFailure = stageName;
            }
        } else {
            if (stageName === 'DELIVERY') {
                lines.push(`${num} ${stageName.padEnd(20, '.')} OBSERVABILITY_BOUNDARY_REACHED`);
                if (firstUnconfirmed === 'NONE') firstUnconfirmed = 'DELIVERY (Client UI Heart Rendering is out-of-band)';
            } else if (stageName === 'FINAL_RESULT') {
                const finalStatus = firstFailure === 'NONE' ? 'PASS (SERVER_ACKED)' : 'FAIL';
                lines.push(`${num} ${stageName.padEnd(20, '.')} ${finalStatus}`);
            } else {
                lines.push(`${num} ${stageName.padEnd(20, '.')} UNKNOWN`);
                if (firstUnconfirmed === 'NONE') firstUnconfirmed = stageName;
            }
        }
    }

    lines.push('--------------------------------------------------------');
    lines.push(`FIRST_UNCONFIRMED_STAGE: ${firstUnconfirmed}`);
    lines.push(`FIRST_FAILURE: ${firstFailure}`);
    lines.push(`OBSERVABILITY_BOUNDARY: ${boundary}`);
    lines.push('========================================================\n');

    const report = lines.join('\n');
    console.log(report);
    trace.completed = true;
    return report;
}
