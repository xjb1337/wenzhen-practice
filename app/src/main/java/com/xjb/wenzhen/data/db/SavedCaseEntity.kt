package com.xjb.wenzhen.data.db

import androidx.room.Entity

@Entity(tableName = "saved_cases")
data class SavedCaseEntity(
    @androidx.room.PrimaryKey val id: String,
    val caseJson: String,
    val createdAt: Long,
    val favorite: Boolean = false,
)

