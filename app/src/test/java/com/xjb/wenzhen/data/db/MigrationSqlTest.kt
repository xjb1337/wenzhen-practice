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
    fun `迁移版本连续注册到数据库版本三`() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
        assertEquals(2, AppDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, AppDatabase.MIGRATION_2_3.endVersion)
    }
}
