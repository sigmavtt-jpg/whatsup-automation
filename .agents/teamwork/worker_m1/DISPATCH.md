## 2026-09-28T20:06:09Z

You are a Worker implementing Milestone 1: Node.js Engine Modularization & Status Reactions Reliability.
Your working directory is: c:/Users/sO377/Downloads/whatsup/.agents/teamwork/worker_m1/
Project root: c:/Users/sO377/Downloads/whatsup

Read the authoritative user request at:
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/ORIGINAL_REQUEST.md

Read the project specification at:
c:/Users/sO377/Downloads/whatsup/PROJECT.md

Read the survey findings at:
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_1/survey_report.md
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_1/handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

CRITICAL CONSTRAINTS (TOKEN DRAIN PREVENTION per AGENTS.md):
- BLOCKED DIRECTORIES (DO NOT READ, INSPECT, OR SEARCH): app/build/, bin/, .cache/, include/, node_modules/, .gradle/, .kotlin/
- Allowed paths: app/src/main/assets/nodejs-project/*.js, app/src/main/assets/nodejs-project/package.json, app/src/main/assets/nodejs-project/build_bundle.cjs, AGENTS.md.
- Never read files > 60KB in one chunk without line slicing.

EXCLUSIVE WRITE OWNERSHIP:
You own all files in: `c:/Users/sO377/Downloads/whatsup/app/src/main/assets/nodejs-project/`
Do NOT edit Kotlin files outside this directory.

Tasks:
1. Refactor `app/src/main/assets/nodejs-project/index.js` into clean, single-responsibility ES modules:
   - `config.js`: Port (9099), paths, socket configs, constants.
   - `bridge.js`: Local TCP server handling line-delimited JSON commands from Android Kotlin client and streaming events back.
   - `lid-resolver.js`: LID-to-phone and phone-to-LID caching, contact resolution.
   - `status-manager.js`: Status message parsing, statusKeysMap management, status viewing, and status reactions.
   - `command-dispatcher.js`: Routing commands (`SEND_MESSAGE`, `REACT_MESSAGE`, `VIEW_STATUS`, `REACT_STATUS`, etc.) to Baileys socket.
   - `index.js`: Main entry point orchestrating lifecycle and module initialization.

2. Fix Status Reactions Reliability (R1 & AGENTS.md Section 8):
   - REMOVE the faulty DM fallback in lines 839-848 of the original index.js completely! NEVER send a status reaction to a private 1-on-1 chat.
   - Implement Strict Single-JID Routing: Status reactions must be sent EXCLUSIVELY to `'status@broadcast'` with `{ statusJidList: [targetParticipant] }`.
   - `targetParticipant` must be the original participant JID (e.g. `@lid` for personal accounts, `@s.whatsapp.net` for business accounts). If `participant` is passed in the `REACT_STATUS` command, use it directly; otherwise look up from `statusKeysMap` or lid resolver.
   - Ensure zero duplicate messages and zero "Waiting for this message" pending messages in private chats.

3. Verify bundling:
   - Run `node build_bundle.cjs` in `app/src/main/assets/nodejs-project/`.
   - Verify that `bundle.cjs` is produced and copied to `dist/bundle.cjs` cleanly without syntax or bundling errors.

Deliverable:
Write your detailed implementation and verification report to:
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/worker_m1/handoff.md
Update progress.md in your working directory. Send a message to parent when done.
