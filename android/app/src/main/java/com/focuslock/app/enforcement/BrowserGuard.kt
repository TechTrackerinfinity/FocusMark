package com.focuslock.app.enforcement

import com.focuslock.app.data.FocusRule
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale

data class GuardedWebPage(val label: String)

object BrowserGuard {
    val packages = setOf(
        "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.brave.browser",
        "com.microsoft.emmx", "org.mozilla.firefox", "org.mozilla.fenix",
        "com.sec.android.app.sbrowser", "com.mi.globalbrowser", "com.opera.browser",
    )
    val addressIds = listOf("url_bar", "location_bar_edit_text", "mozac_browser_toolbar_url_view", "url_bar_title", "url")

    /** Only a visible address label is supplied. No browsing history or page text is retained. */
    fun blockedPage(rule: FocusRule, address: String): GuardedWebPage? {
        val text = address.trim()
        val uri = runCatching { URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        val youtube = host == "youtube.com" || host.endsWith(".youtube.com") || host == "youtu.be"
        val instagram = host == "instagram.com" || host.endsWith(".instagram.com")
        if (!youtube && !instagram) return null
        val path = runCatching { URLDecoder.decode(uri.rawPath.orEmpty(), StandardCharsets.UTF_8.name()) }
            .getOrDefault(uri.path.orEmpty()).lowercase(Locale.ROOT)
        val feed = if (youtube) path == "/shorts" || path.startsWith("/shorts/")
            else path == "/reels" || path.startsWith("/reels/") || path == "/reel" || path.startsWith("/reel/")
        val nativePackage = if (youtube) FeedKind.YOUTUBE_SHORTS.packageName else FeedKind.INSTAGRAM_REELS.packageName
        val protectedWeb = rule.budgetTrackingEnabled && (rule.rulesLocked || rule.dailyLimitReached || feed)
        val wholeApp = rule.blockingActive && nativePackage in rule.blockedPackages
        return if (protectedWeb || wholeApp) GuardedWebPage(if (youtube) "YouTube website" else "Instagram website") else null
    }
}
