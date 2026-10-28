package com.focuslock.app.data

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class FocusRule(
    val enabled: Boolean,
    val startMinute: Int,
    val endMinute: Int,
    val blockedPackages: Set<String>,
    val countInstagramReels: Boolean,
    val countYouTubeShorts: Boolean,
    val blockInstagramReels: Boolean,
    val blockYouTubeShorts: Boolean,
    val instagramReelsToday: Int,
    val youtubeShortsToday: Int,
    val showLiveCounter: Boolean = true,
    val pausePhase: PausePhase = PausePhase.NONE,
    val pauseWaitRemainingMs: Long = 0L,
    val pauseRemainingMs: Long = 0L,
    val rulesLocked: Boolean = false,
    val dailyShortsUsed: Int = 0,
    val effectiveMinute: Int = -1,
    val planZoneId: String = "",
    val clockVerified: Boolean = false,
    val budgetTrackingEnabled: Boolean = false,
    val policyDate: String = "",
) {
    val dailyLimitReached: Boolean get() = budgetTrackingEnabled && dailyShortsUsed >= StrictPolicy.DAILY_SHORT_LIMIT
    val blockingActive: Boolean get() = enabled && pausePhase != PausePhase.BREAK &&
        effectiveMinute >= 0 && FocusSchedule.contains(effectiveMinute, startMinute, endMinute)
}

/**
 * The policy boundary for every UI and service action. Critical changes use synchronous writes;
 * all instances share one lock so a UI save cannot race a service update or quota record.
 */
