package com.xjb.wenzhen.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.text.TextLayoutResult
import com.xjb.wenzhen.data.cases.BuiltInCases
import com.xjb.wenzhen.data.model.ChatMessage
import com.xjb.wenzhen.data.model.MessageDeliveryState
import com.xjb.wenzhen.data.model.MessageRole
import com.xjb.wenzhen.ui.screens.ChatScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ChatStreamingScrollTest {
    @get:Rule
    val rule = createComposeRule()

    private val list = SemanticsMatcher.keyIsDefined(SemanticsActions.ScrollToIndex)

    @Test
    fun growingPatientBubbleFollowsTheBottomUnlessTheUserScrollsUp() {
        val case = BuiltInCases.all.first()
        val initialReply = ChatMessage(
            role = MessageRole.PATIENT,
            content = "我在回想当时的情况。",
            deliveryState = MessageDeliveryState.SENDING,
        )
        var reply by mutableStateOf(initialReply)
        rule.setContent {
            ChatScreen(
                medicalCase = case,
                messages = listOf(ChatMessage(MessageRole.DOCTOR, "具体是什么情况？", timestamp = 1L), reply),
                isPatientTyping = true,
                canPractice = true,
                errorMessage = null,
                onSendMessage = {},
                onFinish = {},
                onBack = {},
                onOpenSettings = {},
                onDismissError = {},
            )
        }
        assertLastCharacterVisible(initialReply.content)

        val longReply = List(55) { index -> "第 ${index + 1} 行测试回复" }.joinToString("\n")
        rule.runOnIdle { reply = initialReply.copy(content = longReply) }
        assertLastCharacterVisible(longReply)

        rule.onNode(list).performTouchInput { swipeDown(durationMillis = 450) }
        rule.onNodeWithContentDescription("回到最新消息").assertIsDisplayed()
        val expandedReply = "$longReply\n最后一条测试消息"
        rule.runOnIdle { reply = initialReply.copy(content = expandedReply) }
        rule.onNodeWithContentDescription("回到最新消息").assertIsDisplayed()

        rule.onNodeWithContentDescription("回到最新消息").performClick()
        assertLastCharacterVisible(expandedReply)
    }

    private fun assertLastCharacterVisible(text: String) {
        val node = rule.onNodeWithText(text)
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { readLayout ->
            assertTrue(readLayout(layouts))
        }
        val last = layouts.single().getBoundingBox(text.lastIndex)
        val top = node.fetchSemanticsNode().positionInRoot.y
        val viewport = rule.onNode(list).fetchSemanticsNode().boundsInRoot
        assertTrue("最后一行下沿 ${top + last.bottom} 超出列表下沿 ${viewport.bottom}", top + last.bottom <= viewport.bottom + 1)
        assertTrue("最后一行超出列表上沿", top + last.top >= viewport.top - 1)
    }
}
