package com.focuslock.app.enforcement

import org.junit.Assert.*
import org.junit.Test

class FeedSessionTrackerTest {
    private val first = FeedDetection(FeedKind.YOUTUBE_SHORTS, "first")
    private val second = FeedDetection(FeedKind.YOUTUBE_SHORTS, "second")

    @Test fun eventStormsAndRepeatedPollsCountAViewerOnlyOnce() {
        val tracker = FeedSessionTracker()
        assertTrue(tracker.observe(first, 100))
        for (time in 200L..8_000L step 100) assertFalse(tracker.observe(first, time))
    }

    @Test fun aChangedVideoCountsOnceAfterItsMetadataSettles() {
        val tracker = FeedSessionTracker()
        assertTrue(tracker.observe(first, 100))
        assertFalse(tracker.observe(second, 1_200))
        assertTrue(tracker.observe(second, 1_500))
        assertFalse(tracker.observe(second, 2_000))
    }

    @Test fun transientLoadingMetadataCannotInflateTheCounter() {
        val tracker = FeedSessionTracker()
        tracker.observe(first, 100)
        assertFalse(tracker.observe(second, 1_000))
        assertFalse(tracker.observe(first, 1_100))
        assertFalse(tracker.observe(first, 1_500))
    }

    @Test fun initialMetadataArrivingLateIsNotASecondVideo() {
        val tracker = FeedSessionTracker()
        assertTrue(tracker.observe(first.copy(itemFingerprint = null), 100))
        assertFalse(tracker.observe(first, 800))
        assertFalse(tracker.observe(first, 1_500))
    }

    @Test fun resettingOnLeavingTheAppAllowsANewEntry() {
        val tracker = FeedSessionTracker()
        assertTrue(tracker.observe(first, 100))
        tracker.reset()
        assertTrue(tracker.observe(first, 800))
    }

    @Test fun briefMissingLayoutDoesNotCountAgainButLeavingTheFeedDoes() {
        val tracker = FeedSessionTracker()
        tracker.observe(first, 100)
        assertFalse(tracker.observe(null, 300))
        assertFalse(tracker.observe(first, 400))
        assertFalse(tracker.observe(null, 1_500))
        assertTrue(tracker.observe(first, 1_600))
    }

    @Test fun scrollFallbackDeduplicatesRepeatedIndices() {
        val tracker = FeedSessionTracker()
        val unknown = first.copy(itemFingerprint = null)
        tracker.observe(unknown, 100, index = 0)
        assertTrue(tracker.observe(unknown, 1_500, viewerScrolled = true, index = 1))
        assertFalse(tracker.observe(unknown, 2_000, viewerScrolled = true, index = 1))
        assertTrue(tracker.observe(unknown, 3_000, viewerScrolled = true, index = 2))
    }

    @Test fun scrollEventsWithKnownUnchangedMetadataDoNotDoubleCount() {
        val tracker = FeedSessionTracker()
        tracker.observe(first, 100)
        assertFalse(tracker.observe(first, 1_500, viewerScrolled = true, index = 1))
    }

    @Test fun captionMissingDuringASwipeCannotCountBothScrollAndMetadata() {
        val tracker = FeedSessionTracker()
        tracker.observe(first, 100)
        assertFalse(tracker.observe(first.copy(itemFingerprint = null), 1_500, viewerScrolled = true, index = 1))
        assertFalse(tracker.observe(second, 1_700))
        assertTrue(tracker.observe(second, 2_000))
    }

    @Test fun fallbackScrollRequiresATrustedViewerScroll() {
        val tracker = FeedSessionTracker()
        val unknown = first.copy(itemFingerprint = null)
        tracker.observe(unknown, 100)
        assertFalse(tracker.observe(unknown, 2_000))
        assertTrue(tracker.observe(unknown, 2_100, viewerScrolled = true))
        assertFalse(tracker.observe(unknown, 2_300, viewerScrolled = true))
    }
}
