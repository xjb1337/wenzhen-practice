package com.xjb.wenzhen.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ConsultationEntity::class, ConsultationDraftEntity::class, SavedCaseEntity::class],
    version = 5,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun consultationDao(): ConsultationDao
    abstract fun consultationDraftDao(): ConsultationDraftDao
    abstract fun savedCaseDao(): SavedCaseDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wenzhen_practice.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }

        const val MIGRATION_1_2_SQL =
            "ALTER TABLE consultations ADD COLUMN patientPersonality TEXT NOT NULL DEFAULT 'COOPERATIVE'"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_1_2_SQL)
            }
        }

        const val MIGRATION_2_3_SQL = "ALTER TABLE consultations ADD COLUMN encounterType TEXT NOT NULL DEFAULT 'OUTPATIENT'"
        const val MIGRATION_2_3_SQL_TREATMENT = "ALTER TABLE consultations ADD COLUMN treatmentAnswer TEXT NOT NULL DEFAULT ''"
        const val MIGRATION_2_3_SQL_RECORD = "ALTER TABLE consultations ADD COLUMN recordType TEXT NOT NULL DEFAULT 'INPATIENT'"
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_2_3_SQL)
                database.execSQL(MIGRATION_2_3_SQL_TREATMENT)
                database.execSQL(MIGRATION_2_3_SQL_RECORD)
            }
        }

        const val MIGRATION_3_4_SQL = "CREATE TABLE IF NOT EXISTS consultation_drafts (id INTEGER NOT NULL, caseJson TEXT NOT NULL, conversationJson TEXT NOT NULL, patientPersonality TEXT NOT NULL, encounterType TEXT NOT NULL, startedAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL, PRIMARY KEY(id))"
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_3_4_SQL)
            }
        }

        // 注意：favorite 列必须与 SavedCaseEntity / 导出的 5.json 完全一致（NOT NULL、无 DEFAULT）。
        // 早期这里写了 `DEFAULT 0`，而实体是 `favorite: Boolean = false`（无 @ColumnInfo(defaultValue)），
        // Room 在 onUpgrade 后会用 TableInfo 校验列默认值，DEFAULT 不一致会抛
        // IllegalStateException: Migration didn't properly handle: saved_cases，导致 v4→v5 升级用户冷启动崩溃。
        // favorite 由实体默认值在每次写入时提供，数据库层面不需要默认值。
        const val MIGRATION_4_5_SQL = "CREATE TABLE IF NOT EXISTS saved_cases (id TEXT NOT NULL, caseJson TEXT NOT NULL, createdAt INTEGER NOT NULL, favorite INTEGER NOT NULL, PRIMARY KEY(id))"
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(MIGRATION_4_5_SQL)
            }
        }
    }
}
