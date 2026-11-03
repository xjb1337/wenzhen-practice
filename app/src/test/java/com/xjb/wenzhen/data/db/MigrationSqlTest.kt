package com.xjb.wenzhen.data.db

import org.junit.Assert.assertEquals
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
    fun `数据库版本为二并注册增量迁移`() {
        assertEquals(1, AppDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, AppDatabase.MIGRATION_1_2.endVersion)
    }
}
