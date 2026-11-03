package com.xjb.wenzhen.data.db

import kotlinx.coroutines.flow.Flow

class ConsultationRepository(private val dao: ConsultationDao) {
    fun observeAll(): Flow<List<ConsultationEntity>> = dao.observeAll()

    suspend fun getById(id: Long): ConsultationEntity? = dao.getById(id)

    suspend fun save(entity: ConsultationEntity): Long = dao.insert(entity)
}
