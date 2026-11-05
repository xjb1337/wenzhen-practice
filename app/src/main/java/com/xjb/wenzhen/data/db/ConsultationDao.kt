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

    /**
     * 查找同一病例在某个时间点之后完成的记录。
     * 用于清理 v1.3.2 及更早版本遗留的「已完成问诊的草稿」：如果同一份 caseJson
     * 在草稿开始时间之后已经生成过结果，说明草稿是结果页退后台时被写回的残留。
     */
    @Query(
        "SELECT * FROM consultations WHERE caseJson = :caseJson AND createdAt >= :since " +
            "ORDER BY createdAt DESC LIMIT 1",
    )
    suspend fun findCompletedAfter(caseJson: String, since: Long): ConsultationEntity?

    @Insert
    suspend fun insert(entity: ConsultationEntity): Long

    @Query("DELETE FROM consultations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM consultations")
    suspend fun deleteAll()
}
