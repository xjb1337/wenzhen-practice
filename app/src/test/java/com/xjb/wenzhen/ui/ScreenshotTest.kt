package com.xjb.wenzhen.ui

import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.db.ConsultationEntity
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.Departments
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.data.settings.AppSettings
import com.xjb.wenzhen.data.update.UpdateInfo
import com.xjb.wenzhen.domain.ScoreResult
import com.xjb.wenzhen.domain.StatsCalculator
import com.xjb.wenzhen.domain.StatsRecord
import com.xjb.wenzhen.ui.screens.CaseLibraryScreen
import com.xjb.wenzhen.ui.screens.ChatScreen
import com.xjb.wenzhen.ui.screens.FinishConsultationScreen
import com.xjb.wenzhen.ui.screens.HistoryScreen
import com.xjb.wenzhen.ui.screens.ResultScreen
import com.xjb.wenzhen.ui.screens.ResultTab
import com.xjb.wenzhen.ui.screens.SettingsScreen
import com.xjb.wenzhen.ui.screens.StatsScreen
import com.xjb.wenzhen.ui.screens.UpdateDialog
import com.xjb.wenzhen.ui.theme.WenzhenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ScreenshotTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val medicalCase = BuiltInCases.byId("cardio-chest-pain")!!
    private val score = ScoreResult(
        totalScore = 85,
        coverageScore = 34,
        techniqueScore = 14,
        communicationScore = 13,
        diagnosisScore = 16,
        treatmentScore = 8,
        coveredPoints = listOf("询问了胸痛起病时间和诱因", "询问了疼痛性质和放射部位", "询问了伴随症状"),
        missedQuestions = listOf("未系统询问饮酒史", "未询问既往用药依从性"),
        strengths = listOf("问诊语言自然，能及时追问", "重点围绕胸痛展开，条理清楚"),
        improvements = listOf("补充心血管危险因素和家族史", "确认药物过敏史后再讨论处理"),
        correctDiagnosis = "急性ST段抬高型心肌梗死（前壁）",
        diagnosisExplanation = "压榨性胸痛持续不缓解并伴大汗、左肩放射，是本例最重要的线索；结合ST段抬高可支持诊断。",
    )

    @Before
    fun setUp() {
        System.setProperty("roborazzi.test.record", "true")
        File("build/outputs/roborazzi").mkdirs()
    }

    @Test
    fun caseLibraryScreenshot() {
        setScreen {
            CaseLibraryScreen(
                cases = BuiltInCases.all,
                selectedDepartment = Departments.ALL,
                hasApiKey = true,
                builtInCount = BuiltInCases.all.size,
            )
        }
        capture("case_library.png")
    }

    @Test
    fun emptyHistoryScreenshot() {
        setScreen {
            HistoryScreen(
                records = emptyList(),
                isLoading = false,
                errorMessage = null,
            )
        }
        capture("history_empty.png")
    }

    @Test
    fun historyRecordScreenshot() {
        val record = historyRecord()
        var openedRecordId: Long? = null
        var deletedRecordId: Long? = null
        setScreen {
            HistoryScreen(
                records = listOf(record),
                isLoading = false,
                errorMessage = null,
                onRecordClick = { openedRecordId = it },
                onDeleteRecord = { deletedRecordId = it },
            )
        }

        composeRule.onNodeWithText("79分").assertExists()
        capture("history_record.png")

        composeRule.onNodeWithContentDescription("删除").performClick()
        composeRule.onNodeWithText("删除这条问诊记录？").assertExists()
        assertNull(openedRecordId)
        assertNull(deletedRecordId)
        captureDialog("history_delete_confirm.png")

        composeRule.onNodeWithText("删除").performClick()
        assertEquals(record.id, deletedRecordId)
        assertNull(openedRecordId)
    }

    @Test
    fun chatScreenshot() {
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, medicalCase.openingLine, 1_700_000_000_000),
            ChatMessage(MessageRole.DOCTOR, "您胸痛多久了？是突然开始的吗？", 1_700_000_010_000),
            ChatMessage(MessageRole.PATIENT, "大概三个小时了，搬东西时突然开始的，一直没缓过来。", 1_700_000_020_000),
            ChatMessage(MessageRole.DOCTOR, "疼痛会向左肩或者后背放射吗？有没有出汗？", 1_700_000_030_000),
            ChatMessage(MessageRole.PATIENT, "左肩有点酸，出了很多汗，人也有点恶心。", 1_700_000_040_000),
        )
        setScreen {
            ChatScreen(
                medicalCase = medicalCase,
                messages = messages,
                isPatientTyping = false,
                hasApiKey = true,
                errorMessage = null,
                onSendMessage = {},
                onFinish = {},
                onBack = {},
                onOpenSettings = {},
                onDismissError = {},
            )
        }
        capture("chat.png")
    }

    @Test
    fun chatCoverageHintScreenshot() {
        val startedAt = System.currentTimeMillis() - 125_000L
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, medicalCase.openingLine, 1_700_000_000_000),
            ChatMessage(MessageRole.DOCTOR, "您胸痛多久了？有没有放射痛、出汗或恶心？", 1_700_000_010_000),
            ChatMessage(MessageRole.PATIENT, "大概三个小时了，左肩有点酸，出了很多汗，也有点恶心。", 1_700_000_020_000),
        )
        setScreen {
            ChatScreen(
                medicalCase = medicalCase,
                messages = messages,
                isPatientTyping = false,
                hasApiKey = true,
                errorMessage = null,
                onSendMessage = {},
                onFinish = {},
                onBack = {},
                onOpenSettings = {},
                onDismissError = {},
                startedAtMillis = startedAt,
                coverageInitiallyExpanded = true,
            )
        }
        capture("chat_coverage_hint.png")
    }

    @Test
    fun chatClinicalResultScreenshot() {
        val startedAt = System.currentTimeMillis() - 65_000L
        val messages = listOf(
            ChatMessage(MessageRole.PATIENT, medicalCase.openingLine, 1_700_000_000_000),
            ChatMessage(MessageRole.DOCTOR, "我先给您做一下心肺听诊。", 1_700_000_010_000),
            ChatMessage(
                role = MessageRole.SYSTEM,
                content = "T 36.8℃，P 96次/分，BP 150/92mmHg。双肺呼吸音清，未闻及干湿啰音；心率96次/分，律齐，心音稍低，未闻及明显杂音。",
                timestamp = 1_700_000_020_000,
                systemTitle = "查体 · 心肺听诊",
            ),
            ChatMessage(MessageRole.PATIENT, "听诊的时候没什么特别感觉，就是胸口还是闷。", 1_700_000_030_000),
        )
        setScreen {
            ChatScreen(
                medicalCase = medicalCase,
                messages = messages,
                isPatientTyping = false,
                hasApiKey = true,
                errorMessage = null,
                onSendMessage = {},
                onFinish = {},
                onBack = {},
                onOpenSettings = {},
                onDismissError = {},
                startedAtMillis = startedAt,
                coverageInitiallyExpanded = true,
            )
        }
        capture("chat_clinical_result.png")
    }

    @Test
    fun settingsScreenshot() {
        setScreen {
            SettingsScreen(
                settings = AppSettings(
                    apiKey = "sk-demo-only-for-masked-display",
                    model = AppSettings.MODEL_PRO,
                    baseUrl = AppSettings.DEFAULT_BASE_URL,
                ),
                onSave = { _, _, _, _, _, _ -> },
                onBack = {},
            )
        }
        capture("settings.png")
    }

    @Test
    fun settingsGuideScreenshot() {
        setScreen {
            SettingsScreen(
                settings = AppSettings(),
                onSave = { _, _, _, _, _, _ -> },
                onBack = {},
                initialGuideExpanded = true,
            )
        }
        capture("settings_key_guide.png")
    }

    @Test
    fun settingsConnectionSuccessScreenshot() {
        setScreen {
            SettingsScreen(
                settings = AppSettings(
                    apiKey = "sk-demo-only-for-connection-test",
                    model = AppSettings.MODEL_FLASH,
                    baseUrl = AppSettings.DEFAULT_BASE_URL,
                ),
                onSave = { _, _, _, _, _, _ -> },
                onBack = {},
                connectionTest = ConnectionTestUiState(
                    message = "连接成功，API Key 可用。",
                    isSuccess = true,
                ),
            )
        }
        capture("settings_connection_success.png")
    }

    @Test
    fun statsScreenshot() {
        val stats = StatsCalculator.calculate(
            listOf(
                StatsRecord(1, "心内科", 72, 1_700_000_000_000, listOf("未询问家族史", "未询问吸烟史")),
                StatsRecord(2, "呼吸内科", 81, 1_700_100_000_000, listOf("未询问职业暴露")),
                StatsRecord(3, "心内科", 86, 1_700_200_000_000, listOf("未询问家族史")),
                StatsRecord(4, "消化内科", 91, 1_700_300_000_000, listOf("未询问饮酒史")),
            )
        )
        setScreen { StatsScreen(stats = stats) }
        capture("stats.png")
    }

    @Test
    fun updateDialogScreenshot() {
        setScreen {
            UpdateDialog(
                update = UpdateInfo(
                    versionName = "1.2.0",
                    versionCode = 12,
                    apkUrl = "https://github.com/xjb1337/wenzhen-practice/releases/download/v1.2.0/wenzhen-practice.apk",
                    sha256 = "a".repeat(64),
                    signingCertSha256 = "b".repeat(64),
                    notes = "新增成绩趋势与常遗漏问诊要点统计；优化问诊提示、查体结果和设置页体验。",
                    minSdk = 26,
                ),
                onDownload = {},
                onInstall = {},
                onDismiss = {},
            )
        }
        captureDialog("update_dialog.png")
    }

    @Test
    fun finishConsultationScreenshot() {
        setScreen {
            FinishConsultationScreen(
                caseTitle = medicalCase.title,
                patientName = medicalCase.patient.name,
                department = medicalCase.department,
                onBack = {},
                onSubmit = { _, _, _, _ -> },
                initialDiagnosis = "急性冠脉综合征",
                initialDiagnosisBasis = "持续胸痛伴大汗，向左肩放射，有高血压和吸烟史。",
            )
        }
        capture("finish_consultation.png")
    }

    @Test
    fun resultRecordScreenshot() {
        setScreen {
            SampleResultScreen(initialTab = ResultTab.RECORD)
        }
        capture("result_record.png")
    }

    @Test
    fun resultTranscriptScreenshot() {
        setScreen {
            SampleResultScreen(
                initialTab = ResultTab.TRANSCRIPT,
                messages = listOf(
                    ChatMessage(MessageRole.PATIENT, medicalCase.openingLine, 1_700_000_000_000),
                    ChatMessage(MessageRole.DOCTOR, "您胸痛多久了？有没有放射痛、出汗或恶心？", 1_700_000_010_000),
                    ChatMessage(MessageRole.PATIENT, "大概三个小时了，左肩有点酸，出了很多汗。", 1_700_000_020_000),
                    ChatMessage(MessageRole.SYSTEM, "心电图：V1-V4 导联 ST 段抬高。", 1_700_000_030_000, systemTitle = "辅助检查 · 心电图"),
                ),
            )
        }
        composeRule.onNodeWithText("辅助检查 · 心电图", substring = true).assertExists()
        composeRule.onNodeWithText("对话").assertExists()
        capture("result_transcript.png")
    }

    @Test
    fun resultReviewScreenshot() {
        setScreen {
            SampleResultScreen(initialTab = ResultTab.REVIEW)
        }
        capture("result_review.png")
    }

    @Test
    fun personalityPickerChildScreenshot() {
        val child = BuiltInCases.all.first { (it.patient.age.filter(Char::isDigit).toIntOrNull() ?: 99) < 14 }
        setScreen {
            com.xjb.wenzhen.ui.screens.PersonalityPickerDialog(medicalCase = child, onDismiss = {}, onConfirm = { _, _ -> })
        }
        captureDialog("personality_picker_child.png")
    }

    @Test
    fun personalityPickerAdultScreenshot() {
        setScreen {
            com.xjb.wenzhen.ui.screens.PersonalityPickerDialog(medicalCase = medicalCase, onDismiss = {}, onConfirm = { _, _ -> })
        }
        captureDialog("personality_picker_adult.png")
    }

    @Test
    fun settingsThinkingScreenshot() {
        setScreen {
            SettingsScreen(settings = AppSettings(apiKey = "sk-demo", thinkingLevel = com.xjb.wenzhen.data.model.ThinkingLevel.HIGH), onSave = { _, _, _, _, _, _ -> }, onBack = {})
        }
        composeRule.onRoot().performTouchInput { swipeUp() }
        composeRule.onRoot().performTouchInput { swipeUp() }
        capture("settings_thinking.png")
    }

    @Test
    fun finishWithTreatmentScreenshot() {
        setScreen {
            FinishConsultationScreen(
                caseTitle = medicalCase.title,
                patientName = medicalCase.patient.name,
                department = medicalCase.department,
                onBack = {},
                onSubmit = { _, _, _, _ -> },
                initialDiagnosis = "急性ST段抬高型心肌梗死",
                initialDiagnosisBasis = "持续胸痛伴大汗，向左肩放射。",
                initialTreatment = "阿司匹林+替格瑞洛负荷，他汀，尽快急诊PCI。",
                initialGenerateRecord = true,
            )
        }
        composeRule.onRoot().performTouchInput { swipeUp() }
        capture("finish_treatment_toggle.png")
    }

    @Test
    fun resultReviewWithoutRecordScreenshot() {
        setScreen {
            ResultScreen(
                caseTitle = medicalCase.title,
                patientName = medicalCase.patient.name,
                department = medicalCase.department,
                difficulty = medicalCase.difficulty.label,
                createdAt = 1_700_000_100_000,
                medicalRecord = "",
                score = score,
                initialDiagnosis = "急性心肌梗死",
                onBack = {},
            )
        }
        capture("result_no_record.png")
    }

    @Composable
    private fun SampleResultScreen(initialTab: ResultTab, messages: List<ChatMessage> = emptyList()) {
        ResultScreen(
            caseTitle = medicalCase.title,
            patientName = medicalCase.patient.name,
            department = medicalCase.department,
            difficulty = medicalCase.difficulty.label,
            createdAt = 1_700_000_100_000,
            medicalRecord = sampleRecord(),
            score = score,
            initialDiagnosis = "急性心肌梗死",
            onBack = {},
            initialTab = initialTab,
            messages = messages,
        )
    }

    private fun setScreen(content: @Composable () -> Unit) {
        composeRule.setContent {
            WenzhenTheme(darkTheme = false) {
                content()
            }
        }
        composeRule.waitForIdle()
    }

    private fun capture(fileName: String) {
        composeRule.waitForIdle()
        composeRule.onRoot().captureRoboImage(
            filePath = "build/outputs/roborazzi/$fileName",
        )
    }

    private fun captureDialog(fileName: String) {
        composeRule.waitForIdle()
        composeRule.onNode(isDialog()).captureRoboImage(
            filePath = "build/outputs/roborazzi/$fileName",
        )
    }

    private fun historyRecord() = ConsultationEntity(
        id = 7L,
        caseTitle = "突发胸痛伴大汗",
        department = "心内科",
        difficulty = "中等",
        patientName = "王建国",
        caseJson = "{}",
        conversationJson = "[]",
        medicalRecord = "",
        scoreJson = "{}",
        totalScore = 79,
        initialDiagnosis = "急性心肌梗死",
        diagnosisBasis = "持续胸痛伴大汗",
        createdAt = 1_700_000_000_000,
    )

    private fun sampleRecord(): String = """
        一般情况
        王建国，男，58岁，退休工人，由家属陪同就诊。
        主诉
        胸骨后压榨样疼痛3小时，伴大汗。
        现病史
        患者3小时前搬重物时突发胸骨后压榨样疼痛，向左肩放射，持续不缓解，伴大汗、恶心。自服硝酸甘油后无明显缓解。
        既往史
        高血压8年，服药不规律。否认糖尿病和既往心肌梗死病史。
        个人史
        吸烟30年，每日约20支，偶饮酒。
        婚育史
        已婚，育有一子，配偶及儿子体健。
        家族史
        父亲曾因心肌梗死去世，母亲有高血压。
        体格检查
        T 36.8℃，P 96次/分，BP 150/92mmHg。神志清楚，大汗，心音稍低，律齐，双肺呼吸音清，双下肢无水肿。
        系统回顾
        无发热、咳嗽，无腹痛、黑便，无尿路刺激症状。
        初步诊断
        急性ST段抬高型心肌梗死（前壁）
    """.trimIndent()
}
