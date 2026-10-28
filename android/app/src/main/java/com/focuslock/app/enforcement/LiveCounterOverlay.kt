package com.focuslock.app.enforcement

import android.content.Context
import android.animation.ValueAnimator
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.SystemClock
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.focuslock.app.R
import com.focuslock.app.data.FocusRule
import com.focuslock.app.data.PausePhase

data class LiveCounterState(val kind: FeedKind, val count: Int, val dailyUsed: Int? = null)

fun liveCounterState(rule: FocusRule, feed: FeedKind?, scheduleActive: Boolean): LiveCounterState? {
    if (!rule.showLiveCounter || feed == null || rule.dailyLimitReached) return null
    val blocking = rule.enabled && scheduleActive && rule.pausePhase != PausePhase.BREAK
    if (blocking && feed.packageName in rule.blockedPackages) return null
    return when (feed) {
        FeedKind.YOUTUBE_SHORTS -> if (!rule.countYouTubeShorts || (blocking && rule.blockYouTubeShorts)) null
            else LiveCounterState(feed, rule.youtubeShortsToday, rule.dailyShortsUsed.takeIf { rule.budgetTrackingEnabled })
        FeedKind.INSTAGRAM_REELS -> if (!rule.countInstagramReels || (blocking && rule.blockInstagramReels)) null
            else LiveCounterState(feed, rule.instagramReelsToday, rule.dailyShortsUsed.takeIf { rule.budgetTrackingEnabled })
    }
}

/** One small, non-interactive accessibility window. It never takes focus or consumes gestures. */
internal class LiveCounterOverlay(
    private val context: Context,
    private val windowManager: WindowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager,
) {
    private var badge: LinearLayout? = null
    private var valueView: TextView? = null
    private var labelView: TextView? = null
    private var quotaView: TextView? = null
    private var countAnimation: ValueAnimator? = null
    private var attached = false
    private var retryAfter = 0L
    var shown: LiveCounterState? = null
        private set

    fun show(state: LiveCounterState): Boolean {
        val root = badge ?: createBadge().also { badge = it }
        if (shown != state) {
            val increases = shown?.let { it.kind == state.kind && state.count > it.count } == true
            labelView?.text = if (state.kind == FeedKind.YOUTUBE_SHORTS) "SHORTS TODAY" else "REELS TODAY"
            valueView?.text = state.count.toString()
            quotaView?.text = state.dailyUsed?.let { "$it / 100 daily" }.orEmpty()
            quotaView?.visibility = if (state.dailyUsed == null) View.GONE else View.VISIBLE
            if (attached && increases) animateIncrease()
            shown = state
        }
        if (attached) return true
        if (SystemClock.uptimeMillis() < retryAfter) return false

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(12)
            y = dp(96)
            title = "FocusMark live counter"
        }
        return try {
            windowManager.addView(root, params)
            attached = true
            retryAfter = 0L
            true
        } catch (_: RuntimeException) {
            // A missing OEM overlay token must not stop counting or crash the service.
            attached = false
            retryAfter = SystemClock.uptimeMillis() + 5_000L
            false
        }
    }

    fun hide() {
        countAnimation?.cancel()
        countAnimation = null
        valueView?.apply { scaleX = 1f; scaleY = 1f; translationY = 0f }
        if (attached) {
            badge?.let { runCatching { windowManager.removeViewImmediate(it) } }
        }
        attached = false
        shown = null
        retryAfter = 0L
    }

    private fun animateIncrease() {
        countAnimation?.cancel()
        if (!ValueAnimator.areAnimatorsEnabled()) return
        countAnimation = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 360L
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                val pulse = kotlin.math.sin(fraction * Math.PI).toFloat()
                valueView?.apply {
                    scaleX = 1f + .18f * pulse
                    scaleY = 1f + .18f * pulse
                    translationY = -dp(3) * pulse
                }
            }
            start()
        }
    }

    private fun createBadge(): LinearLayout {
        val root = object : LinearLayout(context) {
            override fun requestSendAccessibilityEvent(child: View, event: AccessibilityEvent): Boolean = false
        }.apply {
            tag = "focusmark_live_counter"
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumWidth = dp(112)
            setPadding(dp(12), dp(8), dp(14), dp(8))
            background = GradientDrawable().apply {
                setColor(Color.rgb(24, 46, 33))
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), Color.argb(130, 218, 245, 164))
            }
            elevation = dp(6).toFloat()
            isClickable = false
            isFocusable = false
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_NONE
            setFilterTouchesWhenObscured(false)
        }
        val icon = ImageView(context).apply {
            setImageResource(R.drawable.ic_focus_mark)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        root.addView(icon, LinearLayout.LayoutParams(dp(28), dp(28)).apply { marginEnd = dp(10) })
        val text = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        labelView = TextView(context).apply {
            tag = "live_counter_label"
            setTextColor(Color.rgb(192, 213, 183))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isSingleLine = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        valueView = TextView(context).apply {
            tag = "live_counter_value"
            setTextColor(Color.rgb(218, 245, 164))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 23f)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isSingleLine = true
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        text.addView(labelView)
        text.addView(valueView)
        quotaView = TextView(context).apply {
            tag = "live_counter_quota"
            setTextColor(Color.rgb(192, 213, 183))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        text.addView(quotaView)
        root.addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        return root
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
}
