package com.xjb.wenzhen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
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

    @Query(
        "SELECT COUNT(*) FROM consultations WHERE caseJson = :caseJson AND createdAt = :createdAt",
    )
    suspend fun countBySignature(caseJson: String, createdAt: Long): Int

    /**
     * 在同一事务里恢复历史备份：按 caseJson + createdAt 去重，任何一条失败都会整体回滚。
     * @return 实际写入的条数
     */
    @Transaction
    suspend fun restoreAll(records: List<ConsultationEntity>): Int {
        var inserted = 0
        records.forEach { record ->
            if (countBySignature(record.caseJson, record.createdAt) == 0) {
                insert(record.copy(id = 0L))
                inserted++
            }
        }
        return inserted
    }

    /** 重新生成 AI 点评后，只更新同一条记录的评分 JSON 和总分；返回受影响的行数。 */
    @Query("UPDATE consultations SET scoreJson = :scoreJson, totalScore = :totalScore WHERE id = :id")
    suspend fun updateScore(id: Long, scoreJson: String, totalScore: Int): Int

    @Query("DELETE FROM consultations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM consultations")
    suspend fun deleteAll()
}
