package com.xjb.wenzhen.domain

class RecordParseException(message: String, cause: Throwable? = null) : Exception(message, cause)

object RecordParser {
    private enum class Section(val title: String, val aliases: List<String>) {
        GENERAL("一般情况", listOf("一般情况", "一般项目")),
        CHIEF("主诉", listOf("主诉")),
        PRESENT("现病史", listOf("现病史")),
        PAST("既往史", listOf("既往史")),
        PERSONAL("个人史", listOf("个人史")),
        MARITAL("婚育史", listOf("婚育史", "婚姻史")),
        FAMILY("家族史", listOf("家族史")),
        PHYSICAL("体格检查", listOf("体格检查", "查体")),
        REVIEW("系统回顾", listOf("系统回顾", "系统询问")),
        DIAGNOSIS("初步诊断", listOf("初步诊断", "诊断")),
    }

    private const val MISSING = "未问及"

    fun parse(raw: String, fallbackDiagnosis: String = ""): String {
        val cleaned = raw.lineSequence()
            .filterNot { it.trim().startsWith("```") }
            .joinToString("\n")
            .trim()
        if (cleaned.isBlank()) {
            throw RecordParseException("AI 未返回病历内容，请重试。")
        }

        val contents = linkedMapOf<Section, StringBuilder>()
        var current: Section? = null
        var foundHeading = false

        cleaned.lineSequence().forEach { line ->
            val heading = matchHeading(line)
            if (heading != null) {
                foundHeading = true
                current = heading.first
                contents.getOrPut(heading.first) { StringBuilder() }
                heading.second?.trim()?.takeIf { it.isNotEmpty() }?.let {
                    contents.getValue(heading.first).append(it).append('\n')
                }
            } else {
                val section = current
                if (section != null) {
                    contents.getOrPut(section) { StringBuilder() }.append(line.trimEnd()).append('\n')
                }
            }
        }

        if (!foundHeading) {
            throw RecordParseException("病历格式不完整，未识别到规定的小节标题，请重试。")
        }

        contents.forEach { (_, value) ->
            val trimmed = value.toString().trim()
            value.clear()
            value.append(trimmed)
        }

        val normalized = Section.values().associateWith { section ->
            contents[section]?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: MISSING
        }.toMutableMap()
        if (normalized[Section.DIAGNOSIS] == MISSING && fallbackDiagnosis.isNotBlank()) {
            normalized[Section.DIAGNOSIS] = fallbackDiagnosis.trim()
        }

        return Section.values().joinToString(separator = "\n\n") { section ->
            "${section.title}\n${normalized.getValue(section)}"
        }
    }

    private fun matchHeading(line: String): Pair<Section, String?>? {
        Section.values().forEach { section ->
            section.aliases.forEach { alias ->
                val pattern = Regex(
                    "^\\s*(?:[-•]\\s*)?(?:#{1,6}\\s*)?(?:\\*\\*)?\\s*(?:\\d+[.、]\\s*)?${Regex.escape(alias)}(?:\\s*\\*\\*)?\\s*(?:[:：]\\s*(.*))?$"
                )
                val match = pattern.matchEntire(line)
                if (match != null) {
                    return section to match.groupValues.getOrNull(1)
                }
            }
        }
        return null
    }
}
