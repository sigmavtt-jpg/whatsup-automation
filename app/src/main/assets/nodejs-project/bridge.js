/**
 * WhatsApp Automation Engine — Bridge
 * Local TCP server and IPC bridge communicating with Android Kotlin client.
 * Handles line-delimited JSON commands and streams events back.
 */

import net from 'net';
import readline from 'readline';
import { CONFIG } from './config.js';

export const localSockets = new Set();
let activeServers = [];
let stdinRl = null;

/**
 * Emits an event to Android via console.log (IPC stdout) and all connected TCP sockets.
 * Format: [WHATSAPP_EVENT]{"event":"...","data":{...}}
 */
export function emitToAndroid(eventType, payload) {
    const message = JSON.stringify({ event: eventType, data: payload });
    const line = `[WHATSAPP_EVENT]${message}`;
    console.log(line);
    for (const socket of localSockets) {
        try {
            socket.write(line + '\n');
        } catch (_) {}
    }
}

/**
 * Initializes the TCP bridge servers and stdin reader.
 * @param {Function} commandHandler - Function(line: string) => Promise<void> | void
 */
export function initBridge(commandHandler) {
    // 1. Setup stdin reader for IPC
    try {
        if (!stdinRl && process.stdin) {
            stdinRl = readline.createInterface({
                input: process.stdin,
                output: process.stdout,
                terminal: false
            });
            stdinRl.on('error', () => {});
            stdinRl.on('line', (line) => {
                if (typeof commandHandler === 'function') {
                    commandHandler(line);
                }
            });
        }
    } catch (_) {}

    // Helper to setup a TCP server on a given port
    function createBridgeServer(port) {
        try {
            const server = net.createServer((socket) => {
                localSockets.add(socket);
                const clientRl = readline.createInterface({ input: socket });
                clientRl.on('line', (line) => {
                    if (typeof commandHandler === 'function') {
                        commandHandler(line);
                    }
                });
                socket.on('close', () => localSockets.delete(socket));
                socket.on('error', () => localSockets.delete(socket));
            });

            server.on('error', (err) => {
                // Ignore port in use or bind errors gracefully if one port is occupied
                console.error(`Bridge server error on port ${port}:`, err?.message || err);
            });

            server.listen(port, CONFIG.HOST, () => {
                emitToAndroid('BRIDGE_READY', { port });
            });

            activeServers.push(server);
        } catch (err) {
            console.error(`Failed to create bridge server on port ${port}:`, err?.message || err);
        }
    }

    // 2. Listen on primary port (6789 - used by BaileysBridgeManager.kt)
    createBridgeServer(CONFIG.PORT);

    // 3. Listen on alternate port (9099 - defined in PROJECT.md) if different from primary
    if (CONFIG.ALT_PORT && CONFIG.ALT_PORT !== CONFIG.PORT) {
        createBridgeServer(CONFIG.ALT_PORT);
    }
}

/**
 * Closes all active bridge servers and sockets.
 */
export function closeBridge() {
    for (const socket of localSockets) {
        try { socket.destroy(); } catch (_) {}
    }
    localSockets.clear();

    for (const server of activeServers) {
        try { server.close(); } catch (_) {}
    }
    activeServers = [];

    if (stdinRl) {
        try { stdinRl.close(); } catch (_) {}
        stdinRl = null;
    }
}
