## 2026-09-28T20:18:28Z

You are a Worker implementing Milestone 3: Kotlin Status JID Preservation & Architecture Polish.
Your working directory is: c:/Users/sO377/Downloads/whatsup/.agents/teamwork/worker_m3/
Project root: c:/Users/sO377/Downloads/whatsup

Read the authoritative user request at:
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/ORIGINAL_REQUEST.md

Read the project specification at:
c:/Users/sO377/Downloads/whatsup/PROJECT.md

Read the survey findings at:
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_3/survey_report.md
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/explorer_survey_3/handoff.md

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A teamwork_preview_auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

CRITICAL CONSTRAINTS (TOKEN DRAIN PREVENTION per AGENTS.md):
- BLOCKED DIRECTORIES (DO NOT READ, INSPECT, OR SEARCH): app/build/, bin/, .cache/, include/, node_modules/, .gradle/, .kotlin/
- Allowed paths: app/src/main/kotlin/**/*.kt, app/src/main/AndroidManifest.xml, AGENTS.md, root gradle files.
- Never read files > 60KB in one chunk without line slicing.

EXCLUSIVE WRITE OWNERSHIP:
You own these files:
- app/src/main/kotlin/com/whatsup/automation/domain/model/Models.kt
- app/src/main/kotlin/com/whatsup/automation/data/local/entity/Entities.kt
- app/src/main/kotlin/com/whatsup/automation/data/local/AppDatabase.kt
- app/src/main/kotlin/com/whatsup/automation/data/repository/RepositoriesImpl.kt
- app/src/main/kotlin/com/whatsup/automation/data/engine/WhatsAppEngine.kt
- app/src/main/kotlin/com/whatsup/automation/service/WhatsAppForegroundService.kt
- app/src/main/AndroidManifest.xml

Tasks:
1. Preserve Status Participant JID across Kotlin & Room:
   - In `Models.kt`: Add `val participant: String? = null` to `StatusStory` (if not already present).
   - In `Entities.kt`: Add `val participant: String? = null` to `StatusEntity`.
   - In `AppDatabase.kt`: Update database version if necessary or add migration / check database builder configuration. (AppDatabase already has `fallbackToDestructiveMigration()` or migrations, verify how AppDatabase handles schema updates; if bumping version, e.g. from 4 to 5, add migration `MIGRATION_4_5` adding column `participant TEXT` or verify database builder).
   - In `RepositoriesImpl.kt`: Map `participant` in `toDomain()` and `toEntity()` so that when status stories are saved and retrieved from Room, the original `participant` JID (e.g. `<lid>@lid` or `<phone>@s.whatsapp.net`) is never lost.

2. Update WhatsAppEngine & Service to use Participant JID:
   - In `WhatsAppEngine.kt`:
     * When receiving status event (e.g., `INCOMING_STATUS` / `STATUS_RECEIVED`), extract `participant` JID from the JSON and populate `participant` in `StatusStory`.
     * In `markStatusViewed` and `reactToStatus`: accept optional `participant: String? = null`, and include `"participant": participant` in the JSON command sent to Node.js bridge.
   - In `WhatsAppForegroundService.kt`:
     * In auto-view and auto-react methods (`onStatusReceived` / `handleStatusAutoViewAndReact` / `viewAllUnviewedStatuses`), pass `status.participant ?: status.senderPhone` when calling `markStatusViewed` and `reactToStatus`.

3. Register `WhatsAppNotificationListenerService` in `AndroidManifest.xml`:
   - Add the `<service>` declaration for `com.whatsup.automation.service.WhatsAppNotificationListenerService` with:
     * `android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"`
     * `<intent-filter><action android:name="android.service.notification.NotificationListenerService" /></intent-filter>`
     * `android:exported="true"`

4. Build & Verify:
   - Run `.\gradlew.bat compileDebugKotlin` to verify zero compilation errors.

Deliverable:
Write your detailed implementation and verification report to:
c:/Users/sO377/Downloads/whatsup/.agents/teamwork/worker_m3/handoff.md
Update progress.md in your working directory. Send a message to parent when done.
