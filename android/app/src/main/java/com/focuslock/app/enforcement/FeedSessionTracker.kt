package com.focuslock.app.enforcement

/** Deduplicates event storms. Fingerprints stay in memory and are cleared on leaving a feed. */
class FeedSessionTracker {
    private var kind: FeedKind? = null
    private var fingerprint: String? = null
    private var candidate: String? = null
    private var candidateSince = 0L
    private var lastSeen = 0L
    private var lastCount = Long.MIN_VALUE
    private var enteredAt = 0L
    private var lastIndex: Int? = null

    fun observe(
        detection: FeedDetection?,
        now: Long,
        viewerScrolled: Boolean = false,
        index: Int? = null,
    ): Boolean {
        if (detection == null) {
            if (now - lastSeen >= EXIT_GRACE_MILLIS) reset()
            return false
        }
        lastSeen = now
        if (kind != detection.kind) {
            kind = detection.kind
            fingerprint = detection.itemFingerprint
            candidate = null
            enteredAt = now
            lastCount = now
            lastIndex = index
            return true
        }

        val next = detection.itemFingerprint
        if (next != null) {
            if (fingerprint == null || now - enteredAt < INITIAL_SETTLE_MILLIS) {
                fingerprint = next
                candidate = null
                return false
            }
            if (next == fingerprint) {
                candidate = null
                return false
            }
            if (candidate != next) {
                candidate = next
                candidateSince = now
                return false
            }
            if (now - candidateSince >= ITEM_SETTLE_MILLIS) {
                fingerprint = next
                candidate = null
                lastCount = now
                lastIndex = index
                return true
            }
            return false
        }

        // A brief missing caption during a swipe must not count once by scroll and again by identity.
        if (fingerprint != null) return false

        // A supported viewer's scroll index is a better fallback than counting every scroll event.
        if (viewerScrolled && index != null) {
            val changed = lastIndex != null && index != lastIndex
            lastIndex = index
            if (changed && now - lastCount >= ITEM_SETTLE_MILLIS) {
                lastCount = now
                return true
            }
        } else if (viewerScrolled && now - lastCount >= FALLBACK_SCROLL_MILLIS) {
            lastCount = now
            return true
        }
        return false
    }

    fun reset() {
        kind = null
        fingerprint = null
        candidate = null
        lastIndex = null
        lastSeen = 0L
        enteredAt = 0L
        lastCount = Long.MIN_VALUE
    }

    private companion object {
        const val INITIAL_SETTLE_MILLIS = 600L
        const val ITEM_SETTLE_MILLIS = 250L
        const val FALLBACK_SCROLL_MILLIS = 900L
        const val EXIT_GRACE_MILLIS = 1_000L
    }
}
