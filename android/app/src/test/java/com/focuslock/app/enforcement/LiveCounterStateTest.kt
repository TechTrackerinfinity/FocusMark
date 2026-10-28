package com.focuslock.app.enforcement

import com.focuslock.app.data.FocusRule
import com.focuslock.app.data.PausePhase
import org.junit.Assert.*
import org.junit.Test

class LiveCounterStateTest {
    private fun rule() = FocusRule(false, 540, 1380, emptySet(), true, true, true, true, 4, 12)

    @Test fun enabledFeedShowsItsOwnDailyTotalWhileBlockingIsPaused() {
        assertEquals(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12), liveCounterState(rule(), FeedKind.YOUTUBE_SHORTS, true))
        assertEquals(LiveCounterState(FeedKind.INSTAGRAM_REELS, 4), liveCounterState(rule(), FeedKind.INSTAGRAM_REELS, true))
    }

    @Test fun leavingTheFeedHidesTheBadge() {
        assertNull(liveCounterState(rule(), null, false))
    }

    @Test fun userCanHideTheBadgeWithoutDisablingCounters() {
        val rule = rule().copy(showLiveCounter = false)
        assertNull(liveCounterState(rule, FeedKind.YOUTUBE_SHORTS, false))
        assertTrue(rule.countYouTubeShorts)
    }

    @Test fun disabledPlatformCounterDoesNotShowOtherPlatformTotal() {
        val rule = rule().copy(countYouTubeShorts = false)
        assertNull(liveCounterState(rule, FeedKind.YOUTUBE_SHORTS, false))
        assertEquals(4, liveCounterState(rule, FeedKind.INSTAGRAM_REELS, false)?.count)
    }

    @Test fun activelyBlockedFeedHasNoBadge() {
        assertNull(liveCounterState(rule().copy(enabled = true), FeedKind.YOUTUBE_SHORTS, true))
    }

    @Test fun outsideTheBlockWindowCountingStillShowsTheBadge() {
        assertNotNull(liveCounterState(rule().copy(enabled = true), FeedKind.YOUTUBE_SHORTS, false))
    }

    @Test fun wholeAppBlockAlsoHidesTheBadge() {
        val rule = rule().copy(enabled = true, blockYouTubeShorts = false,
            blockedPackages = setOf(FeedKind.YOUTUBE_SHORTS.packageName))
        assertNull(liveCounterState(rule, FeedKind.YOUTUBE_SHORTS, true))
    }

    @Test fun aResetDailyTotalIsDisplayedAsZeroRatherThanAnOldValue() {
        assertEquals(0, liveCounterState(rule().copy(youtubeShortsToday = 0), FeedKind.YOUTUBE_SHORTS, false)?.count)
    }

    @Test fun aTimedBreakShowsCounterAndSharedAllowance() {
        val r = rule().copy(enabled = true, pausePhase = PausePhase.BREAK, dailyShortsUsed = 16, budgetTrackingEnabled = true)
        assertEquals(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12, 16), liveCounterState(r, FeedKind.YOUTUBE_SHORTS, true))
        assertNull(liveCounterState(r.copy(dailyShortsUsed = 100), FeedKind.YOUTUBE_SHORTS, false))
    }
}
