package com.xjb.wenzhen.data.db

import kotlinx.coroutines.flow.Flow

class SavedCaseRepository(private val dao: SavedCaseDao) {
    fun observeAll(): Flow<List<SavedCaseEntity>> = dao.observeAll()
    suspend fun save(caseEntity: SavedCaseEntity) = dao.upsert(caseEntity)
    suspend fun delete(id: String) = dao.delete(id)
    suspend fun setFavorite(id: String, favorite: Boolean) = dao.setFavorite(id, favorite)
}

