# Execution Plan: WhatsApp Automation Engine Refactoring & Reliability

## Objective
Execute R1, R2, R3 per ORIGINAL_REQUEST.md:
1. R1: WhatsApp Status Reactions Reliability (LID & Business accounts, single-JID routing, eliminate DM side-effects).
2. R2: Selective Command & Activity Logging (Zero-Clutter & Privacy, remove NO_MATCH, purge DB clutter).
3. R3: Modular Clean Architecture (refactor index.js into ES modules, ensure clean architecture across Kotlin layers).

## Architecture & Work Breakdown
- **Survey Phase**:
  - Dispatch Explorer 1: Inspect Node.js project (`app/src/main/assets/nodejs-project/`) specifically `index.js`, status handling, Baileys reaction mechanism, and build tooling (`build_bundle.cjs`).
  - Dispatch Explorer 2: Inspect Kotlin ActivityLog system, Room DB schema, `ProcessIncomingMessageUseCase.kt`, `ActivityLogDao`, and database migration/purge logic.
  - Dispatch Explorer 3: Inspect Kotlin architecture, services (`WhatsAppForegroundService`, `WhatsAppNotificationListenerService`), repository, viewmodels, and status reaction triggers.
- **Milestone 1 (M1)**: Status Reactions Reliability (Node.js engine single-JID routing, Baileys reaction fix for LID and Business).
- **Milestone 2 (M2)**: Selective Activity Logging & DB Purge (Silent ignore for unhandled messages, remove NO_MATCH, format logs, migration/cleanup).
- **Milestone 3 (M3)**: Modularization of Node.js engine & Kotlin architecture adherence.
- **Milestone 4 (Final)**: Integration build verification (`node build_bundle.cjs`, `./gradlew assembleDebug`), Review & Forensic Audit.
