package com.xjb.wenzhen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConsultationDao {
    @Query("SELECT * FROM consultations ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ConsultationEntity>>

    @Query("SELECT * FROM consultations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ConsultationEntity?

    @Insert
    suspend fun insert(entity: ConsultationEntity): Long
}
