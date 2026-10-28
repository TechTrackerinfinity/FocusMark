package com.focuslock.app.ui

import com.focuslock.app.data.FocusRule
import com.focuslock.app.data.FocusSchedule
import com.focuslock.app.data.PausePhase
import com.focuslock.app.enforcement.FeedKind
import java.util.Locale

enum class FocusDestination(val title: String) { FOCUS("Focus"), ACTIVITY("Activity"), SETTINGS("Settings") }
enum class FocusAction { CONNECT, CHOOSE_APPS, SCHEDULE, START, PAUSE, CANCEL_PAUSE, RESUME }
enum class FocusStatus(val label: String) { SETUP("SETUP"), EMPTY("YOUR PLAN"), PAUSED("READY"), ACTIVE("ACTIVE NOW"), SCHEDULED("SCHEDULED"), WAITING("20-MIN WAIT"), BREAK("10-MIN BREAK") }

data class FocusSummary(
    val status: FocusStatus,
    val title: String,
    val detail: String,
    val actionLabel: String,
    val action: FocusAction,
)

enum class FeedControl(val kind: FeedKind, val counter: Boolean) {
    COUNT_REELS(FeedKind.INSTAGRAM_REELS, true),
    COUNT_SHORTS(FeedKind.YOUTUBE_SHORTS, true),
    BLOCK_REELS(FeedKind.INSTAGRAM_REELS, false),
    BLOCK_SHORTS(FeedKind.YOUTUBE_SHORTS, false),
}

fun focusSummary(rule: FocusRule, connected: Boolean, minute: Int, installed: Set<FeedKind> = FeedKind.entries.toSet()): FocusSummary {
    val hasRules = rule.blockedPackages.isNotEmpty() ||
        (rule.blockInstagramReels && FeedKind.INSTAGRAM_REELS in installed) ||
        (rule.blockYouTubeShorts && FeedKind.YOUTUBE_SHORTS in installed)
    return when {
        !hasRules -> FocusSummary(FocusStatus.EMPTY, "Make room for focus", "Choose the apps or feeds you want a break from.", "Choose apps to block", FocusAction.CHOOSE_APPS)
        !connected -> FocusSummary(FocusStatus.SETUP, "One step to get ready", "Connect protection to start using your daily focus plan.", "Connect protection", FocusAction.CONNECT)
        rule.pausePhase == PausePhase.WAITING -> FocusSummary(FocusStatus.WAITING, "Your break is queued", "Blocking stays on. Your 10-minute break starts in " + formatCountdown(rule.pauseWaitRemainingMs) + ".", "Cancel pause request", FocusAction.CANCEL_PAUSE)
        rule.pausePhase == PausePhase.BREAK -> FocusSummary(FocusStatus.BREAK, "A short break", "Blocking resumes automatically in " + formatCountdown(rule.pauseRemainingMs) + ". The daily 100-video limit still applies.", "Resume blocking now", FocusAction.RESUME)
        rule.startMinute == rule.endMinute -> FocusSummary(FocusStatus.SETUP, "Choose your focus hours", "Start and end times need to be different.", "Set your schedule", FocusAction.SCHEDULE)
        !rule.enabled -> FocusSummary(FocusStatus.PAUSED, "Start on your terms", "Your plan is saved. Turn it on for your daily schedule.", "Turn on focus", FocusAction.START)
        FocusSchedule.contains(if (rule.effectiveMinute >= 0) rule.effectiveMinute else minute, rule.startMinute, rule.endMinute) -> FocusSummary(FocusStatus.ACTIVE, "Your focus is protected", "A pause needs a 20-minute wait, then lasts 10 minutes.", "Request a break", FocusAction.PAUSE)
        else -> FocusSummary(FocusStatus.SCHEDULED, "Your plan is ready", "Blocking follows your ${formatFocusTime(rule.startMinute)}–${formatFocusTime(rule.endMinute)} daily window. Breaks require a 20-minute wait.", "Request a break", FocusAction.PAUSE)
    }
}

fun formatFocusTime(minute: Int): String = String.format(Locale.getDefault(), "%02d:%02d", minute / 60, minute % 60)

fun formatCountdown(millis: Long): String {
    val seconds = (millis.coerceAtLeast(0) + 999) / 1000
    return String.format(Locale.getDefault(), "%02d:%02d", seconds / 60, seconds % 60)
}

fun FeedControl.isEnabled(rule: FocusRule): Boolean = when (this) {
    FeedControl.COUNT_REELS -> rule.countInstagramReels
    FeedControl.COUNT_SHORTS -> rule.countYouTubeShorts
    FeedControl.BLOCK_REELS -> rule.blockInstagramReels
    FeedControl.BLOCK_SHORTS -> rule.blockYouTubeShorts
}
