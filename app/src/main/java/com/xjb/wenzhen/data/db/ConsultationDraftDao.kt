package com.xjb.wenzhen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ConsultationDraftDao {
    @Query("SELECT * FROM consultation_drafts WHERE id = 1 LIMIT 1")
    suspend fun get(): ConsultationDraftEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: ConsultationDraftEntity)

    @Query("DELETE FROM consultation_drafts WHERE id = 1")
    suspend fun delete()
}

