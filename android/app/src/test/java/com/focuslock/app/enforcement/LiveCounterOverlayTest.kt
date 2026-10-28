package com.focuslock.app.enforcement

import android.view.View
import android.view.WindowManager
import android.widget.TextView
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import android.os.Looper
import java.time.Duration
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiveCounterOverlayTest {
    private class Windows {
        var adds = 0
        var removes = 0
        var view: View? = null
        var params: WindowManager.LayoutParams? = null
        var reject = false
        val manager = Proxy.newProxyInstance(WindowManager::class.java.classLoader, arrayOf(WindowManager::class.java)) { _, method, args ->
            when (method.name) {
                "addView" -> {
                    if (reject) throw WindowManager.BadTokenException("Test failure")
                    adds++
                    view = args[0] as View
                    params = args[1] as WindowManager.LayoutParams
                    null
                }
                "removeViewImmediate", "removeView" -> { removes++; null }
                "toString" -> "TestWindowManager"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> null
            }
        } as WindowManager
    }

    @Test fun badgeUsesANonTouchableNonFocusableAccessibilityWindow() {
        val windows = Windows()
        val overlay = LiveCounterOverlay(RuntimeEnvironment.getApplication(), windows.manager)
        assertTrue(overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12)))
        val params = windows.params!!
        assertEquals(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, params.type)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE != 0)
        assertTrue(params.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0)
        assertEquals("12", windows.view!!.findViewWithTag<TextView>("live_counter_value").text.toString())
    }

    @Test fun newCountUpdatesTheExistingBadgeInsteadOfAddingAnotherWindow() {
        val windows = Windows()
        val overlay = LiveCounterOverlay(RuntimeEnvironment.getApplication(), windows.manager)
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12))
        val firstView = windows.view
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 13))
        assertEquals(1, windows.adds)
        assertSame(firstView, windows.view)
        assertEquals("13", windows.view!!.findViewWithTag<TextView>("live_counter_value").text.toString())
    }

    @Test fun switchingPlatformsChangesBothLabelAndTotal() {
        val windows = Windows()
        val overlay = LiveCounterOverlay(RuntimeEnvironment.getApplication(), windows.manager)
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12))
        overlay.show(LiveCounterState(FeedKind.INSTAGRAM_REELS, 4))
        assertEquals("REELS TODAY", windows.view!!.findViewWithTag<TextView>("live_counter_label").text.toString())
        assertEquals("4", windows.view!!.findViewWithTag<TextView>("live_counter_value").text.toString())
        assertEquals(1, windows.adds)
    }

    @Test fun hidingRepeatedlyRemovesOnlyTheAttachedWindowAndAllowsReuse() {
        val windows = Windows()
        val overlay = LiveCounterOverlay(RuntimeEnvironment.getApplication(), windows.manager)
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12))
        overlay.hide()
        overlay.hide()
        assertEquals(1, windows.removes)
        assertNull(overlay.shown)
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 13))
        assertEquals(2, windows.adds)
    }

    @Test fun anOemWindowRejectionDoesNotCrashTheServiceCaller() {
        val windows = Windows().apply { reject = true }
        val overlay = LiveCounterOverlay(RuntimeEnvironment.getApplication(), windows.manager)
        assertFalse(overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12)))
        overlay.hide()
        assertEquals(0, windows.removes)
    }

    @Test fun incrementAnimationEndsAtOriginalSizeAndShowsSharedAllowance() {
        val windows = Windows()
        val overlay = LiveCounterOverlay(RuntimeEnvironment.getApplication(), windows.manager)
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 12, 16))
        overlay.show(LiveCounterState(FeedKind.YOUTUBE_SHORTS, 13, 17))
        Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        val value = windows.view!!.findViewWithTag<TextView>("live_counter_value")
        assertEquals("13", value.text.toString())
        assertEquals(1f, value.scaleX, .001f)
        assertEquals(0f, value.translationY, .001f)
        assertEquals("17 / 100 daily", windows.view!!.findViewWithTag<TextView>("live_counter_quota").text.toString())
        overlay.hide()
        assertEquals(1f, value.scaleY, .001f)
    }
}
