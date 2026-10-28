package com.focuslock.app.enforcement

import java.security.MessageDigest
import java.util.Locale

enum class FeedKind(val packageName: String, val label: String) {
    INSTAGRAM_REELS("com.instagram.android", "Instagram Reels"),
    YOUTUBE_SHORTS("com.google.android.youtube", "YouTube Shorts"),
}

/** A transient, visible, non-editable node. These values must never be logged or persisted. */
data class FeedNode(
    val resourceId: String = "",
    val text: String = "",
    val description: String = "",
    val selected: Boolean = false,
    val contentArea: Boolean = false,
)

data class FeedDetection(val kind: FeedKind, val itemFingerprint: String?)

object FeedDetector {
    fun detect(packageName: String, nodes: List<FeedNode>): FeedDetection? {
        val kind = FeedKind.entries.firstOrNull { it.packageName == packageName } ?: return null
        val ids = nodes.map { it.resourceId.substringAfterLast('/').lowercase(Locale.ROOT) }
        val selectedLabels = nodes.filter { it.selected }.flatMap { listOf(it.text, it.description) }
            .map { it.trim().lowercase(Locale.ROOT) }
        val labels = nodes.flatMap { listOf(it.text, it.description) }.map { it.lowercase(Locale.ROOT) }

        val isFeed = when (kind) {
            FeedKind.YOUTUBE_SHORTS -> ids.any { id ->
                id.startsWith("reel_watch_") || id.startsWith("reel_player_") ||
                    id == "reel_recycler" || id.startsWith("shorts_player") ||
                    id.startsWith("shorts_video") || id.startsWith("shorts_pager")
            } || (selectedLabels.any { it == "shorts" } &&
                labels.any { it.startsWith("dislike") || it == "remix" })

            FeedKind.INSTAGRAM_REELS -> ids.any { id ->
                id.contains("clips_viewer") || id.contains("clips_video_container") ||
                    id.contains("reels_viewer") || id.contains("reel_viewer") ||
                    id == "clips_video_player"
            } || (selectedLabels.any { it == "reels" || it.startsWith("reels,") } &&
                labels.any { it.startsWith("like") || it.startsWith("comment") })
        }
        if (!isFeed) return null

        // Prefer captions/title fields. Virtual Compose nodes often have no resource ID, so
        // visible descriptions inside the player's content area provide the second path.
        val explicitMetadata = nodes.filter { node ->
            val id = node.resourceId.substringAfterLast('/').lowercase(Locale.ROOT)
            METADATA_IDS.any { it in id }
        }.flatMap { listOf(it.text, it.description) }.filter(::isMetadata)
        val virtualMetadata = nodes.filter { it.contentArea }
            .flatMap { listOf(it.text, it.description) }.filter(::isMetadata)
        // Additional action labels can load seconds after the caption. Hash the primary caption
        // only, rather than the set of all labels, so those changes cannot look like a new item.
        val metadata = explicitMetadata.ifEmpty { virtualMetadata }.maxByOrNull { it.length }
        val fingerprint = if (metadata == null) null else {
            MessageDigest.getInstance("SHA-256").digest(metadata.toByteArray())
                .take(12).joinToString("") { "%02x".format(it) }
        }
        return FeedDetection(kind, fingerprint)
    }

    fun isViewerScroll(resourceId: String): Boolean {
        val id = resourceId.substringAfterLast('/').lowercase(Locale.ROOT)
        if (listOf("comment", "time_bar", "seek", "caption", "description").any { it in id }) return false
        return id == "reel_recycler" || id.contains("clips_viewer") ||
            id.contains("shorts_pager") || id.contains("reels_pager") || id.contains("reel_viewer")
    }

    private fun isMetadata(value: String): Boolean {
        val label = value.trim()
        if (label.length < 10 || label.length > 2_000) return false
        if (label.none { it.isLetter() }) return false
        return !CONTROL_LABEL.containsMatchIn(label)
    }

    private val METADATA_IDS = listOf(
        "reel_video_title", "reel_video_description", "reel_title", "clips_caption",
        "clips_description", "reel_caption", "reels_caption",
    )
    private val CONTROL_LABEL = Regex(
        "^(like|unlike|dislike|share|remix|comments?|subscribe|subscribed|go to channel|" +
            "pause|play|mute|unmute|seek|back|search|more|use this sound|original audio|" +
            "audio|follow|following|send|save|view (?:[\\d,.]+[kmb]? )?comments|view all|" +
            "see more videos|tap to|loading|please wait|double tap|video progress|" +
            "shorts|reels|home|subscriptions|create|you)(\\b|[,.:])",
        RegexOption.IGNORE_CASE,
    )
}
