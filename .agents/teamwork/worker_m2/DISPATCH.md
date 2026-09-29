## 2026-09-28T20:07:00Z

Task: Milestone 2: Selective Command & Activity Logging (Zero-Clutter & Privacy)
Project root: c:/Users/sO377/Downloads/whatsup
Working directory: c:/Users/sO377/Downloads/whatsup/.agents/teamwork/worker_m2/

Exclusive write ownership:
- app/src/main/kotlin/com/whatsup/automation/domain/usecase/UseCases.kt
- app/src/main/kotlin/com/whatsup/automation/data/local/AppDatabase.kt
- app/src/main/kotlin/com/whatsup/automation/data/local/dao/Daos.kt
- app/src/main/kotlin/com/whatsup/automation/presentation/screens/logs/LogsScreen.kt
- app/src/main/kotlin/com/whatsup/automation/presentation/screens/dashboard/DashboardScreen.kt

Tasks:
1. In `UseCases.kt` (lines 148-156 in ProcessIncomingMessageUseCase):
   - Remove `logRepository.insertLog(...)` completely when no rule matches.
   - Unmatched incoming messages must be silently ignored and return `ProcessResult.NoMatch` with ZERO database writes.
2. In `UseCases.kt` (executeRuleActions):
   - Ensure automated replies are logged with:
     * `extractedName`: populated via `deviceContactsManager.getContactDisplayName(targetPhone) ?: message.senderName.takeIf { it.isNotBlank() }`
     * `matchedRule`: formatted with rule name and trigger pattern (e.g. `"${rule.name} (${rule.pattern})"`)
     * `actionExecuted`: formatted with reply text (e.g. `actionDesc` or reply content)
     * `senderPhone`: `targetPhone`
     * `status`: `LogStatus.SUCCESS`
3. In `Daos.kt` (LogDao):
   - Add query:
     `@Query("DELETE FROM activity_logs WHERE status = 'NO_MATCH' OR actionExecuted LIKE '%تجاهل%'") suspend fun purgeClutterLogs(): Int`
4. In `AppDatabase.kt`:
   - In `createCallback()`, implement `onOpen(db: SupportSQLiteDatabase)` executing:
     `db.execSQL("DELETE FROM activity_logs WHERE status = 'NO_MATCH' OR actionExecuted LIKE '%تجاهل%'")`
     to automatically purge legacy clutter on startup across all devices.
5. In `LogsScreen.kt` and `DashboardScreen.kt`:
   - Polish UI: remove the "بدون مطابقة" / NO_MATCH filter chip from LogsScreen.
   - In DashboardScreen, update the stat card that showed "بدون مطابقة" / ignored count to display a meaningful metric (such as active automation rules or total successful replies) or cleanly hide the zero-ignored card.
6. Verify Kotlin compilation:
   - Run `.\gradlew.bat compileDebugKotlin --dry-run` or check with compiler.
