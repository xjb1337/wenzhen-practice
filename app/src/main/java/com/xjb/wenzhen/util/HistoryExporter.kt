package com.xjb.wenzhen.util

import android.content.Context
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.xjb.wenzhen.data.db.ConsultationEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 问诊历史的 Markdown 和 PDF 导出。 */
object HistoryExporter {
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

        val file = File(exportDir(context), "wenzhen-history-${timestamp()}.pdf")
        try {
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
            file.outputStream().use { document.writeTo(it) }
            return file
        } finally {
            document.close()
        }
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
        SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())

    private fun formatTime(value: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(value))
}
