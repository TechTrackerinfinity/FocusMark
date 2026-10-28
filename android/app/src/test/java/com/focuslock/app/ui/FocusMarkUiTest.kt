package com.focuslock.app.ui

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.focuslock.app.FocusMarkApp
import com.focuslock.app.BlockPage
import com.focuslock.app.data.FocusPreferences
import com.focuslock.app.data.PausePhase
import com.focuslock.app.enforcement.BlockerRuntime
import com.focuslock.app.enforcement.FeedKind
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp")
class FocusMarkUiTest {
    @get:Rule val compose = createComposeRule()
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var preferences: FocusPreferences

    @Before fun prepare() {
        context.getSharedPreferences("focus_lock_rules", Context.MODE_PRIVATE).edit().clear().commit()
        preferences = FocusPreferences(context)
        preferences.setSchedule(540, 1380)
        preferences.setBlockYouTubeShorts(true)
        preferences.setCountYouTubeShorts(true)
        val youtube = PackageInfo().apply {
            packageName = FeedKind.YOUTUBE_SHORTS.packageName
            applicationInfo = ApplicationInfo().apply {
                packageName = FeedKind.YOUTUBE_SHORTS.packageName
                flags = ApplicationInfo.FLAG_INSTALLED
            }
        }
        Shadows.shadowOf(context.packageManager).installPackage(youtube)
        BlockerRuntime.connected()
    }

    @After fun cleanup() { BlockerRuntime.disconnected() }

    private fun openApp() {
        compose.setContent { FocusMarkTheme(darkTheme = false) { FocusMarkApp() } }
    }

    @Test fun mainButtonRequestsDelayedBreakAndCannotInstantlyPause() {
        openApp()
        compose.onNodeWithText("Turn on focus").assertIsDisplayed()
        compose.onNodeWithTag("focus_primary_action").performClick()
        compose.runOnIdle { assertTrue(preferences.rule().enabled) }
        compose.onNodeWithTag("focus_primary_action").performClick()
        compose.onNodeWithText("Request a 10-minute break?").assertIsDisplayed()
        compose.runOnIdle { assertEquals(PausePhase.NONE, preferences.rule().pausePhase) }
        compose.onNodeWithText("Start 20-minute wait").performClick()
        compose.runOnIdle {
            assertTrue(preferences.rule().enabled)
            assertEquals(PausePhase.WAITING, preferences.rule().pausePhase)
            assertTrue(preferences.rule().countYouTubeShorts)
        }
        compose.onNodeWithText("Cancel pause request").performClick()
        compose.runOnIdle { assertEquals(PausePhase.NONE, preferences.rule().pausePhase) }
    }

    @Test fun bottomTabsExposeCountersAndSetupWithoutChangingThePlan() {
        openApp()
        compose.onNodeWithTag("tab_activity").performClick()
        compose.onNodeWithText("Your activity").assertIsDisplayed()
        compose.onNodeWithText("Not installed").assertIsDisplayed()
        compose.onNodeWithTag("tab_settings").performClick()
        compose.onNodeWithText("Protection service").assertIsDisplayed()
        compose.onNodeWithTag("tab_focus").performClick()
        compose.onNodeWithText("Turn on focus").assertIsDisplayed()
        compose.runOnIdle { assertTrue(preferences.rule().blockYouTubeShorts) }
    }

    @Test fun schedulePresetIsStagedUntilTheUserSaves() {
        openApp()
        compose.onNodeWithText("Daily schedule").performScrollTo().performClick()
        compose.onNodeWithText("Morning").performClick()
        compose.runOnIdle { assertEquals(1380, preferences.rule().endMinute) }
        compose.onNodeWithTag("save_schedule").performClick()
        compose.runOnIdle {
            assertEquals(540, preferences.rule().startMinute)
            assertEquals(720, preferences.rule().endMinute)
        }
    }

    @Test fun pickerSearchKeepsExistingSelectionsWhenSavingANewApp() {
        var saved: Set<String>? = null
        compose.setContent {
            FocusMarkTheme {
                AppPickerContent(
                    listOf(SelectableApp("youtube", "YouTube"), SelectableApp("signal", "Signal")),
                    setOf("signal"), { saved = it }, {},
                )
            }
        }
        compose.onNodeWithTag("app_search").performTextInput("You")
        compose.onNodeWithText("Signal").assertDoesNotExist()
        compose.onNodeWithText("YouTube").performClick()
        compose.onNodeWithTag("save_apps").performClick()
        compose.runOnIdle { assertEquals(setOf("signal", "youtube"), saved) }
    }

    @Test fun automaticCountersOfferNoStopSwitchAndDoNotPauseBlocking() {
        preferences.setEnabled(true)
        openApp()
        compose.onNodeWithTag("tab_activity").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("Automatic counting"))
        compose.onNodeWithText("Always on for Shorts and Reels. There is no in-app stop or reset control.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Show Shorts stats").assertDoesNotExist()
        compose.onNodeWithText("Show Reels stats").assertDoesNotExist()
        compose.runOnIdle {
            assertTrue(preferences.rule().countYouTubeShorts)
            assertTrue(preferences.rule().countInstagramReels)
            assertTrue(preferences.rule().blockYouTubeShorts)
            assertTrue(preferences.rule().enabled)
        }
    }

    @Test fun firstFeedOptInRequiresConsentAndThenRemembersIt() {
        context.getSharedPreferences("focus_lock_rules", Context.MODE_PRIVATE).edit().clear().commit()
        openApp()
        compose.onNodeWithTag("tab_activity").performClick()
        compose.onNodeWithText("Set up the daily limit").performClick()
        compose.onNodeWithText("Connect protection").assertIsDisplayed()
        compose.runOnIdle { assertFalse(preferences.rule().countYouTubeShorts) }
        compose.onNodeWithText("Open Android settings").performClick()
        compose.runOnIdle {
            assertTrue(preferences.rule().countYouTubeShorts)
            assertTrue(preferences.rule().countInstagramReels)
            assertTrue(preferences.hasContentConsent())
        }
    }

    @Test fun disconnectedTrackingClearlyWarnsThatEnforcementIsUnavailable() {
        BlockerRuntime.disconnected()
        openApp()
        compose.onNodeWithTag("tab_activity").performClick()
        compose.onNodeWithText("Tracking disconnected. The daily limit cannot be enforced until Accessibility reconnects.").assertIsDisplayed()
        compose.onNodeWithText("Reconnect protection").assertIsDisplayed()
    }

    @Test fun blockScreenShowsTheNewBrandAndOffersTheHomeAction() {
        var wentHome = false
        compose.setContent { FocusMarkTheme { BlockPage("YouTube Shorts", true, 1380) { wentHome = true } } }
        compose.onNodeWithText("FocusMark").assertIsDisplayed()
        compose.onNodeWithText("Your daily focus window is on until 23:00.", substring = true).assertIsDisplayed()
        compose.onNodeWithText("The rest of the app is still available.").assertIsDisplayed()
        compose.onNodeWithText("Return home").performClick()
        compose.runOnIdle { assertTrue(wentHome) }
    }

    @Test fun liveCounterToggleDoesNotTurnOffViewTrackingOrBlocking() {
        preferences.setEnabled(true)
        openApp()
        compose.onNodeWithTag("tab_activity").performClick()
        compose.onNodeWithTag("live_counter_toggle").performScrollTo().performClick()
        compose.runOnIdle {
            assertFalse(preferences.rule().showLiveCounter)
            assertTrue(preferences.rule().countYouTubeShorts)
            assertTrue(preferences.rule().blockYouTubeShorts)
            assertTrue(preferences.rule().enabled)
        }
    }
}
