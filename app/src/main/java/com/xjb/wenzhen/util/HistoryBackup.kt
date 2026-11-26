package com.xjb.wenzhen.util

import android.content.Context
import android.net.Uri
import android.util.JsonReader
import android.util.JsonToken
import android.util.JsonWriter
import com.xjb.wenzhen.data.db.ConsultationEntity
import java.io.File
import java.io.FilterReader
import java.io.Reader
import java.io.Writer
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONObject

/** 原格式的 JSON 备份，按记录读写。导入方在同一事务内消费整个流。 */
object HistoryBackup {
    private const val FORMAT_VERSION = 1
    private val filenameFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)

    fun read(context: Context, uri: Uri): Flow<ConsultationEntity> =
        readRecords {
            context.contentResolver.openInputStream(uri)?.reader(Charsets.UTF_8)
                ?: error("无法打开备份文件")
        }

    internal fun readRecords(open: () -> Reader): Flow<ConsultationEntity> = flow {
        val source = RecordReader(open())
        val importedAt = System.currentTimeMillis()
        var count = 0
        JsonReader(source).use { json ->
            json.beginObject()
            var foundRecords = false
            while (json.hasNext()) {
                when (json.nextName()) {
                    "formatVersion" -> require(json.nextInt() == FORMAT_VERSION) { "不支持此备份版本" }
                    "records" -> {
                        require(!foundRecords) { "备份包含重复的记录列表" }
                        foundRecords = true
                        json.beginArray()
                        while (json.hasNext()) {
                            val record = readRecord(json, ++count, importedAt)
                            source.beginRecord()
                            emit(record)
                        }
                        json.endArray()
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
            require(json.peek() == JsonToken.END_DOCUMENT) { "备份结束后仍有多余内容" }
            require(count > 0) { "备份中没有问诊记录" }
        }
    }.buffer(0).flowOn(Dispatchers.IO)

    private fun readRecord(json: JsonReader, index: Int, importedAt: Long): ConsultationEntity {
        val fields = JSONObject()
        json.beginObject()
        while (json.hasNext()) {
            val name = json.nextName()
            when (json.peek()) {
                JsonToken.STRING, JsonToken.NUMBER -> fields.put(name, json.nextString())
                else -> json.skipValue()
            }
        }
        json.endObject()
        val title = fields.optString("caseTitle")
        require(title.isNotBlank()) { "备份第 $index 条记录缺少病例标题" }
        return ConsultationEntity(
            caseTitle = title,
            department = fields.optString("department"),
            difficulty = fields.optString("difficulty"),
            patientName = fields.optString("patientName"),
            caseJson = fields.optString("caseJson"),
            conversationJson = fields.optString("conversationJson"),
            medicalRecord = fields.optString("medicalRecord"),
            scoreJson = fields.optString("scoreJson"),
            totalScore = fields.optInt("totalScore"),
            initialDiagnosis = fields.optString("initialDiagnosis"),
            diagnosisBasis = fields.optString("diagnosisBasis"),
            createdAt = fields.optString("createdAt").toLongOrNull() ?: fields.optLong("createdAt", importedAt),
            patientPersonality = fields.optString("patientPersonality", "COOPERATIVE"),
            encounterType = fields.optString("encounterType", "OUTPATIENT"),
            treatmentAnswer = fields.optString("treatmentAnswer"),
            recordType = fields.optString("recordType", "INPATIENT"),
        )
    }

    fun suggestedFilename(): String = "wenzhen-history-${LocalDateTime.now().format(filenameFormat)}.json"

    suspend fun write(context: Context, uri: Uri, records: List<ConsultationEntity>) {
        val output = context.contentResolver.openOutputStream(uri, "wt") ?: error("无法写入所选文件")
        writeRecords(output.bufferedWriter(Charsets.UTF_8), records)
    }

    suspend fun write(context: Context, records: List<ConsultationEntity>): File {
        val directory = File(context.cacheDir, "exports").apply { mkdirs() }
        val prefix = suggestedFilename().removeSuffix(".json")
        val file = File.createTempFile("$prefix-", ".json", directory)
        var written = false
        try {
            writeRecords(file.bufferedWriter(Charsets.UTF_8), records)
            written = true
            return file
        } finally {
            if (!written) file.delete()
        }
    }

    internal suspend fun writeRecords(writer: Writer, records: List<ConsultationEntity>) {
        JsonWriter(writer).use { json ->
            json.beginObject()
            json.name("formatVersion").value(FORMAT_VERSION.toLong())
            json.name("exportedAt").value(System.currentTimeMillis())
            json.name("records").beginArray()
            for (record in records) {
                currentCoroutineContext().ensureActive()
                json.beginObject()
                json.name("caseTitle").value(record.caseTitle)
                json.name("department").value(record.department)
                json.name("difficulty").value(record.difficulty)
                json.name("patientName").value(record.patientName)
                json.name("caseJson").value(record.caseJson)
                json.name("conversationJson").value(record.conversationJson)
                json.name("medicalRecord").value(record.medicalRecord)
                json.name("scoreJson").value(record.scoreJson)
                json.name("totalScore").value(record.totalScore.toLong())
                json.name("initialDiagnosis").value(record.initialDiagnosis)
                json.name("diagnosisBasis").value(record.diagnosisBasis)
                json.name("createdAt").value(record.createdAt)
                json.name("patientPersonality").value(record.patientPersonality)
                json.name("encounterType").value(record.encounterType)
                json.name("treatmentAnswer").value(record.treatmentAnswer)
                json.name("recordType").value(record.recordType)
                json.endObject()
            }
            json.endArray()
            json.endObject()
        }
    }

    /**
     * 限制两次完成读取记录之间的字符数，防止单个 JSON 值把内存耗尽。留出 JSON 转义空间，
     * 此上限远高于 Android 的单行数据库读取容量，且与备份总大小和记录总数无关。
     */
    private class RecordReader(reader: Reader) : FilterReader(reader) {
        private var remaining = MAX_RECORD_CHARS

        fun beginRecord() {
            remaining = MAX_RECORD_CHARS
        }

        override fun read(buffer: CharArray, offset: Int, length: Int): Int {
            if (length == 0) return 0
            require(remaining > 0) { "备份中的单条记录过大" }
            val count = super.read(buffer, offset, minOf(length, remaining))
            if (count > 0) remaining -= count
            return count
        }

        override fun read(): Int {
            require(remaining > 0) { "备份中的单条记录过大" }
            val value = super.read()
            if (value != -1) remaining--
            return value
        }

        private companion object {
            const val MAX_RECORD_CHARS = 16 * 1024 * 1024
        }
    }
}
