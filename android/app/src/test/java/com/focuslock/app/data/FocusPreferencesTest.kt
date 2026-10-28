package com.focuslock.app.data

import android.content.Context
import java.time.Instant
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

internal class FakeFocusClock : FocusClock {
    var wall = Instant.parse("2026-10-25T12:00:00Z").toEpochMilli()
    var elapsed = 1_000_000L
    var boot = 1
    var zone = "UTC"
    var network: Long? = wall
    override fun read() = FocusClockReading(wall, elapsed, boot, zone)
    override fun networkTimeMillis() = network
    fun advance(ms: Long) { wall += ms; elapsed += ms; network = network?.plus(ms) }
    fun reboot() { boot++; elapsed = 1_000L }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], manifest = Config.NONE)
class FocusPreferencesTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()
    private lateinit var clock: FakeFocusClock
    private fun prefs() = FocusPreferences(context, clock)
    private val raw get() = context.getSharedPreferences("focus_lock_rules", Context.MODE_PRIVATE)
    private fun armed(): FocusPreferences = prefs().apply {
        setSchedule(540, 1380); setBlockYouTubeShorts(true)
        setBlockedPackages(setOf("example.app")); setEnabled(true)
    }
    @Before fun clearRules() { raw.edit().clear().commit(); clock = FakeFocusClock() }

    @Test fun freshInstallIsNotArmed() {
        assertFalse(prefs().rule().enabled)
        assertEquals(PauseRequest.PROTECTION_OFF, prefs().requestPause())
    }
    @Test fun waitsTwentyMinutesThenAllowsExactlyTenAndResumes() {
        val p = armed()
        assertEquals(PauseRequest.STARTED, p.requestPause())
        assertEquals(1_200_000L, p.rule().pauseWaitRemainingMs)
        clock.advance(1_199_999)
        assertEquals(PausePhase.WAITING, p.rule().pausePhase); assertTrue(p.rule().blockingActive)
        clock.advance(1)
        assertEquals(PausePhase.BREAK, p.rule().pausePhase)
        assertEquals(600_000L, p.rule().pauseRemainingMs); assertFalse(p.rule().blockingActive)
        clock.advance(599_999); assertFalse(p.rule().blockingActive)
        clock.advance(1)
        assertEquals(PausePhase.NONE, p.rule().pausePhase); assertTrue(p.rule().blockingActive)
        assertTrue(p.rule().enabled)
    }
    @Test fun reopeningAndRepeatedRequestsCannotExtendTimers() {
        armed().requestPause(); clock.advance(300_000)
        assertEquals(PauseRequest.ALREADY_WAITING, prefs().requestPause())
        assertEquals(900_000L, prefs().rule().pauseWaitRemainingMs)
        clock.advance(1_000_000)
        assertEquals(PauseRequest.ALREADY_ON_BREAK, prefs().requestPause())
        assertEquals(500_000L, prefs().rule().pauseRemainingMs)
        clock.advance(600_000)
        assertEquals(PausePhase.NONE, prefs().rule().pausePhase)
        assertTrue(prefs().rule().blockingActive)
    }
    @Test fun oldDisableCallRequestsWaitInsteadOfTurningOff() {
        val p = armed(); p.setEnabled(false)
        assertTrue(p.rule().enabled); assertTrue(p.rule().blockingActive)
        assertEquals(PausePhase.WAITING, p.rule().pausePhase)
    }
    @Test fun cancellationAndEarlyResumeRestoreProtection() {
        val p = armed(); p.requestPause(); p.cancelPause()
        assertEquals(PausePhase.NONE, p.rule().pausePhase); assertTrue(p.rule().blockingActive)
        p.requestPause(); clock.advance(1_200_000); p.cancelPause()
        assertTrue(p.rule().blockingActive); assertEquals(PausePhase.NONE, p.rule().pausePhase)
    }
    private fun assertChangesDenied(p: FocusPreferences) {
        assertTrue(p.rule().rulesLocked)
        assertFalse(p.setSchedule(0, 1)); assertFalse(p.setBlockedPackages(emptySet()))
        assertFalse(p.setBlockYouTubeShorts(false)); assertFalse(p.setBlockInstagramReels(true))
        assertEquals(setOf("example.app"), p.rule().blockedPackages)
        assertEquals(540, p.rule().startMinute); assertTrue(p.rule().blockYouTubeShorts)
    }
    @Test fun allRuleMutationsGuardedDuringFocusWaitingAndBreak() {
        val p = armed(); assertChangesDenied(p)
        p.requestPause(); assertChangesDenied(p)
        clock.advance(1_200_000); assertChangesDenied(prefs())
        clock.advance(600_000); assertChangesDenied(prefs())
    }
    @Test fun staleEditorCannotReplacePlanAfterItStarts() {
        val editor = prefs(); editor.rule(); armed()
        assertFalse(editor.setBlockedPackages(emptySet())); assertFalse(editor.setSchedule(0, 1))
    }
    @Test fun outsideScheduleCanConfigureNextPlanAndExcludesOwnPackage() {
        val p = armed(); clock.advance(11 * 3_600_000L)
        assertFalse(p.rule().rulesLocked)
        assertTrue(p.setBlockedPackages(setOf("next.app", context.packageName)))
        assertEquals(setOf("next.app"), p.rule().blockedPackages)
        assertFalse(p.setSchedule(1, 1)); assertTrue(p.setSchedule(600, 1000))
    }
    @Test fun rebootCancelsWaitAndResumesProtection() {
        val p = armed(); p.requestPause(); clock.advance(300_000); clock.reboot()
        assertEquals(PausePhase.NONE, prefs().rule().pausePhase); assertTrue(prefs().rule().blockingActive)
    }
    @Test fun rebootCancelsBreakAndResumesProtection() {
        val p = armed(); p.requestPause(); clock.advance(1_300_000)
        assertFalse(p.rule().blockingActive); clock.reboot()
        assertEquals(PausePhase.NONE, prefs().rule().pausePhase); assertTrue(prefs().rule().blockingActive)
    }
    @Test fun clockAndZoneChangesCannotSkipWaitOrResetUsage() {
        val p = armed(); p.recordFeedView(false); p.requestPause()
        clock.wall += 10 * 86_400_000L; clock.zone = "Pacific/Kiritimati"
        assertEquals(1_200_000L, p.rule().pauseWaitRemainingMs)
        assertEquals(1, p.rule().dailyShortsUsed); assertEquals("UTC", p.rule().planZoneId)
        assertEquals("2026-10-25", p.rule().policyDate)
        clock.wall -= 20 * 86_400_000L; clock.advance(1_200_000)
        assertEquals(PausePhase.BREAK, p.rule().pausePhase)
    }
    @Test fun offlineRebootIgnoresForwardWallClockJump() {
        val p = armed(); p.recordFeedView(false); p.requestPause()
        clock.network = null; clock.wall += 5 * 86_400_000L; clock.reboot()
        val r = prefs().rule()
        assertEquals("2026-10-25", r.policyDate); assertEquals(1, r.dailyShortsUsed)
        assertTrue(r.blockingActive); assertFalse(r.clockVerified)
    }
    @Test fun stoppingCountersIsRejectedAndHiddenBadgeDoesNotAffectAllowance() {
        val p = armed()
        assertFalse(p.setCountYouTubeShorts(false)); assertFalse(p.setCountInstagramReels(false))
        p.setShowLiveCounter(false)
        repeat(70) { p.recordFeedView(false) }; repeat(40) { prefs().recordFeedView(true) }
        assertEquals(100, p.rule().dailyShortsUsed); assertTrue(p.rule().dailyLimitReached)
        assertEquals(70, p.rule().youtubeShortsToday); assertEquals(30, p.rule().instagramReelsToday)
        assertTrue(p.rule().budgetTrackingEnabled)
    }
    @Test fun allowanceStillAppliesDuringBreakAndAfterResume() {
        val p = armed(); repeat(99) { p.recordFeedView(false) }
        p.requestPause(); clock.advance(1_200_000)
        assertEquals(PausePhase.BREAK, p.rule().pausePhase)
        assertTrue(p.recordFeedView(true).dailyLimitReached); p.cancelPause()
        assertEquals(100, p.rule().dailyShortsUsed)
    }
    @Test fun nextProtectedDayResetsBothCountersAndAllowance() {
        val p = prefs(); p.setCountInstagramReels(true); p.setCountYouTubeShorts(true)
        p.recordFeedView(true); p.recordFeedView(false); clock.advance(12 * 3_600_000L)
        assertEquals("2026-10-26", p.rule().policyDate); assertEquals(0, p.rule().dailyShortsUsed)
        assertEquals(0, p.rule().instagramReelsToday); assertEquals(0, p.rule().youtubeShortsToday)
        assertEquals(1, p.recordFeedView(false).youtubeShortsToday)
    }
    @Test fun countersAndQuotaPersistAcrossInstances() {
        val p = prefs(); p.setCountInstagramReels(true); p.setCountYouTubeShorts(true)
        p.recordFeedView(true); prefs().recordFeedView(false); p.recordFeedView(false)
        assertEquals(1, prefs().rule().instagramReelsToday); assertEquals(2, prefs().rule().youtubeShortsToday)
        assertEquals(3, prefs().rule().dailyShortsUsed)
    }
    @Test fun upgradeImportsUsageAndArmsPreviouslyPausedLegacyPlan() {
        raw.edit().putBoolean("enabled", false).putBoolean("block_youtube_shorts", true)
            .putBoolean("count_youtube_shorts", true).putInt("content_count_day", 2026298)
            .putInt("youtube_shorts_count", 29).putInt("instagram_reels_count", 4).commit()
        val r = prefs().rule()
        assertTrue(r.enabled); assertEquals(33, r.dailyShortsUsed); assertEquals(29, r.youtubeShortsToday)
        assertTrue(r.budgetTrackingEnabled); prefs().setCountYouTubeShorts(false)
        assertTrue(prefs().rule().budgetTrackingEnabled)
    }
    @Test fun hidingBadgeKeepsBothCountersEnabledAndRecording() {
        val p = armed(); p.setShowLiveCounter(false)
        assertTrue(prefs().hasContentConsent()); assertFalse(prefs().rule().showLiveCounter)
        assertTrue(prefs().rule().countYouTubeShorts); assertTrue(prefs().rule().countInstagramReels)
        prefs().recordFeedView(false); prefs().recordFeedView(true)
        assertEquals(1, prefs().rule().youtubeShortsToday); assertEquals(1, prefs().rule().instagramReelsToday)
        assertEquals(2, prefs().rule().dailyShortsUsed)
    }
    @Test fun upgradeIgnoresOldDisabledCounterFlagsWithoutResettingQuotaOrWait() {
        val p = armed(); p.recordFeedView(false); p.requestPause()
        raw.edit().putBoolean("count_youtube_shorts", false).putBoolean("count_instagram_reels", false).commit()
        val restored = prefs()
        assertTrue(restored.rule().countYouTubeShorts); assertTrue(restored.rule().countInstagramReels)
        assertEquals(PausePhase.WAITING, restored.rule().pausePhase)
        assertEquals(1, restored.rule().dailyShortsUsed)
        restored.recordFeedView(true)
        assertEquals(1, restored.rule().instagramReelsToday); assertEquals(2, restored.rule().dailyShortsUsed)
    }
    @Test fun acceptingRecognitionEnablesBothCountersTogether() {
        val p = prefs()
        assertFalse(p.rule().countYouTubeShorts); assertFalse(p.rule().countInstagramReels)
        p.grantContentConsent()
        assertTrue(prefs().rule().countYouTubeShorts); assertTrue(prefs().rule().countInstagramReels)
        assertFalse(p.setCountInstagramReels(false)); assertFalse(p.setCountYouTubeShorts(false))
    }
}
