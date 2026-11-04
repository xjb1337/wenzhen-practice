package com.xjb.wenzhen.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedCaseDao {
    @Query("SELECT * FROM saved_cases ORDER BY favorite DESC, createdAt DESC")
    fun observeAll(): Flow<List<SavedCaseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(caseEntity: SavedCaseEntity)

    @Query("DELETE FROM saved_cases WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE saved_cases SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)
}

