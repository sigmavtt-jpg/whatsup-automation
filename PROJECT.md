# Project: WhatsApp Automation Android Engine

## Architecture
- **Layer 1: Node.js / Baileys Embedded Engine (`app/src/main/assets/nodejs-project/`)**:
  - Embedded Node.js runtime executing Baileys library for WhatsApp Multi-Device connection over local TCP bridge (port 9099).
  - Modularized ES modules:
    - `config.js`: Port, paths, configuration, timing constants.
    - `bridge.js`: Local TCP server communicating with Android Kotlin client (JSON line-delimited events/commands).
    - `lid-resolver.js`: Phone-to-LID and LID-to-phone mapping and resolution.
    - `status-manager.js`: Status event processing, keys caching (`statusKeysMap`), status viewing, and strict single-JID status reactions without DM side-effects.
    - `command-dispatcher.js`: Routing commands (`SEND_MESSAGE`, `REACT_MESSAGE`, `VIEW_STATUS`, `REACT_STATUS`, etc.) to Baileys socket.
    - `index.js`: Main entry point orchestrating lifecycle, socket initialization, and module binding.
  - Build tool: `build_bundle.cjs` bundling via esbuild into `bundle.cjs` and `dist/bundle.cjs`.

- **Layer 2: Kotlin Android Client (`app/src/main/kotlin/com/whatsup/automation/`)**:
  - **Service Layer (`service/`)**:
    - `WhatsAppForegroundService`: 24/7 background foreground service maintaining connection to local Node.js engine, processing inbound messages & statuses.
    - `WhatsAppNotificationListenerService`: Android notification listener reacting to notifications via `RemoteInput`.
    - `WhatsAppAccessibilityService`: Fallback accessibility service.
    - `BootReceiver`: Auto-starts foreground service upon device reboot.
  - **Data Layer (`data/`)**:
    - Room Database (`AppDatabase`, version 4+): Entities (`RuleEntity`, `LogEntity`, `StatusEntity`, `ContactEntity`, `GroupLogEntity`).
    - Repository implementations (`RepositoriesImpl.kt`).
    - Engine Bridge (`WhatsAppEngine.kt`, `BaileysBridgeManager.kt`).
  - **Domain Layer (`domain/`)**:
    - Domain models (`Rule`, `ActivityLog`, `StatusStory`, `Contact`, `GroupMessageLog`).
    - Use Cases (`ProcessIncomingMessageUseCase`, `RegisterContactUseCase`, etc.).
    - Rule Engine with regex, contains, exact match, startsWith.
  - **Presentation Layer (`presentation/`)**:
    - Jetpack Compose with Dark Glassmorphism, MVI/MVVM with StateFlow, LTR phone inputs.
    - Screens: `DashboardScreen`, `RulesScreen`, `LogsScreen`, `StatusesScreen`, `ContactsScreen`, `SettingsScreen`.

## Feature Inventory
| # | Feature | Description | Milestone | Source |
|---|---------|-------------|-----------|--------|
| 1 | R1.1: Strict Single-JID Status Reactions | React to status posts (💚/emoji) strictly to `status@broadcast` with `statusJidList: [targetParticipant]` without DM fallback | M1 | ORIGINAL_REQUEST §R1 |
| 2 | R1.2: Personal (LID) & Business Status Support | Correctly target `@lid` for personal accounts and `@s.whatsapp.net` for business accounts | M1 & M3 | ORIGINAL_REQUEST §R1 |
| 3 | R1.3: Zero DM Side-Effects | Eliminate duplicate messages and unkeyed pending messages ("Waiting for this message") in private 1-on-1 chats | M1 | ORIGINAL_REQUEST §R1 |
| 4 | R2.1: Silent Ignore of Unmatched Messages | Incoming messages that do NOT match any rule are silently ignored without database writes (remove NO_MATCH) | M2 | ORIGINAL_REQUEST §R2 |
| 5 | R2.2: Purge Room DB Clutter | Purge existing unhandled `NO_MATCH` and clutter records from Room Database on startup | M2 | ORIGINAL_REQUEST §R2 |
| 6 | R2.3: Rich Automated Reply Logging | Record successful auto-replies with conversation name, trigger pattern condition, and reply text | M2 | ORIGINAL_REQUEST §R2 |
| 7 | R2.4: Status Views & Reactions Logging | Record status views and status reactions with contact/publisher name | M2 | ORIGINAL_REQUEST §R2 |
| 8 | R2.5: Contact Registration Logging | Maintain clean logging of contact registrations and updates | M2 | ORIGINAL_REQUEST §R2 |
| 9 | R3.1: Node.js Modularization into ES Modules | Refactor monolithic `index.js` (~900 lines) into `config.js`, `bridge.js`, `lid-resolver.js`, `status-manager.js`, `command-dispatcher.js`, `index.js` | M1 | ORIGINAL_REQUEST §R3 |
| 10 | R3.2: Node.js Bundle Build | Bundle cleanly via `node build_bundle.cjs` into `bundle.cjs` and `dist/bundle.cjs` | M1 | ORIGINAL_REQUEST §AC |
| 11 | R3.3: Kotlin Participant Preservation | Preserve `participant` JID across `StatusStory`, `StatusEntity`, and Room database to avoid losing LID identity | M3 | Survey Findings |
| 12 | R3.4: Notification Listener Manifest Registration | Register `WhatsAppNotificationListenerService` in `AndroidManifest.xml` | M3 | Survey Findings |
| 13 | R3.5: Clean Architecture Verification | Maintain strict Clean Architecture across Presentation, Domain, Data, and Service layers with zero god classes | M3 | ORIGINAL_REQUEST §R3 |
| 14 | Final Verification: End-to-End Build & Integrity | Compile Node.js bundle and Android APK via Gradle (`assembleDebug`), conduct review, challenger stress tests, and forensic audit | M4 | ORIGINAL_REQUEST §AC |

