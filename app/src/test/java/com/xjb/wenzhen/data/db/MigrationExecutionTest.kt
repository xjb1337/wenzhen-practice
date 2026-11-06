package com.xjb.wenzhen.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MigrationExecutionTest {
    private lateinit var file: File
    private lateinit var db: SQLiteDatabase

    @Before
    fun setUp() {
        file = File.createTempFile("wenzhen-migration", ".db")
        db = SQLiteDatabase.openOrCreateDatabase(file, null)
        db.execSQL(
            "CREATE TABLE consultations (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, caseTitle TEXT NOT NULL, department TEXT NOT NULL, difficulty TEXT NOT NULL, patientName TEXT NOT NULL, caseJson TEXT NOT NULL, conversationJson TEXT NOT NULL, medicalRecord TEXT NOT NULL, scoreJson TEXT NOT NULL, totalScore INTEGER NOT NULL, initialDiagnosis TEXT NOT NULL, diagnosisBasis TEXT NOT NULL, createdAt INTEGER NOT NULL, patientPersonality TEXT NOT NULL DEFAULT 'COOPERATIVE', encounterType TEXT NOT NULL DEFAULT 'OUTPATIENT', treatmentAnswer TEXT NOT NULL DEFAULT '', recordType TEXT NOT NULL DEFAULT 'INPATIENT')",
        )
        db.execSQL(
            "INSERT INTO consultations (caseTitle, department, difficulty, patientName, caseJson, conversationJson, medicalRecord, scoreJson, totalScore, initialDiagnosis, diagnosisBasis, createdAt) VALUES ('旧记录','心内科','中等','王建国','{}','[]','病历','{}',82,'STEMI','心电图',123)",
        )
    }

    @After
    fun tearDown() {
        db.close()
        file.delete()
    }

    @Test
    fun `从版本3升级到版本5保留旧记录并创建新表`() {
        db.execSQL(AppDatabase.MIGRATION_3_4_SQL)
        db.execSQL(AppDatabase.MIGRATION_4_5_SQL)

        db.rawQuery("SELECT caseTitle, totalScore FROM consultations", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧记录", cursor.getString(0))
            assertEquals(82, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }

        val draft = ContentValues().apply {
            put("id", 1)
            put("caseJson", "{}")
            put("conversationJson", "[]")
            put("patientPersonality", "COOPERATIVE")
            put("encounterType", "OUTPATIENT")
            put("startedAt", 1L)
            put("updatedAt", 2L)
        }
        assertTrue(db.insert("consultation_drafts", null, draft) > 0)

        val caseValues = ContentValues().apply {
            put("id", "ai-1")
            put("caseJson", "{}")
            put("createdAt", 3L)
            // favorite 在数据库层没有 DEFAULT，Room 写入时总会显式提供实体默认值 false。
            put("favorite", 0)
        }
        assertTrue(db.insert("saved_cases", null, caseValues) > 0)

        db.rawQuery("SELECT favorite FROM saved_cases WHERE id = 'ai-1'", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test
    fun `从版本5升级到版本6创建createdAt索引并保留旧记录`() {
        db.execSQL(AppDatabase.MIGRATION_5_6_SQL)

        db.rawQuery("SELECT caseTitle, totalScore FROM consultations", null).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("旧记录", cursor.getString(0))
            assertEquals(82, cursor.getInt(1))
            assertEquals(1, cursor.count)
        }

        var indexFound = false
        db.rawQuery("PRAGMA index_list(consultations)", null).use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (name == "index_consultations_createdAt") indexFound = true
            }
        }
        assertTrue("缺少 index_consultations_createdAt 索引", indexFound)
    }
}
