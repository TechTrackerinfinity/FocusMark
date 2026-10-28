package com.focuslock.app.ui

import com.focuslock.app.data.FocusRule
import com.focuslock.app.data.PausePhase
import com.focuslock.app.enforcement.FeedKind
import org.junit.Assert.*
import org.junit.Test

class FocusPresentationTest {
    private fun rule() = FocusRule(true, 540, 1380, emptySet(), false, true, false, true, 0, 12)

    @Test fun connectedAndInScheduleShowsAnActivePlan() {
        val summary = focusSummary(rule(), true, 800)
        assertEquals(FocusStatus.ACTIVE, summary.status)
        assertEquals(FocusAction.PAUSE, summary.action)
        assertTrue(summary.detail.contains("20-minute"))
    }

    @Test fun outsideTheWindowShowsScheduledRatherThanActive() {
        assertEquals(FocusStatus.SCHEDULED, focusSummary(rule(), true, 1400).status)
    }

    @Test fun pausedPlanOffersStartWhileCounterPreferenceIsPreserved() {
        val rule = rule().copy(enabled = false)
        assertEquals(FocusAction.START, focusSummary(rule, true, 800).action)
        assertTrue(rule.countYouTubeShorts)
    }

    @Test fun enabledButDisconnectedServiceAsksForSetup() {
        val summary = focusSummary(rule(), false, 800)
        assertEquals(FocusStatus.SETUP, summary.status)
        assertEquals(FocusAction.CONNECT, summary.action)
    }

    @Test fun counterOnlyPlanDoesNotClaimBlockingIsActive() {
        val summary = focusSummary(rule().copy(blockYouTubeShorts = false), true, 800)
        assertEquals(FocusStatus.EMPTY, summary.status)
        assertEquals(FocusAction.CHOOSE_APPS, summary.action)
    }

    @Test fun anUninstalledFeedCannotClaimActiveBlocking() {
        assertEquals(FocusStatus.EMPTY, focusSummary(rule(), true, 800, emptySet()).status)
    }

    @Test fun wholeAppSelectionWorksWithoutAFeedRule() {
        val rule = rule().copy(blockYouTubeShorts = false, blockedPackages = setOf("example.app"))
        assertEquals(FocusStatus.ACTIVE, focusSummary(rule, true, 800, emptySet()).status)
    }

    @Test fun invalidSchedulePointsToTheScheduleEditor() {
        val summary = focusSummary(rule().copy(endMinute = 540), true, 800)
        assertEquals(FocusAction.SCHEDULE, summary.action)
    }

    @Test fun waitingAndBreakShowRemainingTimeAndSafeActions() {
        val waiting = focusSummary(rule().copy(pausePhase = PausePhase.WAITING, pauseWaitRemainingMs = 1_199_001), true, 800)
        assertEquals(FocusAction.CANCEL_PAUSE, waiting.action)
        assertTrue(waiting.detail.contains("20:00"))
        val takingBreak = focusSummary(rule().copy(pausePhase = PausePhase.BREAK, pauseRemainingMs = 599_001), true, 800)
        assertEquals(FocusAction.RESUME, takingBreak.action)
        assertTrue(takingBreak.detail.contains("10:00"))
    }

    @Test fun protectedClockOverridesDeviceClockForStatus() {
        assertEquals(FocusStatus.ACTIVE, focusSummary(rule().copy(effectiveMinute = 800), true, 1400).status)
    }

    @Test fun overnightStatusAndEndBoundaryAreCorrect() {
        val rule = rule().copy(startMinute = 1320, endMinute = 360)
        assertEquals(FocusStatus.ACTIVE, focusSummary(rule, true, 120).status)
        assertEquals(FocusStatus.SCHEDULED, focusSummary(rule, true, 360).status)
    }
}
