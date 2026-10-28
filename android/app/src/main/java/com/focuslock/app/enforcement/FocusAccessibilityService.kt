package com.focuslock.app.enforcement

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.focuslock.app.BlockActivity
import com.focuslock.app.BuildConfig
import com.focuslock.app.data.FocusPreferences
import com.focuslock.app.data.FocusRule
import java.util.ArrayDeque

/**
 * Inspects opted-in feed labels and supported browsers' visible address labels locally.
 * A light foreground check covers late layouts, missed events and schedule transitions.
 */
class FocusAccessibilityService : AccessibilityService() {
    private lateinit var preferences: FocusPreferences
    private lateinit var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener
    private val handler = Handler(Looper.getMainLooper())
    private val tracker = FeedSessionTracker()
    private var liveCounter: LiveCounterOverlay? = null
    private var connected = false
    private var configuration: WatchConfiguration? = null
    private var scanQueued = false
    private var pendingScroll: ScrollSignal? = null
    private var lastOpenedAt = Long.MIN_VALUE
    private var lastBlockKey = ""
    private var lastTraceAt = Long.MIN_VALUE

    private val scanTask = Runnable {
        scanQueued = false
        scanCurrentWindow()
    }
    private val pollTask = object : Runnable {
        override fun run() {
            if (!connected) return
            if (configuration?.hasWork == true) scanCurrentWindow()
            handler.postDelayed(this, POLL_MILLIS)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        if (::preferences.isInitialized && ::preferenceListener.isInitialized) {
            preferences.unregisterChangeListener(preferenceListener)
        }
        handler.removeCallbacksAndMessages(null)
        liveCounter?.hide()
        liveCounter = LiveCounterOverlay(this)
        configuration = null
        tracker.reset()
        scanQueued = false
        connected = true
        preferences = FocusPreferences(applicationContext)
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
            configureForCurrentRules()
        }
        preferences.registerChangeListener(preferenceListener)
        BlockerRuntime.connected()
        configureForCurrentRules()
        handler.post(pollTask)
        Log.i(TAG, "Accessibility connected version=" + BuildConfig.VERSION_NAME)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!connected || event == null) return
        val eventPackage = event.packageName?.toString()
        // Our live badge can emit window events. Only the actual foreground window may end
        // a feed session; resetting on every event from our package would inflate counts.

