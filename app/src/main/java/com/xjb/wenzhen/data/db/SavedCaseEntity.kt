package com.xjb.wenzhen.data.db

import androidx.room.Entity

@Entity(tableName = "saved_cases")
data class SavedCaseEntity(
    @androidx.room.PrimaryKey val id: String,
    /** 已保存的生成病例快照。内置病例按 ID 从病例库读取，收藏时不保存副本。 */
    val caseJson: String,
    val createdAt: Long,
    val favorite: Boolean = false,
)

