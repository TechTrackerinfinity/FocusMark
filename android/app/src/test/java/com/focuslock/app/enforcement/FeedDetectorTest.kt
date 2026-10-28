package com.focuslock.app.enforcement

import org.junit.Assert.*
import org.junit.Test

class FeedDetectorTest {
    private val youtube = FeedKind.YOUTUBE_SHORTS.packageName
    private val instagram = FeedKind.INSTAGRAM_REELS.packageName

    @Test fun recognizesViewerIdsObservedOnTheConnectedPhone() {
        listOf("reel_watch_fragment_root", "reel_watch_player", "reel_player_page_container", "reel_recycler")
            .forEach { id ->
                assertEquals(id, FeedKind.YOUTUBE_SHORTS,
                    FeedDetector.detect(youtube, listOf(FeedNode(resourceId = "$youtube:id/$id")))?.kind)
            }
    }

    @Test fun selectedShortsWithViewerControlsWorksWithoutResourceIds() {
        val nodes = listOf(FeedNode(description = "Shorts", selected = true), FeedNode(description = "Remix"))
        assertEquals(FeedKind.YOUTUBE_SHORTS, FeedDetector.detect(youtube, nodes)?.kind)
    }

    @Test fun shortsNavigationButtonOnRegularVideosDoesNotBlockTheWholeApp() {
        val nodes = listOf(FeedNode(description = "Shorts"), FeedNode(description = "Share this video"),
            FeedNode(description = "like this video"), FeedNode(description = "Remix"))
        assertNull(FeedDetector.detect(youtube, nodes))
    }

    @Test fun selectedShortsChipWithoutPlayerControlsIsNotAViewer() {
        assertNull(FeedDetector.detect(youtube, listOf(FeedNode(text = "Shorts", selected = true))))
    }

    @Test fun instagramViewerIdsRecognizeReels() {
        assertEquals(FeedKind.INSTAGRAM_REELS, FeedDetector.detect(instagram,
            listOf(FeedNode(resourceId = "$instagram:id/clips_viewer_view_pager")))?.kind)
    }

    @Test fun ordinaryInstagramPostsAndReelsNavigationAreNotReels() {
        val nodes = listOf(FeedNode(text = "My reel today"), FeedNode(description = "Reels"),
            FeedNode(description = "Like"), FeedNode(description = "Comment"), FeedNode(description = "Share"))
        assertNull(FeedDetector.detect(instagram, nodes))
    }

    @Test fun selectedInstagramReelsAndActionsWorkWithoutIds() {
        val nodes = listOf(FeedNode(description = "Reels", selected = true), FeedNode(description = "Like"))
        assertEquals(FeedKind.INSTAGRAM_REELS, FeedDetector.detect(instagram, nodes)?.kind)
    }

    @Test fun otherPackagesAreNeverClassifiedUsingTheirText() {
        assertNull(FeedDetector.detect("com.example.notes", listOf(FeedNode(resourceId = "reel_watch_player"))))
    }

    @Test fun virtualPlayerDescriptionsIdentifyDifferentVideos() {
        fun detect(caption: String) = FeedDetector.detect(youtube, listOf(
            FeedNode(resourceId = "$youtube:id/reel_watch_player"),
            FeedNode(description = caption, contentArea = true),
        ))!!
        val first = detect("An example video caption about gardening")
        assertNotNull(first.itemFingerprint)
        assertEquals(first.itemFingerprint, detect("An example video caption about gardening").itemFingerprint)
        assertNotEquals(first.itemFingerprint, detect("A different example about making furniture").itemFingerprint)
    }

    @Test fun changingLikeCountsDoesNotCreateANewVideoIdentity() {
        fun detect(like: String) = FeedDetector.detect(youtube, listOf(
            FeedNode(resourceId = "$youtube:id/reel_watch_player"),
            FeedNode(description = "A stable video caption for this test", contentArea = true),
            FeedNode(description = like, contentArea = true),
        ))!!
        assertEquals(detect("like this video with 15 people").itemFingerprint,
            detect("like this video with 16 people").itemFingerprint)
    }

    @Test fun textOutsideTheContentAreaDoesNotChangeVideoIdentity() {
        val result = FeedDetector.detect(youtube, listOf(
            FeedNode(resourceId = "$youtube:id/reel_watch_player"),
            FeedNode(description = "An unrelated navigation description", contentArea = false),
        ))!!
        assertNull(result.itemFingerprint)
    }

    @Test fun lateCommentAndSoundControlsCannotCreateNewVideoIdentities() {
        val player = FeedNode(resourceId = "$youtube:id/reel_watch_player")
        val caption = FeedNode(description = "An example caption with the actual video title", contentArea = true)
        val initial = FeedDetector.detect(youtube, listOf(player, caption))!!
        val loaded = FeedDetector.detect(youtube, listOf(player, caption,
            FeedNode(description = "View 714 comments", contentArea = true),
            FeedNode(description = "See more videos using this sound", contentArea = true),
            FeedNode(description = "Remix this Short along with 13 other remixes", contentArea = true),
        ))!!
        assertEquals(initial.itemFingerprint, loaded.itemFingerprint)
    }

    @Test fun aNewShorterCaptionStillChangesTheVideoIdentity() {
        val player = FeedNode(resourceId = "$youtube:id/reel_watch_player")
        val first = FeedDetector.detect(youtube, listOf(player,
            FeedNode(description = "A longer initial video caption about furniture", contentArea = true)))!!
        val second = FeedDetector.detect(youtube, listOf(player,
            FeedNode(description = "A short title", contentArea = true)))!!
        assertNotEquals(first.itemFingerprint, second.itemFingerprint)
    }

    @Test fun commentAndSeekbarScrollsCannotIncrementTheCounter() {
        assertFalse(FeedDetector.isViewerScroll("$youtube:id/reel_comments_list"))
        assertFalse(FeedDetector.isViewerScroll("$youtube:id/reel_time_bar"))
        assertFalse(FeedDetector.isViewerScroll("$youtube:id/pivot_bar"))
        assertTrue(FeedDetector.isViewerScroll("$youtube:id/reel_recycler"))
        assertTrue(FeedDetector.isViewerScroll("$instagram:id/clips_viewer_view_pager"))
    }
}
