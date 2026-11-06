package com.xjb.wenzhen.data.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MigrationSqlTest {
    @Test
    fun `v1到v2迁移增加带默认值的性格列且不破坏旧数据`() {
        val sql = AppDatabase.MIGRATION_1_2_SQL

        assertTrue(sql.startsWith("ALTER TABLE consultations ADD COLUMN"))
        assertTrue(sql.contains("patientPersonality"))
        assertTrue(sql.contains("NOT NULL"))
        assertTrue(sql.contains("DEFAULT 'COOPERATIVE'"))
    }

    @Test
    fun `v2到v3迁移增加就诊类型治疗答案和病历类型且保留旧数据`() {
        val statements = listOf(
            AppDatabase.MIGRATION_2_3_SQL,
            AppDatabase.MIGRATION_2_3_SQL_TREATMENT,
            AppDatabase.MIGRATION_2_3_SQL_RECORD,
        )

        statements.forEach { sql ->
            assertTrue(sql.startsWith("ALTER TABLE consultations ADD COLUMN"))
            assertTrue(sql.contains("NOT NULL"))
            assertTrue(sql.contains("DEFAULT"))
            assertFalse(sql.contains("DROP TABLE", ignoreCase = true))
            assertFalse(sql.contains("DELETE FROM", ignoreCase = true))
        }
        assertTrue(AppDatabase.MIGRATION_2_3_SQL.contains("encounterType"))
        assertTrue(AppDatabase.MIGRATION_2_3_SQL.contains("DEFAULT 'OUTPATIENT'"))
        assertTrue(AppDatabase.MIGRATION_2_3_SQL_TREATMENT.contains("treatmentAnswer"))
        assertTrue(AppDatabase.MIGRATION_2_3_SQL_TREATMENT.contains("DEFAULT ''"))
        assertTrue(AppDatabase.MIGRATION_2_3_SQL_RECORD.contains("recordType"))
        assertTrue(AppDatabase.MIGRATION_2_3_SQL_RECORD.contains("DEFAULT 'INPATIENT'"))
    }

    @Test
    fun `迁移版本连续注册到数据库版本六`() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
        assertEquals(3, AppDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, AppDatabase.MIGRATION_3_4.endVersion)
        assertEquals(4, AppDatabase.MIGRATION_4_5.startVersion)
        assertEquals(5, AppDatabase.MIGRATION_4_5.endVersion)
        assertEquals(5, AppDatabase.MIGRATION_5_6.startVersion)
        assertEquals(6, AppDatabase.MIGRATION_5_6.endVersion)
    }

    @Test
    fun `v3到v4创建草稿表且含恢复所需字段`() {
        val sql = AppDatabase.MIGRATION_3_4_SQL

        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS consultation_drafts"))
        listOf("caseJson", "conversationJson", "patientPersonality", "encounterType", "startedAt", "updatedAt").forEach {
            assertTrue("缺少字段 $it", sql.contains(it))
        }
        assertTrue(sql.contains("PRIMARY KEY(id)"))
    }

    @Test
    fun `v4到v5创建病例库表且结构与实体和导出schema一致`() {
        val sql = AppDatabase.MIGRATION_4_5_SQL

        assertTrue(sql.startsWith("CREATE TABLE IF NOT EXISTS saved_cases"))
        listOf("id", "caseJson", "createdAt", "favorite").forEach {
            assertTrue("缺少字段 $it", sql.contains(it))
        }
        // 回归：SavedCaseEntity 的 favorite 没有 @ColumnInfo(defaultValue)，
        // 迁移 SQL 若带 DEFAULT 0，Room 在 onUpgrade 的 onValidateSchema 会判定结构不一致并崩溃。
        assertTrue(sql.contains("favorite INTEGER NOT NULL"))
        assertFalse("迁移 SQL 不得再携带 DEFAULT 0", sql.contains("DEFAULT"))
        assertTrue(sql.contains("PRIMARY KEY(id)"))
    }

    @Test
    fun `v5到v6为病例创建时间创建索引且不破坏数据`() {
        val sql = AppDatabase.MIGRATION_5_6_SQL

        assertEquals(
            "CREATE INDEX IF NOT EXISTS index_consultations_createdAt ON consultations(createdAt)",
            sql,
        )
        assertFalse(sql.contains("DROP TABLE", ignoreCase = true))
        assertFalse(sql.contains("DELETE FROM", ignoreCase = true))
    }
}
