package com.focuslock.app.data

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import java.time.ZoneId

data class FocusClockReading(val wallMillis: Long, val elapsedMillis: Long, val bootCount: Int, val zoneId: String)

interface FocusClock {
    fun read(): FocusClockReading
    fun networkTimeMillis(): Long? = null
}

class AndroidFocusClock(private val context: Context) : FocusClock {
    override fun read() = FocusClockReading(
        System.currentTimeMillis(), SystemClock.elapsedRealtime(),
        Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, -1),
        ZoneId.systemDefault().id,
    )

    override fun networkTimeMillis(): Long? = if (Build.VERSION.SDK_INT >= 33) {
        runCatching { SystemClock.currentNetworkTimeClock().millis() }.getOrNull()
    } else null
}

enum class PausePhase { NONE, WAITING, BREAK }
enum class PauseRequest { STARTED, ALREADY_WAITING, ALREADY_ON_BREAK, PROTECTION_OFF }

object StrictPolicy {
    const val PAUSE_WAIT_MILLIS = 20 * 60_000L
    const val PAUSE_LENGTH_MILLIS = 10 * 60_000L
    const val DAILY_SHORT_LIMIT = 100

    fun phase(nowElapsed: Long, ready: Long, end: Long): PausePhase = when {
        ready <= 0 || end <= ready -> PausePhase.NONE
        nowElapsed < ready -> PausePhase.WAITING
        nowElapsed < end -> PausePhase.BREAK
        else -> PausePhase.NONE
    }
}
