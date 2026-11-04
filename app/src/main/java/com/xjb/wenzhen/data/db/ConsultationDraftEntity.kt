package com.xjb.wenzhen.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consultation_drafts")
data class ConsultationDraftEntity(
    @PrimaryKey val id: Int = 1,
    val caseJson: String,
    val conversationJson: String,
    val patientPersonality: String,
    val encounterType: String,
    val startedAt: Long,
    val updatedAt: Long,
)

