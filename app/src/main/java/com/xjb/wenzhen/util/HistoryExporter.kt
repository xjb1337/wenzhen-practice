package com.xjb.wenzhen.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.xjb.wenzhen.data.db.ConsultationEntity
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 历史记录导出：Markdown / JSON 备份 / PDF。备份 JSON 可再次导入恢复。
 */
object HistoryExporter {
    private const val FORMAT_VERSION = 1

    /** 导入备份的大小上限，避免把超大文件整段读进内存。 */
    const val MAX_IMPORT_BYTES = 5 * 1024 * 1024

    /** 单次导入的记录条数上限。 */
    const val MAX_IMPORT_RECORDS = 5_000

    fun toMarkdown(records: List<ConsultationEntity>): String {
        val header = "# 问诊历史记录\n\n共 ${records.size} 条，导出时间：${formatTime(System.currentTimeMillis())}\n"
        val body = records.joinToString("\n---\n") { record ->
            buildString {
                appendLine("## ${record.caseTitle}")
                appendLine("- 科室：${record.department}")
                appendLine("- 患者：${record.patientName}")
                appendLine("- 难度：${record.difficulty}")
                appendLine("- 时间：${formatTime(record.createdAt)}")
                appendLine("- 得分：${record.totalScore}")
                appendLine("- 初步诊断：${record.initialDiagnosis}")
                if (record.recordType != "NONE" && record.medicalRecord.isNotBlank()) {
                    appendLine()
                    appendLine("### 病历")
                    appendLine(record.medicalRecord)
                }
            }
        }
        return header + "\n" + body
    }

    fun toJson(records: List<ConsultationEntity>): String = JSONObject().apply {
        put("formatVersion", FORMAT_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("records", JSONArray().apply {
            records.forEach { record ->
                put(JSONObject().apply {
                    put("caseTitle", record.caseTitle)
                    put("department", record.department)
                    put("difficulty", record.difficulty)
                    put("patientName", record.patientName)
                    put("caseJson", record.caseJson)
                    put("conversationJson", record.conversationJson)
                    put("medicalRecord", record.medicalRecord)
                    put("scoreJson", record.scoreJson)
                    put("totalScore", record.totalScore)
                    put("initialDiagnosis", record.initialDiagnosis)
                    put("diagnosisBasis", record.diagnosisBasis)
                    put("createdAt", record.createdAt)
                    put("patientPersonality", record.patientPersonality)
                    put("encounterType", record.encounterType)
                    put("treatmentAnswer", record.treatmentAnswer)
                    put("recordType", record.recordType)
                })
            }
        })
    }.toString()

    fun parseBackup(raw: String): List<ConsultationEntity> {
        val root = raw.trim().let { text ->
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start >= 0 && end > start) JSONObject(text.substring(start, end + 1)) else JSONObject(text)
        }
        val array = root.optJSONArray("records") ?: JSONArray()
        require(array.length() <= MAX_IMPORT_RECORDS) { "备份记录超过 $MAX_IMPORT_RECORDS 条，请拆分后再导入" }
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val title = item.optString("caseTitle").trim()
                if (title.isEmpty()) continue
                add(
                    ConsultationEntity(
                        id = 0L,
                        caseTitle = title,
                        department = item.optString("department"),
                        difficulty = item.optString("difficulty"),
                        patientName = item.optString("patientName"),
                        caseJson = item.optString("caseJson"),
                        conversationJson = item.optString("conversationJson"),
                        medicalRecord = item.optString("medicalRecord"),
                        scoreJson = item.optString("scoreJson"),
                        totalScore = item.optInt("totalScore"),
                        initialDiagnosis = item.optString("initialDiagnosis"),
                        diagnosisBasis = item.optString("diagnosisBasis"),
                        createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                        patientPersonality = item.optString("patientPersonality", "COOPERATIVE"),
                        encounterType = item.optString("encounterType", "OUTPATIENT"),
                        treatmentAnswer = item.optString("treatmentAnswer"),
                        recordType = item.optString("recordType", "INPATIENT"),
                    )
                )
            }
        }
    }

    /**
     * 从 content Uri 读取并解析备份。读取在调用方指定的 IO 线程执行，
     * 超过 [MAX_IMPORT_BYTES] 时立即失败，不会把整个文件读入内存。
     */
    fun readBackup(context: Context, uri: Uri): List<ConsultationEntity> {
        val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_IMPORT_BYTES) { "备份文件超过 5 MB，无法导入" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("无法读取文件")
        return parseBackup(String(bytes, Charsets.UTF_8))
    }

    fun writeJson(context: Context, records: List<ConsultationEntity>): File =
        File(exportDir(context), "wenzhen-history-${timestamp()}.json").apply {
            writeText(toJson(records), Charsets.UTF_8)
        }

    fun writeMarkdown(context: Context, records: List<ConsultationEntity>): File =
        File(exportDir(context), "wenzhen-history-${timestamp()}.md").apply {
            writeText(toMarkdown(records), Charsets.UTF_8)
        }

    fun writePdf(context: Context, records: List<ConsultationEntity>): File {
        val document = PdfDocument()
        val paint = Paint().apply {
            textSize = 11f
            isAntiAlias = true
        }
        val titlePaint = Paint().apply {
            textSize = 16f
            isFakeBoldText = true
            isAntiAlias = true
        }
        val lineHeight = 17f
        val margin = 36f
        val pageWidth = 595
        val pageHeight = 842
        val contentWidth = pageWidth - margin * 2
        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        )
        var canvas = page.canvas
        var y = margin + 12f

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            canvas = page.canvas
            y = margin + 12f
        }

        fun drawLine(text: String, title: Boolean = false) {
            val current = if (title) titlePaint else paint
            val wrapped = wrap(text, current, contentWidth)
            wrapped.forEach { line ->
                if (y > pageHeight - margin) newPage()
                canvas.drawText(line, margin, y, current)
                y += if (title) lineHeight + 4f else lineHeight
            }
        }

        drawLine("问诊历史记录（${records.size} 条）", title = true)
        drawLine("导出时间：${formatTime(System.currentTimeMillis())}")
        y += 8f
        records.forEachIndexed { index, record ->
            drawLine("${index + 1}. ${record.caseTitle}", title = true)
            drawLine("科室：${record.department}　患者：${record.patientName}　得分：${record.totalScore}")
            drawLine("时间：${formatTime(record.createdAt)}　初步诊断：${record.initialDiagnosis}")
            if (record.recordType != "NONE" && record.medicalRecord.isNotBlank()) {
                record.medicalRecord.lineSequence().forEach { drawLine(it) }
            }
            y += 10f
        }
        document.finishPage(page)
        val file = File(exportDir(context), "wenzhen-history-${timestamp()}.pdf")
        file.outputStream().use { document.writeTo(it) }
        document.close()
        return file
    }

    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val result = mutableListOf<String>()
        val builder = StringBuilder()
        text.forEach { char ->
            builder.append(char)
            if (paint.measureText(builder.toString()) > maxWidth) {
                builder.deleteCharAt(builder.length - 1)
                result.add(builder.toString())
                builder.clear().append(char)
            }
        }
        if (builder.isNotEmpty()) result.add(builder.toString())
        return result
    }

    private fun exportDir(context: Context): File =
        File(context.cacheDir, "exports").apply { mkdirs() }

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.getDefault()).format(Date())

    private fun formatTime(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
