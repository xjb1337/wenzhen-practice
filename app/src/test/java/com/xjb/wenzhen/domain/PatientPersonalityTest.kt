package com.xjb.wenzhen.domain

import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.PatientPersonality
import org.junit.Assert.assertTrue
import org.junit.Test

class PatientPersonalityTest {
    private val promptBuilder = PromptBuilder()
    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!

    @Test
    fun `每种性格都会写入病人提示词`() {
        PatientPersonality.entries.forEach { personality ->
            val prompt = promptBuilder.buildPatientPrompt(medicalCase, personality)
            assertTrue(prompt.contains(personality.label))
            assertTrue(prompt.contains(personality.promptInstruction))
        }
    }

    @Test
    fun `隐瞒型要求追问后才逐步透露敏感信息`() {
        val prompt = promptBuilder.buildPatientPrompt(medicalCase, PatientPersonality.EVASIVE)
        assertTrue(prompt.contains("有所隐瞒"))
        assertTrue(prompt.contains("具体、温和地追问"))
    }

    @Test
    fun `老年人性格包含听力和啰嗦设定`() {
        val prompt = promptBuilder.buildPatientPrompt(medicalCase, PatientPersonality.ELDERLY)
        assertTrue(prompt.contains("听力较差"))
        assertTrue(prompt.contains("家常事"))
    }
}
