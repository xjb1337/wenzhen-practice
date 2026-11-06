package com.xjb.wenzhen.data.db

import kotlinx.coroutines.flow.Flow

class ConsultationRepository(private val dao: ConsultationDao) {
    fun observeAll(): Flow<List<ConsultationEntity>> = dao.observeAll()

    suspend fun getById(id: Long): ConsultationEntity? = dao.getById(id)

    suspend fun findCompletedAfter(caseJson: String, since: Long): ConsultationEntity? =
        dao.findCompletedAfter(caseJson, since)

    suspend fun save(entity: ConsultationEntity): Long = dao.insert(entity)

    /** 事务式恢复历史：重复记录（caseJson + createdAt 相同）会跳过。 */
    suspend fun restoreAll(records: List<ConsultationEntity>): Int = dao.restoreAll(records)

    suspend fun updateScore(id: Long, scoreJson: String, totalScore: Int): Int =
        dao.updateScore(id, scoreJson, totalScore)

    suspend fun delete(id: Long) = dao.deleteById(id)

    suspend fun clear() = dao.deleteAll()
}
