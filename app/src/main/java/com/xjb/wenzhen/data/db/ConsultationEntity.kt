package com.xjb.wenzhen.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

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
    @ColumnInfo(defaultValue = "'COOPERATIVE'")
    val patientPersonality: String = "COOPERATIVE",
    @ColumnInfo(defaultValue = "'OUTPATIENT'")
    val encounterType: String = "OUTPATIENT",
    @ColumnInfo(defaultValue = "''")
    val treatmentAnswer: String = "",
    @ColumnInfo(defaultValue = "'INPATIENT'")
    val recordType: String = "INPATIENT",
)
