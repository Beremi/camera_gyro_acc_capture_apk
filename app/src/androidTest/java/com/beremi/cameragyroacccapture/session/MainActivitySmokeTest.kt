package com.beremi.cameragyroacccapture.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.beremi.cameragyroacccapture.MainActivity
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun scientificHeaderIsDisplayed() {
        composeRule.onNodeWithText("Scientific Capture Prototype").assertIsDisplayed()
    }
}
