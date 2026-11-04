package com.xjb.wenzhen.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.xjb.wenzhen.ui.components.bringIntoViewOnFocus
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
class ImeModifiersTest {
    @get:Rule
    val rule = createComposeRule()

    @Test
    fun focusedFieldBelowViewportIsScrolledIntoView() {
        val focus = FocusRequester()
        rule.setContent {
            LazyColumn(Modifier.height(300.dp)) {
                item { Spacer(Modifier.height(280.dp)) }
                item {
                    OutlinedTextField(
                        value = "",
                        onValueChange = {},
                        label = { Text("治疗") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("field")
                            .focusRequester(focus)
                            .bringIntoViewOnFocus(),
                    )
                }
            }
        }
        // 初始只露出一小截
        val before = rule.onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val px280 = with(rule.density) { 280.dp.toPx() }
        assertFalse(before.top < px280 - 1f)
        rule.runOnIdle { focus.requestFocus() }
        rule.waitForIdle()
        val after = rule.onNodeWithTag("field").fetchSemanticsNode().boundsInRoot
        val viewport = with(rule.density) { 300.dp.toPx() }
        assertTrue("field 未被滚动进视口: top=${after.top}, bottom=${after.bottom}", after.top < px280 - 10f && after.bottom <= viewport + 1f)
        rule.onNodeWithTag("field").assertIsDisplayed()
    }
}
