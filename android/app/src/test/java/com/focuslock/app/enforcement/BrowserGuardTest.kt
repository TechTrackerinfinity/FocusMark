package com.focuslock.app.enforcement

import com.focuslock.app.data.FocusRule
import com.focuslock.app.data.PausePhase
import org.junit.Assert.*
import org.junit.Test

class BrowserGuardTest {
    private fun rule() = FocusRule(true, 540, 1380, emptySet(), false, false, true, true, 0, 0,
        rulesLocked = true, effectiveMinute = 800, budgetTrackingEnabled = true)

    @Test fun protectedWebDomainsAreGuardedIncludingDuringBreak() {
        for (r in listOf(rule(), rule().copy(pausePhase = PausePhase.WAITING), rule().copy(pausePhase = PausePhase.BREAK))) {
            assertNotNull(BrowserGuard.blockedPage(r, "m.youtube.com/watch?v=123"))
            assertNotNull(BrowserGuard.blockedPage(r, "https://www.instagram.com/"))
        }
    }
    @Test fun shortVideoPathsCannotAvoidQuotaOutsideScheduledHours() {
        val r = rule().copy(rulesLocked = false, effectiveMinute = 1400)
        assertNotNull(BrowserGuard.blockedPage(r, "youtube.com/shorts/123"))
        assertNotNull(BrowserGuard.blockedPage(r, "https://instagram.com/%72eel/123"))
        assertNotNull(BrowserGuard.blockedPage(r, "https://instagram.com/reels/"))
        assertNull(BrowserGuard.blockedPage(r, "youtube.com/watch?v=123"))
        assertNull(BrowserGuard.blockedPage(r, "instagram.com/accounts/"))
    }
    @Test fun exhaustedQuotaGuardsWholePlatformDomains() {
        val r = rule().copy(rulesLocked = false, effectiveMinute = 1400, dailyShortsUsed = 100)
        assertNotNull(BrowserGuard.blockedPage(r, "youtube.com"))
        assertNotNull(BrowserGuard.blockedPage(r, "youtu.be/123"))
    }
    @Test fun lookalikeHostsSearchTextAndOtherSitesAreNotBlocked() {
        for (url in listOf("youtube.com.example.org/shorts/1", "https://youtube.com@evil.com/shorts/1",
            "google.com/search?q=youtube.com", "https://notinstagram.com/reels", "Some page title"))
            assertNull(url, BrowserGuard.blockedPage(rule(), url))
    }
    @Test fun usersWithoutConsentAreNotSubjectToContentInspectionPolicy() {
        assertNull(BrowserGuard.blockedPage(rule().copy(budgetTrackingEnabled = false), "youtube.com/shorts/1"))
    }
}
