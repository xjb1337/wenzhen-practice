package com.xjb.wenzhen.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "consultations")
data class ConsultationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val caseTitle: String,
    val department: String,
    val difficulty: String,
    val patientName: String,
    val caseJson: String,
    val conversationJson: String,
    val medicalRecord: String,
    val scoreJson: String,
    val totalScore: Int,
    val initialDiagnosis: String,
    val diagnosisBasis: String,
    val createdAt: Long,
)