class FocusPreferences(context: Context, private val clock: FocusClock = AndroidFocusClock(context)) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val ownPackage = context.packageName

    fun rule(): FocusRule = synchronized(LOCK) { snapshot() }

    fun setEnabled(enabled: Boolean): Boolean = if (enabled) synchronized(LOCK) {
        snapshot()
        preferences.edit().putBoolean(KEY_ENABLED, true).clearPause().commit()
    } else requestPause() == PauseRequest.STARTED

    fun requestPause(): PauseRequest = synchronized(LOCK) {
        val rule = snapshot()
        when {
            !rule.enabled -> PauseRequest.PROTECTION_OFF
            rule.pausePhase == PausePhase.WAITING -> PauseRequest.ALREADY_WAITING
            rule.pausePhase == PausePhase.BREAK -> PauseRequest.ALREADY_ON_BREAK
            else -> {
                val elapsed = clock.read().elapsedMillis
                check(preferences.edit()
                    .putLong(KEY_PAUSE_READY, elapsed + StrictPolicy.PAUSE_WAIT_MILLIS)
                    .putLong(KEY_PAUSE_END, elapsed + StrictPolicy.PAUSE_WAIT_MILLIS + StrictPolicy.PAUSE_LENGTH_MILLIS)
                    .commit()) { "Could not save pause request" }
                PauseRequest.STARTED
            }
        }
    }

    fun cancelPause(): Boolean = synchronized(LOCK) {
        snapshot()
        preferences.edit().clearPause().commit()
    }

    fun setSchedule(startMinute: Int, endMinute: Int): Boolean = synchronized(LOCK) {
        val rule = snapshot()
        val start = startMinute.coerceIn(0, 1439)
        val end = endMinute.coerceIn(0, 1439)
        if (rule.startMinute == start && rule.endMinute == end) return@synchronized true
        if (rule.rulesLocked || start == end) return@synchronized false
        preferences.edit().putInt(KEY_START_MINUTE, start).putInt(KEY_END_MINUTE, end).commit()
    }

    fun setBlockedPackages(packages: Set<String>): Boolean = synchronized(LOCK) {
        val rule = snapshot()
        val selection = packages - ownPackage
        if (selection == rule.blockedPackages) return@synchronized true
        if (rule.rulesLocked) return@synchronized false
        preferences.edit().putStringSet(KEY_BLOCKED_PACKAGES, selection.toSet()).commit()
    }

    fun setBlockInstagramReels(enabled: Boolean): Boolean = setProtectedBoolean(KEY_BLOCK_INSTAGRAM_REELS, enabled)
    fun setBlockYouTubeShorts(enabled: Boolean): Boolean = setProtectedBoolean(KEY_BLOCK_YOUTUBE_SHORTS, enabled)
    fun setCountInstagramReels(enabled: Boolean) = setCountingPreference(KEY_COUNT_INSTAGRAM_REELS, enabled)
    fun setCountYouTubeShorts(enabled: Boolean) = setCountingPreference(KEY_COUNT_YOUTUBE_SHORTS, enabled)
    fun setShowLiveCounter(enabled: Boolean) = setBoolean(KEY_SHOW_LIVE_COUNTER, enabled)
    fun grantContentConsent() = setBoolean(KEY_CONTENT_CONSENT, true)
    fun hasContentConsent(): Boolean = synchronized(LOCK) { consent() }

    /** Always consumes the fixed daily allowance, even if the user hides an individual counter. */
    fun recordFeedView(instagram: Boolean): FocusRule = synchronized(LOCK) {
        record(instagram, false)
        snapshot()
    }

    // Retained for existing callers; the hard allowance is still enforced.
    fun incrementInstagramReels() = synchronized(LOCK) { record(true, true) }
    fun incrementYouTubeShorts() = synchronized(LOCK) { record(false, true) }

    private fun record(instagram: Boolean, forceStats: Boolean) {
        val rule = snapshot()
        if (rule.dailyShortsUsed >= StrictPolicy.DAILY_SHORT_LIMIT) return
        val key = if (instagram) KEY_INSTAGRAM_REELS_COUNT else KEY_YOUTUBE_SHORTS_COUNT
        val showsStats = if (instagram) rule.countInstagramReels else rule.countYouTubeShorts
        val edit = preferences.edit().putInt(KEY_QUOTA_USED, rule.dailyShortsUsed + 1)
        if (rule.budgetTrackingEnabled || showsStats || forceStats)
            edit.putInt(key, preferences.getInt(key, 0).coerceAtLeast(0) + 1)
        check(edit.commit()) { "Could not save daily usage" }
    }

    fun isScheduleActiveNow(): Boolean = rule().blockingActive
    fun isBlockedNow(): Boolean = rule().let { it.blockingActive && it.blockedPackages.isNotEmpty() }
    fun isWithinSchedule(current: Int, start: Int, end: Int): Boolean = FocusSchedule.contains(current, start, end)

    fun registerChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.registerOnSharedPreferenceChangeListener(listener)
    fun unregisterChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) =
        preferences.unregisterOnSharedPreferenceChangeListener(listener)

    private fun setProtectedBoolean(key: String, value: Boolean): Boolean = synchronized(LOCK) {
        val rule = snapshot()
        if (preferences.getBoolean(key, false) == value) return@synchronized true
        if (rule.rulesLocked) return@synchronized false
        preferences.edit().putBoolean(key, value).apply {
            if (value) putBoolean(KEY_CONTENT_CONSENT, true)
        }.commit()
    }

    private fun setBoolean(key: String, value: Boolean): Boolean = synchronized(LOCK) {
        snapshot()
        preferences.edit().putBoolean(key, value).apply {
            if (value && key == KEY_CONTENT_CONSENT) {
                putBoolean(KEY_COUNT_INSTAGRAM_REELS, true)
                putBoolean(KEY_COUNT_YOUTUBE_SHORTS, true)
            }
        }.commit()
    }

    /** Legacy callers cannot turn off tracking once the fixed allowance is accepted. */
    private fun setCountingPreference(key: String, enabled: Boolean): Boolean = synchronized(LOCK) {
        val rule = snapshot()
        if (!enabled && rule.budgetTrackingEnabled) return@synchronized false
        preferences.edit().putBoolean(key, enabled).apply {
            if (enabled) {
                putBoolean(KEY_CONTENT_CONSENT, true)
                putBoolean(KEY_COUNT_INSTAGRAM_REELS, true)
                putBoolean(KEY_COUNT_YOUTUBE_SHORTS, true)
            }
        }.commit()
    }

    private fun snapshot(): FocusRule {
        val time = protectedTime()
        migrateStrictPolicy()
        normalizeDay(time.date)
        val enabled = preferences.getBoolean(KEY_ENABLED, false)
        val start = preferences.getInt(KEY_START_MINUTE, 540)
        val end = preferences.getInt(KEY_END_MINUTE, 1020)
        val packages = preferences.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()).orEmpty().toSet()
        val reels = preferences.getBoolean(KEY_BLOCK_INSTAGRAM_REELS, false)
        val shorts = preferences.getBoolean(KEY_BLOCK_YOUTUBE_SHORTS, false)
        val ready = preferences.getLong(KEY_PAUSE_READY, 0L)
        val pauseEnd = preferences.getLong(KEY_PAUSE_END, 0L)
        val phase = if (enabled) StrictPolicy.phase(time.elapsed, ready, pauseEnd) else PausePhase.NONE
        if (pauseEnd > 0 && time.elapsed >= pauseEnd) preferences.edit().clearPause().commit()
        val inWindow = FocusSchedule.contains(time.minute, start, end)
        val hasTargets = packages.isNotEmpty() || reels || shorts
        val mandatoryCounting = consent()
        return FocusRule(
            enabled, start, end, packages,
            mandatoryCounting || preferences.getBoolean(KEY_COUNT_INSTAGRAM_REELS, false),
            mandatoryCounting || preferences.getBoolean(KEY_COUNT_YOUTUBE_SHORTS, false),
            reels, shorts,
            preferences.getInt(KEY_INSTAGRAM_REELS_COUNT, 0).coerceAtLeast(0),
            preferences.getInt(KEY_YOUTUBE_SHORTS_COUNT, 0).coerceAtLeast(0),
            preferences.getBoolean(KEY_SHOW_LIVE_COUNTER, true),
            phase,
            if (phase == PausePhase.WAITING) (ready - time.elapsed).coerceAtLeast(0) else 0,
            if (phase == PausePhase.BREAK) (pauseEnd - time.elapsed).coerceAtLeast(0) else 0,
            enabled && hasTargets && (inWindow || phase != PausePhase.NONE),
            preferences.getInt(KEY_QUOTA_USED, 0).coerceIn(0, StrictPolicy.DAILY_SHORT_LIMIT),
            time.minute, time.zoneId, time.verified, consent(), time.date.toString(),
        )
    }

    private fun migrateStrictPolicy() {
        if (preferences.getInt(KEY_POLICY_VERSION, 0) >= 1) return
        val legacyPlan = preferences.contains(KEY_ENABLED) &&
            (preferences.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()).orEmpty().isNotEmpty() ||
                preferences.getBoolean(KEY_BLOCK_INSTAGRAM_REELS, false) ||
                preferences.getBoolean(KEY_BLOCK_YOUTUBE_SHORTS, false))
        val edit = preferences.edit().putInt(KEY_POLICY_VERSION, 1)
        if (legacyPlan) edit.putBoolean(KEY_ENABLED, true).clearPause()
        if (consent()) edit.putBoolean(KEY_CONTENT_CONSENT, true)
        check(edit.commit()) { "Could not initialize strict policy" }
    }

    private fun normalizeDay(day: LocalDate) {
        val date = day.toString()
        val previous = preferences.getString(KEY_QUOTA_DAY, null)
        if (previous == date || (previous != null && previous > date)) return
        val legacyCode = day.year * 1000 + day.dayOfYear
        val importLegacy = previous == null && preferences.getInt(KEY_COUNT_DAY, -1) == legacyCode
        val reels = if (importLegacy) preferences.getInt(KEY_INSTAGRAM_REELS_COUNT, 0).coerceAtLeast(0) else 0
        val shorts = if (importLegacy) preferences.getInt(KEY_YOUTUBE_SHORTS_COUNT, 0).coerceAtLeast(0) else 0
        check(preferences.edit().putString(KEY_QUOTA_DAY, date)
            .putInt(KEY_QUOTA_USED, (reels.toLong() + shorts).coerceAtMost(StrictPolicy.DAILY_SHORT_LIMIT.toLong()).toInt())
            .putInt(KEY_COUNT_DAY, legacyCode)
            .putInt(KEY_INSTAGRAM_REELS_COUNT, reels).putInt(KEY_YOUTUBE_SHORTS_COUNT, shorts).commit()) {
            "Could not save new allowance day"
        }
    }

    private data class PolicyTime(val elapsed: Long, val date: LocalDate, val minute: Int, val zoneId: String, val verified: Boolean)

    private fun protectedTime(): PolicyTime {
        val reading = clock.read()
        val initialized = preferences.contains(KEY_TIME_BASE_WALL)
        val storedBoot = preferences.getInt(KEY_TIME_BOOT, Int.MIN_VALUE)
        val baseElapsed = preferences.getLong(KEY_TIME_BASE_ELAPSED, reading.elapsedMillis)
        val reboot = initialized && (storedBoot != reading.bootCount || reading.elapsedMillis < baseElapsed)
        var verified = preferences.getBoolean(KEY_TIME_VERIFIED, false)
        var zone = preferences.getString(KEY_TIME_ZONE, reading.zoneId) ?: reading.zoneId
        var effective: Long
        val edit = preferences.edit()
        var save = false

        if (!initialized || reboot) {
            val network = clock.networkTimeMillis()
            val checkpoint = preferences.getLong(KEY_TIME_CHECKPOINT, reading.wallMillis)
            effective = if (!initialized) network ?: reading.wallMillis
                else maxOf(checkpoint, network ?: checkpoint + reading.elapsedMillis)
            verified = network != null
            if (!initialized) zone = reading.zoneId
            edit.putLong(KEY_TIME_BASE_WALL, effective).putLong(KEY_TIME_BASE_ELAPSED, reading.elapsedMillis)
                .putInt(KEY_TIME_BOOT, reading.bootCount).putString(KEY_TIME_ZONE, zone)
                .putBoolean(KEY_TIME_VERIFIED, verified).putLong(KEY_TIME_VERIFY_AFTER, reading.elapsedMillis + 60_000)
            if (reboot) edit.clearPause()
            save = true
        } else {
            effective = preferences.getLong(KEY_TIME_BASE_WALL, reading.wallMillis) + (reading.elapsedMillis - baseElapsed).coerceAtLeast(0)
            if (!verified && reading.elapsedMillis >= preferences.getLong(KEY_TIME_VERIFY_AFTER, 0)) {
                val network = clock.networkTimeMillis()
                if (network != null) {
                    effective = maxOf(effective, network)
                    verified = true
                    edit.putLong(KEY_TIME_BASE_WALL, effective).putLong(KEY_TIME_BASE_ELAPSED, reading.elapsedMillis)
                        .putBoolean(KEY_TIME_VERIFIED, true)
                }
                edit.putLong(KEY_TIME_VERIFY_AFTER, reading.elapsedMillis + 60_000)
                save = true
            }
        }
        if (save || effective - preferences.getLong(KEY_TIME_CHECKPOINT, 0) >= 30_000) {
            edit.putLong(KEY_TIME_CHECKPOINT, effective)
            check(edit.commit()) { "Could not checkpoint policy time" }
        }
        val zoned = Instant.ofEpochMilli(effective).atZone(runCatching { ZoneId.of(zone) }.getOrDefault(ZoneId.of("UTC")))
        return PolicyTime(reading.elapsedMillis, zoned.toLocalDate(), zoned.hour * 60 + zoned.minute, zone, verified)
    }

    private fun consent(): Boolean = preferences.getBoolean(KEY_CONTENT_CONSENT, false) ||
        listOf(KEY_COUNT_INSTAGRAM_REELS, KEY_COUNT_YOUTUBE_SHORTS, KEY_BLOCK_INSTAGRAM_REELS, KEY_BLOCK_YOUTUBE_SHORTS)
            .any { preferences.getBoolean(it, false) }

    private fun SharedPreferences.Editor.clearPause(): SharedPreferences.Editor =
        remove(KEY_PAUSE_READY).remove(KEY_PAUSE_END)

    private companion object {
        val LOCK = Any()
        const val FILE_NAME = "focus_lock_rules"
        const val KEY_ENABLED = "enabled"
        const val KEY_START_MINUTE = "start_minute"
        const val KEY_END_MINUTE = "end_minute"
        const val KEY_BLOCKED_PACKAGES = "blocked_packages"
        const val KEY_COUNT_INSTAGRAM_REELS = "count_instagram_reels"
        const val KEY_COUNT_YOUTUBE_SHORTS = "count_youtube_shorts"
        const val KEY_BLOCK_INSTAGRAM_REELS = "block_instagram_reels"
        const val KEY_BLOCK_YOUTUBE_SHORTS = "block_youtube_shorts"
        const val KEY_CONTENT_CONSENT = "content_consent_v1"
        const val KEY_SHOW_LIVE_COUNTER = "show_live_counter"
        const val KEY_COUNT_DAY = "content_count_day"
        const val KEY_INSTAGRAM_REELS_COUNT = "instagram_reels_count"
        const val KEY_YOUTUBE_SHORTS_COUNT = "youtube_shorts_count"
        const val KEY_POLICY_VERSION = "strict_policy_version"
        const val KEY_PAUSE_READY = "pause_ready_elapsed"
        const val KEY_PAUSE_END = "pause_end_elapsed"
        const val KEY_QUOTA_DAY = "quota_day"
        const val KEY_QUOTA_USED = "quota_used"
        const val KEY_TIME_BASE_WALL = "policy_time_base_wall"
        const val KEY_TIME_BASE_ELAPSED = "policy_time_base_elapsed"
        const val KEY_TIME_CHECKPOINT = "policy_time_checkpoint"
        const val KEY_TIME_BOOT = "policy_time_boot"
        const val KEY_TIME_ZONE = "policy_time_zone"
        const val KEY_TIME_VERIFIED = "policy_time_verified"
        const val KEY_TIME_VERIFY_AFTER = "policy_time_verify_after"
    }
}