## Milestones
| # | Name | Scope | Dependencies | Status |
|---|------|-------|-------------|--------|
| 1 | M1: Node.js Engine Modularization & Status Reaction Fix | Refactor index.js into ES modules; fix status reaction single-JID routing; eliminate DM fallback; verify build_bundle.cjs | None | PLANNED |
| 2 | M2: Selective Activity Logging & DB Purge | Remove NO_MATCH logging in UseCases.kt; purge DB clutter; enrich automated reply and status logs; polish UI | None | PLANNED |
| 3 | M3: Kotlin Status JID Preservation & Architecture Polish | Add participant preservation in StatusEntity/Room; pass participant JID in engine; register NotificationListenerService in manifest | M1, M2 | PLANNED |
| 4 | M4: Final Build, E2E Verification & Forensic Audit | Compile Node.js bundle, run Gradle assembleDebug, verify integrity and pass all gate criteria | M1, M2, M3 | PLANNED |

## Interface Contracts
### Kotlin Client ↔ Node.js Local Bridge (TCP Port 9099)
- Line-delimited JSON messages.
- Outbound Commands from Kotlin:
  - `{"type": "SEND_MESSAGE", "to": "<jid>", "text": "...", "replyToId": "..."}`
  - `{"type": "REACT_STATUS", "statusId": "<id>", "emoji": "💚", "participant": "<targetParticipantJid>"}`
  - `{"type": "VIEW_STATUS", "statusId": "<id>", "participant": "<targetParticipantJid>"}`
- Inbound Events from Node.js:
  - `{"event": "CONNECTED", "user": {"id": "..."}}`
  - `{"event": "DISCONNECTED", "reason": "..."}`
  - `{"event": "QR", "qr": "..."}`
  - `{"event": "PAIRING_CODE", "code": "..."}`
  - `{"event": "MESSAGE_RECEIVED", "message": {"id": "...", "sender": "...", "text": "...", "fromMe": false, ...}}`
  - `{"event": "STATUS_RECEIVED", "status": {"id": "...", "participant": "<targetParticipantJid>", "senderPhone": "...", "senderName": "...", "text": "...", "timestamp": ...}}`
  - `{"event": "STATUS_VIEWED", "statusId": "...", "participant": "..."}`
  - `{"event": "STATUS_REACTED", "statusId": "...", "participant": "...", "emoji": "..."}`

## Code Layout
- `app/src/main/assets/nodejs-project/`:
  - `index.js` (main entry point)
  - `config.js` (configuration constants)
  - `bridge.js` (TCP bridge communication)
  - `lid-resolver.js` (LID/phone resolution cache)
  - `status-manager.js` (status events, key caching, view and reaction execution)
  - `command-dispatcher.js` (inbound TCP command handler)
  - `build_bundle.cjs` (esbuild bundler script)
- `app/src/main/kotlin/com/whatsup/automation/`:
  - `data/local/entity/Entities.kt` (`LogEntity`, `StatusEntity`)
  - `data/local/dao/Daos.kt` (`LogDao`, `StatusDao`)
  - `data/local/AppDatabase.kt` (`AppDatabase`)
  - `data/repository/RepositoriesImpl.kt` (`LogRepositoryImpl`, `StatusRepositoryImpl`)
  - `data/engine/WhatsAppEngine.kt`
  - `domain/model/Models.kt` (`ActivityLog`, `StatusStory`)
  - `domain/usecase/UseCases.kt` (`ProcessIncomingMessageUseCase`, etc.)
  - `service/WhatsAppForegroundService.kt`
  - `service/WhatsAppNotificationListenerService.kt`
  - `presentation/screens/logs/LogsScreen.kt`
  - `presentation/screens/dashboard/DashboardScreen.kt`
  - `app/src/main/AndroidManifest.xml`
