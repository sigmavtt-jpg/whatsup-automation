package com.whatsup.automation.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.whatsup.automation.data.local.entity.SyncedContactDao
import com.whatsup.automation.data.local.dao.LogDao
import com.whatsup.automation.data.local.dao.RuleDao
import com.whatsup.automation.data.local.entity.SyncedContactEntity
import com.whatsup.automation.data.local.entity.LogEntity
import com.whatsup.automation.data.local.entity.RuleEntity

/**
 * قاعدة بيانات Room الرئيسية — المصدر الوحيد للحقيقة (Single Source of Truth).
 */
@Database(
    entities = [
        RuleEntity::class,
        LogEntity::class,
        SyncedContactEntity::class,
        com.whatsup.automation.data.local.entity.StatusEntity::class,
        com.whatsup.automation.data.local.entity.GroupEntity::class,
        com.whatsup.automation.data.local.entity.GroupMemberEntity::class,
        com.whatsup.automation.data.local.entity.GroupLogEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun ruleDao(): RuleDao
    abstract fun logDao(): LogDao
    abstract fun syncedContactDao(): SyncedContactDao
    abstract fun statusDao(): com.whatsup.automation.data.local.dao.StatusDao
    abstract fun groupDao(): com.whatsup.automation.data.local.dao.GroupDao
    abstract fun groupMemberDao(): com.whatsup.automation.data.local.dao.GroupMemberDao
    abstract fun groupLogDao(): com.whatsup.automation.data.local.dao.GroupLogDao

    companion object {
        const val DATABASE_NAME = "whatsup_automation.db"

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE statuses ADD COLUMN participant TEXT")
            }
        }

        /**
         * إنشاء callback لتزويد قاعدة البيانات بالبيانات الأولية (القواعد الافتراضية).
         */
        fun createCallback(): Callback {
            return object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    // القاعدة 1: الرد على التحية والسلام
                    db.execSQL("""
                        INSERT INTO rules (name, description, patternType, patternValue, actionsJson, priority, isEnabled, createdAt)
                        VALUES (
                            'الرد على التحية والسلام',
                            'الرد بقرعة ترحيبية مهذبة عند استقبال عبارات السلام والتحية',
                            'CONTAINS',
                            'سلام, السلام عليكم, مرحبا, هلا, مساء الخير, صباح الخير',
                            '[{"type":"SEND_REPLY","message":"وعليكم السلام ورحمة الله وبركاته 🌹
أهلاً وسهلاً بك، تفضل كيف أقدر أساعدك؟
حياك الله أخي الكريم ✨"}]',
                            1,
                            1,
                            ${System.currentTimeMillis()}
                        )
                    """.trimIndent())
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("DELETE FROM activity_logs WHERE status = 'NO_MATCH' OR actionExecuted LIKE '%تجاهل%'")
                }
            }
        }
    }
}
