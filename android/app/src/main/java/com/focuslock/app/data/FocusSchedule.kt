package com.focuslock.app.data

object FocusSchedule {
    /** End is exclusive; equal times keep the schedule inactive. */
    fun contains(current: Int, start: Int, end: Int): Boolean = when {
        start == end -> false
        start < end -> current in start until end
        else -> current >= start || current < end
    }
}
