package com.focuslock.app.data

import org.junit.Assert.*
import org.junit.Test

class FocusScheduleTest {
    @Test fun daytimeBoundariesAreCorrect() {
        assertFalse(FocusSchedule.contains(539, 540, 1380))
        assertTrue(FocusSchedule.contains(540, 540, 1380))
        assertTrue(FocusSchedule.contains(1379, 540, 1380))
        assertFalse(FocusSchedule.contains(1380, 540, 1380))
    }

    @Test fun overnightScheduleCoversBothSidesOfMidnight() {
        assertTrue(FocusSchedule.contains(1380, 1320, 360))
        assertTrue(FocusSchedule.contains(120, 1320, 360))
        assertFalse(FocusSchedule.contains(360, 1320, 360))
        assertFalse(FocusSchedule.contains(800, 1320, 360))
    }

    @Test fun equalTimesRemainInactive() {
        assertFalse(FocusSchedule.contains(600, 540, 540))
    }
}
