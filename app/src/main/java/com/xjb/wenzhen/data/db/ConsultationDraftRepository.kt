package com.xjb.wenzhen.data.db

class ConsultationDraftRepository(private val dao: ConsultationDraftDao) {
    suspend fun get(): ConsultationDraftEntity? = dao.get()
    suspend fun save(draft: ConsultationDraftEntity) = dao.upsert(draft)
    suspend fun clear() = dao.delete()
}