        // The existing whole-app path remains immediate. Feed rules are checked against the
        // actual active window after the target app has had time to finish its layout.
        val rule = preferences.rule()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            eventPackage in rule.blockedPackages && preferences.isBlockedNow()
        ) {
            showBlock(eventPackage.orEmpty(), null)
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED && eventPackage != null) {
            val source = runCatching { event.source }.getOrNull()
            try {
                val id = source?.viewIdResourceName.orEmpty()
                if (FeedDetector.isViewerScroll(id)) {
                    pendingScroll = ScrollSignal(
                        packageName = eventPackage,
                        index = event.fromIndex.takeIf { it >= 0 },
                    )
                }
            } finally {
                source?.let(::release)
            }
        }
        queueScan()
    }

    override fun onInterrupt() {
        liveCounter?.hide()
        tracker.reset()
        queueScan()
    }

    override fun onUnbind(intent: Intent?): Boolean {
        disconnect()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        disconnect()
        super.onDestroy()
    }

    private fun disconnect() {
        liveCounter?.hide()
        liveCounter = null
        connected = false
        handler.removeCallbacksAndMessages(null)
        scanQueued = false
        tracker.reset()
        pendingScroll = null
        if (::preferences.isInitialized && ::preferenceListener.isInitialized) {
            preferences.unregisterChangeListener(preferenceListener)
        }
        BlockerRuntime.disconnected()
    }

    private fun configureForCurrentRules() {
        if (!connected) return
        val rule = preferences.rule()
        liveCounter?.shown?.let { current ->
            if (liveCounterState(rule, current.kind, preferences.isScheduleActiveNow()) == null) liveCounter?.hide()
        }
        val instagram = rule.budgetTrackingEnabled || rule.countInstagramReels || rule.blockInstagramReels
        val youtube = rule.budgetTrackingEnabled || rule.countYouTubeShorts || rule.blockYouTubeShorts
        val packages = rule.blockedPackages.toMutableSet()
        packages += packageName
        if (instagram) packages += FeedKind.INSTAGRAM_REELS.packageName
        if (youtube) packages += FeedKind.YOUTUBE_SHORTS.packageName
        if (rule.budgetTrackingEnabled || rule.enabled) packages += BrowserGuard.packages
        val next = WatchConfiguration(
            packages.toSet(),
            instagram || youtube,
            instagram || youtube || (rule.enabled && rule.blockedPackages.isNotEmpty()),
        )
        if (next == configuration) return

        val info = serviceInfo
        info.packageNames = next.packages.toTypedArray()
        info.eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
            AccessibilityEvent.TYPE_WINDOWS_CHANGED or AccessibilityEvent.TYPE_VIEW_SELECTED
        if (next.content) {
            info.eventTypes = info.eventTypes or AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                AccessibilityEvent.TYPE_VIEW_SCROLLED
        }
        // YouTube's viewer containers can be omitted unless non-important nodes are included.
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
            AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
            AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        info.notificationTimeout = 50
        setServiceInfo(info)
        configuration = next
        tracker.reset()
        pendingScroll = null
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Configured packages=" + next.packages.joinToString() + " flags=" + info.flags)
        }
        queueScan()
    }

    private fun queueScan() {
        if (!connected || scanQueued) return
        scanQueued = true
        handler.postDelayed(scanTask, LAYOUT_SETTLE_MILLIS)
    }

    private fun scanCurrentWindow() {
        if (!connected || !::preferences.isInitialized) return
        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (!power.isInteractive) {
            liveCounter?.hide()
            tracker.reset()
            BlockerRuntime.detected(null)
            return
        }
        var root: AccessibilityNodeInfo? = null
        try {
            root = rootInActiveWindow
            val interactiveWindows = windows
            val activeRootId = root?.windowId
            if (activeRootId != null && interactiveWindows.any {
                    it.id == activeRootId && it.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY
                }
            ) {
                root?.let(::release)
                root = null
            }
            if (root == null) {
                // Some OEM windows become available through the interactive-window list first.
                val active = interactiveWindows.firstOrNull {
                    it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused)
                }
                root = active?.root
            }
            if (root == null) {
                liveCounter?.hide()
                trace("No active accessibility root")
                return
            }

            val currentRoot = root
            val foregroundPackage = currentRoot.packageName?.toString().orEmpty()
            val rule = preferences.rule()
            if (foregroundPackage in rule.blockedPackages && rule.blockingActive) {
                liveCounter?.hide()
                tracker.reset()
                BlockerRuntime.detected(null)
                showBlock(foregroundPackage, null)
                return
            }

            if (foregroundPackage in BrowserGuard.packages) {
                liveCounter?.hide()
                tracker.reset()
                pendingScroll = null
                BlockerRuntime.detected(null)
                val address = browserAddress(currentRoot, foregroundPackage)
                val guarded = address?.let { BrowserGuard.blockedPage(rule, it) }
                if (guarded != null) showBlock(foregroundPackage, guarded.label, BlockActivity.REASON_WEB_GUARD)
                return
            }

            val watches = when (foregroundPackage) {
                FeedKind.INSTAGRAM_REELS.packageName -> rule.budgetTrackingEnabled || rule.countInstagramReels || rule.blockInstagramReels
                FeedKind.YOUTUBE_SHORTS.packageName -> rule.budgetTrackingEnabled || rule.countYouTubeShorts || rule.blockYouTubeShorts
                else -> false
            }
            if (!watches) {
                liveCounter?.hide()
                tracker.reset()
                pendingScroll = null
                BlockerRuntime.detected(null)
                return
            }

            val nodes = readVisibleNodes(currentRoot)
            val detection = FeedDetector.detect(foregroundPackage, nodes)
            val now = SystemClock.elapsedRealtime()
            val scroll = pendingScroll?.takeIf { it.packageName == foregroundPackage }
            pendingScroll = null
            BlockerRuntime.detected(detection?.kind)
            trace("Scan package=" + foregroundPackage + " nodes=" + nodes.size +
                " detected=" + detection?.kind + " identity=" + (detection?.itemFingerprint != null))

            if (detection == null) {
                liveCounter?.hide()
                tracker.observe(null, now)
                return
            }

            if (rule.dailyLimitReached) {
                liveCounter?.hide()
                tracker.reset()
                showBlock(foregroundPackage, detection.kind.label, BlockActivity.REASON_DAILY_LIMIT)
                return
            }

            // Recheck blocking on every scan so a late layout or schedule change also takes effect.
            val blocks = when (detection.kind) {
                FeedKind.INSTAGRAM_REELS -> rule.blockInstagramReels
                FeedKind.YOUTUBE_SHORTS -> rule.blockYouTubeShorts
            }
            if (blocks && rule.blockingActive) {
                liveCounter?.hide()
                tracker.reset()
                showBlock(foregroundPackage, detection.kind.label)
                return
            }

            val counts = when (detection.kind) {
                FeedKind.INSTAGRAM_REELS -> rule.countInstagramReels
                FeedKind.YOUTUBE_SHORTS -> rule.countYouTubeShorts
            }
            if (!counts && !rule.budgetTrackingEnabled) {
                tracker.reset()
                liveCounter?.hide()
                return
            }
            val newItem = tracker.observe(detection, now, scroll != null, scroll?.index)
            if (newItem) {
                val recorded = preferences.recordFeedView(detection.kind == FeedKind.INSTAGRAM_REELS)
                if (recorded.dailyLimitReached) {
                    tracker.reset()
                    liveCounter?.hide()
                    showBlock(foregroundPackage, detection.kind.label, BlockActivity.REASON_DAILY_LIMIT)
                    return
                }
                if (BuildConfig.DEBUG) Log.d(TAG, "Counted " + detection.kind)
            }
            val liveState = liveCounterState(preferences.rule(), detection.kind, preferences.isScheduleActiveNow())
            if (liveState == null) {
                liveCounter?.hide()
            } else if (liveCounter?.show(liveState) == false) {
                BlockerRuntime.error("The live counter could not appear. Your views are still counted in Activity.")
            }
        } catch (error: RuntimeException) {
            liveCounter?.hide()
            // Stale accessibility nodes and OEM binder failures must not kill the service.
            BlockerRuntime.error("Screen detection needs another attempt.")
            Log.w(TAG, "Accessibility scan failed: " + error.javaClass.simpleName)
        } finally {
            root?.let(::release)
        }
    }

    private fun showBlock(targetPackage: String, featureLabel: String?, reason: String = BlockActivity.REASON_SCHEDULE) {
        liveCounter?.hide()
        val key = "$targetPackage:$featureLabel:$reason"
        val now = SystemClock.elapsedRealtime()
        if (key == lastBlockKey && now - lastOpenedAt < BLOCK_DEBOUNCE_MILLIS) return
        lastBlockKey = key
        lastOpenedAt = now
        if (featureLabel != null) {
            // Leave the short-form viewer before showing the interstitial. Otherwise reopening
            // YouTube/Instagram restores that viewer and traps users away from regular content.
            val exited = runCatching { performGlobalAction(GLOBAL_ACTION_BACK) }.getOrDefault(false)
            handler.postDelayed(
                { if (connected) openBlockScreen(targetPackage, featureLabel, key, reason) },
                if (exited) FEED_EXIT_DELAY_MILLIS else 0L,
            )
        } else {
            openBlockScreen(targetPackage, null, key, reason)
        }
    }

    private fun openBlockScreen(targetPackage: String, featureLabel: String?, key: String, reason: String) {
        val current = preferences.rule()
        if (reason == BlockActivity.REASON_SCHEDULE && !current.blockingActive) return
        if (reason == BlockActivity.REASON_DAILY_LIMIT && !current.dailyLimitReached) return
        try {
            startActivity(
                Intent(this, BlockActivity::class.java)
                    .putExtra(BlockActivity.EXTRA_BLOCKED_PACKAGE, targetPackage)
                    .putExtra(BlockActivity.EXTRA_BLOCKED_FEATURE, featureLabel)
                    .putExtra(BlockActivity.EXTRA_BLOCK_REASON, reason)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP),
            )
            if (BuildConfig.DEBUG) Log.d(TAG, "Block requested target=" + key)
        } catch (error: RuntimeException) {
            BlockerRuntime.error("Android prevented the block screen from opening.")
            Log.w(TAG, "Block screen failed: " + error.javaClass.simpleName)
        }
    }

    private fun browserAddress(root: AccessibilityNodeInfo, packageName: String): String? {
        for (id in BrowserGuard.addressIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(packageName + ":id/" + id).orEmpty()
            try {
                for (node in nodes) {
                    if (node.isVisibleToUser && !node.isFocused && !node.isPassword) {
                        val address = node.text?.toString()?.take(2_048).orEmpty()
                        if (address.isNotBlank()) return address
                    }
                }
            } finally {
                nodes.forEach(::release)
            }
        }
        return null
    }

    private fun readVisibleNodes(root: AccessibilityNodeInfo): List<FeedNode> {
        val result = mutableListOf<FeedNode>()
        val windowBounds = Rect().also(root::getBoundsInScreen)
        val queue = ArrayDeque<TreeEntry>()
        queue.add(TreeEntry(root, 0, false, false, false))
        var visited = 0
        try {
            while (queue.isNotEmpty() && visited < MAX_TREE_NODES) {
                val entry = queue.removeFirst()
                val node = entry.node
                try {
                    visited++
                    if (entry.depth > MAX_TREE_DEPTH || node.isPassword || node.isEditable) continue
                    val id = node.viewIdResourceName.orEmpty()
                    val entryName = id.substringAfterLast('/')
                    val excluded = entry.excludedMetadata ||
                        listOf("comment", "time_bar", "seek", "toolbar", "pivot_bar").any { it in entryName }
                    val content = entry.contentArea ||
                        listOf("player_overlay", "reel_player_footer_container", "clips_viewer", "reels_viewer")
                            .any { it in entryName }
                    if (node.isVisibleToUser) {
                        val bounds = Rect().also(node::getBoundsInScreen)
                        // Titles/captions occupy the content column. The narrow action rail has
                        // dynamically changing counts and localized controls, not item identity.
                        val contentColumn = bounds.width() >= windowBounds.width() / 4 &&
                            bounds.left < windowBounds.left + windowBounds.width() * 0.65f
                        result += FeedNode(
                            resourceId = id,
                            text = node.text?.toString()?.take(2_000).orEmpty(),
                            description = node.contentDescription?.toString()?.take(2_000).orEmpty(),
                            selected = node.isSelected,
                            contentArea = content && !excluded && contentColumn,
                        )
                    }
                    if (entry.depth < MAX_TREE_DEPTH) {
                        for (index in 0 until node.childCount) {
                            val child = runCatching { node.getChild(index) }.getOrNull() ?: continue
                            queue.add(TreeEntry(child, entry.depth + 1, content, excluded, true))
                        }
                    }
                } finally {
                    if (entry.owned) release(node)
                }
            }
        } finally {
            while (queue.isNotEmpty()) {
                val entry = queue.removeFirst()
                if (entry.owned) release(entry.node)
            }
        }
        return result
    }

    private fun trace(message: String) {
        if (!BuildConfig.DEBUG) return
        val now = SystemClock.elapsedRealtime()
        if (lastTraceAt != Long.MIN_VALUE && now - lastTraceAt < TRACE_MILLIS) return
        lastTraceAt = now
        Log.d(TAG, message)
    }

    @Suppress("DEPRECATION")
    private fun release(node: AccessibilityNodeInfo) {
        if (Build.VERSION.SDK_INT < 33) node.recycle()
    }

    private data class WatchConfiguration(val packages: Set<String>, val content: Boolean, val hasWork: Boolean)
    private data class ScrollSignal(val packageName: String, val index: Int?)
    private data class TreeEntry(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val contentArea: Boolean,
        val excludedMetadata: Boolean,
        val owned: Boolean,
    )

    private companion object {
        const val TAG = "FocusLock"
        const val POLL_MILLIS = 650L
        const val LAYOUT_SETTLE_MILLIS = 120L
        const val BLOCK_DEBOUNCE_MILLIS = 1_000L
        const val FEED_EXIT_DELAY_MILLIS = 180L
        const val MAX_TREE_NODES = 1_200
        const val MAX_TREE_DEPTH = 64
        const val TRACE_MILLIS = 3_000L
    }
}
