package com.xjb.wenzhen.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 走真实 Room 升级路径的回归测试。
 *
 * 与 [MigrationExecutionTest] 直接 `execSQL` 不同，本测试让 Room 自己执行 onUpgrade：
 * 迁移完成后 Room 会调用 onValidateSchema 用 TableInfo 逐列比对实体与真实表结构。
 *
 * P0-2 的现场是 `MIGRATION_4_5_SQL` 曾给 favorite 加了 `DEFAULT 0`，而实体没有
 * `@ColumnInfo(defaultValue = "0")`。实测 Room 2.6.1 的 TableInfo.Column.equals 只在实体侧
 * 声明了 defaultValue 时才比较默认值，因此这不会直接抛 Migration didn't properly handle；
 * 但 schema 文本与导出 5.json 不一致本身仍应修正，本测试用 PRAGMA 锁住「favorite 无默认值」。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationRoomUpgradeTest {
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(DB_NAME)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `v4数据库经Room升级到v5通过结构校验并保留旧记录`() {
        createVersion4Database()

        val room = Room.databaseBuilder(context, AppDatabase::class.java, DB_NAME)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
            )
            .allowMainThreadQueries()
            .build()

        try {
            // 访问 writableDatabase 会触发 onUpgrade → 执行 4→5 → onValidateSchema。
            val database = room.openHelper.writableDatabase
            assertEquals(5, database.version)

            // 迁移产物必须与 SavedCaseEntity / 5.json 一致：favorite 为 NOT NULL 且无 DEFAULT。
            // 修复前 MIGRATION_4_5_SQL 带 DEFAULT 0，这里会读到 "0" 并失败。
            database.query("PRAGMA table_info(saved_cases)").use { cursor ->
                var favoriteDefault: String? = null
                while (cursor.moveToNext()) {
                    if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "favorite") {
                        favoriteDefault = if (cursor.isNull(cursor.getColumnIndexOrThrow("dflt_value"))) {
                            null
                        } else {
                            cursor.getString(cursor.getColumnIndexOrThrow("dflt_value"))
                        }
                        assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("notnull")))
                    }
                }
                assertEquals(null, favoriteDefault)
            }

            database.query("SELECT caseTitle, totalScore FROM consultations").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals("旧记录", cursor.getString(0))
                assertEquals(82, cursor.getInt(1))
                assertEquals(1, cursor.count)
            }

            database.execSQL(
                "INSERT INTO saved_cases (id, caseJson, createdAt, favorite) VALUES ('ai-1', '{}', 3, 0)",
            )
            database.query("SELECT favorite FROM saved_cases WHERE id = 'ai-1'").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        } finally {
            room.close()
        }
    }

    private fun createVersion4Database() {
        val dbFile = context.getDatabasePath(DB_NAME)
        dbFile.parentFile?.mkdirs()
        val db = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
        try {
            db.execSQL(V4_CONSULTATIONS_SQL)
            db.execSQL(AppDatabase.MIGRATION_3_4_SQL)
            db.execSQL(
                "INSERT INTO consultations (caseTitle, department, difficulty, patientName, caseJson, " +
                    "conversationJson, medicalRecord, scoreJson, totalScore, initialDiagnosis, diagnosisBasis, " +
                    "createdAt) VALUES ('旧记录','心内科','中等','王建国','{}','[]','病历','{}',82,'STEMI','心电图',123)",
            )
            db.version = 4
        } finally {
            db.close()
        }
    }

    private companion object {
        const val DB_NAME = "migration-room-upgrade-test.db"

        /** 与 5.json 中 consultations 的 createSql 完全一致（v4 时 consultations 已是该结构）。 */
        const val V4_CONSULTATIONS_SQL =
            "CREATE TABLE IF NOT EXISTS `consultations` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`caseTitle` TEXT NOT NULL, `department` TEXT NOT NULL, `difficulty` TEXT NOT NULL, " +
                "`patientName` TEXT NOT NULL, `caseJson` TEXT NOT NULL, `conversationJson` TEXT NOT NULL, " +
                "`medicalRecord` TEXT NOT NULL, `scoreJson` TEXT NOT NULL, `totalScore` INTEGER NOT NULL, " +
                "`initialDiagnosis` TEXT NOT NULL, `diagnosisBasis` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, " +
                "`patientPersonality` TEXT NOT NULL DEFAULT 'COOPERATIVE', " +
                "`encounterType` TEXT NOT NULL DEFAULT 'OUTPATIENT', " +
                "`treatmentAnswer` TEXT NOT NULL DEFAULT '', " +
                "`recordType` TEXT NOT NULL DEFAULT 'INPATIENT')"
    }
}
