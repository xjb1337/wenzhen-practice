package com.xjb.wenzhen.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RoomDaoTest {
    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun consultation(title: String, createdAt: Long = 1L) = ConsultationEntity(
        caseTitle = title,
        department = "心内科",
        difficulty = "中等",
        patientName = "测试患者",
        caseJson = "{}",
        conversationJson = "[]",
        medicalRecord = "记录",
        scoreJson = "{}",
        totalScore = 80,
        initialDiagnosis = "诊断",
        diagnosisBasis = "依据",
        createdAt = createdAt,
    )

    @Test
    fun `草稿按单例主键覆盖保存并可删除`() = runBlocking {
        val dao = db.consultationDraftDao()
        dao.upsert(ConsultationDraftEntity(caseJson = "{\"v\":1}", conversationJson = "[]", patientPersonality = "COOPERATIVE", encounterType = "OUTPATIENT", startedAt = 10, updatedAt = 10))
        dao.upsert(ConsultationDraftEntity(caseJson = "{\"v\":2}", conversationJson = "[1]", patientPersonality = "ANXIOUS", encounterType = "INPATIENT", startedAt = 10, updatedAt = 20))

        val saved = dao.get()
        assertEquals("{\"v\":2}", saved?.caseJson)
        assertEquals("ANXIOUS", saved?.patientPersonality)

        dao.delete()
        assertNull(dao.get())
    }

    @Test
    fun `收藏病例排在前面且可切换收藏`() = runBlocking {
        val dao = db.savedCaseDao()
        dao.upsert(SavedCaseEntity(id = "old", caseJson = "{}", createdAt = 100, favorite = false))
        dao.upsert(SavedCaseEntity(id = "new", caseJson = "{}", createdAt = 200, favorite = false))
        dao.upsert(SavedCaseEntity(id = "fav", caseJson = "{}", createdAt = 50, favorite = true))

        val initial = dao.observeAll().first().map { it.id }
        assertEquals(listOf("fav", "new", "old"), initial)

        dao.setFavorite("old", true)
        val updated = dao.observeAll().first()
        assertEquals("old", updated.first().id)
        assertTrue(updated.first().favorite)
    }

    @Test
    fun `历史记录可删除单条与清空`() = runBlocking {
        val dao = db.consultationDao()
        val firstId = dao.insert(consultation("第一条", createdAt = 100))
        dao.insert(consultation("第二条", createdAt = 200))

        dao.deleteById(firstId)
        val remaining = dao.observeAll().first()
        assertEquals(listOf("第二条"), remaining.map { it.caseTitle })

        dao.deleteAll()
        assertTrue(dao.observeAll().first().isEmpty())
    }
}
