package com.focuslock.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focuslock.app.BuildConfig
import com.focuslock.app.data.FocusRule
import com.focuslock.app.data.StrictPolicy
import com.focuslock.app.enforcement.BlockerRuntimeState
import com.focuslock.app.enforcement.FeedKind
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun FocusScreen(
    rule: FocusRule, summary: FocusSummary, runtime: BlockerRuntimeState, installed: Set<FeedKind>,
    selectedAppNames: List<String>, onPrimary: () -> Unit, onSchedule: () -> Unit, onApps: () -> Unit,
    onControl: (FeedControl, Boolean) -> Unit, onFeedOnly: (FeedKind) -> Unit,
    onActivity: () -> Unit, onSetup: () -> Unit,
) {
    FocusList {
        item("hero") { FocusHero(summary, onPrimary) }
        item("schedule") { ScheduleLink(rule, onSchedule) }
        item("allowance") { DailyAllowanceCard(rule, runtime.connected, onSetup) }
        item("blockers_heading") { SectionTitle("What to block", if (rule.rulesLocked) "Locked during focus, the waiting period and your break." else "Your choices follow the daily schedule.") }
        item("shorts") {
            BlockerTile(FeedKind.YOUTUBE_SHORTS, rule.blockYouTubeShorts, FeedKind.YOUTUBE_SHORTS in installed,
                FeedKind.YOUTUBE_SHORTS.packageName in rule.blockedPackages, rule.rulesLocked,
                { onControl(FeedControl.BLOCK_SHORTS, it) }, { onFeedOnly(FeedKind.YOUTUBE_SHORTS) })
        }
        item("reels") {
            BlockerTile(FeedKind.INSTAGRAM_REELS, rule.blockInstagramReels, FeedKind.INSTAGRAM_REELS in installed,
                FeedKind.INSTAGRAM_REELS.packageName in rule.blockedPackages, rule.rulesLocked,
                { onControl(FeedControl.BLOCK_REELS, it) }, { onFeedOnly(FeedKind.INSTAGRAM_REELS) })
        }
        item("apps") {
            SurfaceCard(onClick = if (rule.rulesLocked) null else onApps) {
                Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconTile(Icons.Rounded.Apps)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Block entire apps", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (rule.rulesLocked) rule.blockedPackages.size.toString() + " selected · Locked"
                            else if (rule.blockedPackages.isEmpty()) "Choose distracting apps"
                            else selectedAppNames.take(3).joinToString(", ").ifBlank { rule.blockedPackages.size.toString() + " apps selected" },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(if (rule.rulesLocked) Icons.Rounded.Lock else Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("today_heading") { SectionTitle("A little awareness", "Your tracked views today.") }
        item("today") { TodayStats(rule, installed, onActivity) }
        item("service") {
            TextButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
                Icon(if (runtime.connected) Icons.Rounded.CheckCircle else Icons.Rounded.Info, null, Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (runtime.connected) "Protection connected" else "Set up protection", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
private fun DailyAllowanceCard(rule: FocusRule, connected: Boolean, onSetup: () -> Unit) {
    SurfaceCard {
        Column(Modifier.padding(18.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Daily short-video allowance", style = MaterialTheme.typography.titleMedium)
                    Text("Shorts + Reels combined", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("${rule.dailyShortsUsed} / ${StrictPolicy.DAILY_SHORT_LIMIT}",
                    style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("daily_allowance"))
            }
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(progress = { rule.dailyShortsUsed.toFloat() / StrictPolicy.DAILY_SHORT_LIMIT },
                modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(10.dp))
            Text(if (rule.dailyLimitReached) "Today's allowance is used. Short videos unlock on the next day of your plan."
                else "The 100-view limit also applies during breaks and when display counters are hidden.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!rule.budgetTrackingEnabled) TextButton(onClick = onSetup) { Text("Set up the daily limit") }
            else if (!connected) {
                Spacer(Modifier.height(10.dp))
                Text("Tracking disconnected. The daily limit cannot be enforced until Accessibility reconnects.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = onSetup) { Text("Reconnect protection") }
            } else {
                Spacer(Modifier.height(8.dp))
                Text("Tracking connected · counting cannot be paused in FocusMark",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FocusHero(summary: FocusSummary, onPrimary: () -> Unit) {
    Surface(color = FocusMarkPalette.Lime, contentColor = FocusMarkPalette.Forest, shape = RoundedCornerShape(28.dp)) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Surface(color = FocusMarkPalette.Forest.copy(alpha = .09f), shape = RoundedCornerShape(50), contentColor = FocusMarkPalette.Forest) {
                    Row(Modifier.padding(horizontal = 11.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(6.dp).background(FocusMarkPalette.Forest, CircleShape))
                        Spacer(Modifier.width(7.dp))
                        Text(summary.status.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.weight(1f))
                Icon(if (summary.status == FocusStatus.ACTIVE) Icons.Rounded.VerifiedUser else Icons.Rounded.Bookmark,
                    null, Modifier.size(30.dp), tint = FocusMarkPalette.Forest.copy(alpha = .65f))
            }
            Spacer(Modifier.height(22.dp))
            Text(summary.title, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(summary.detail, style = MaterialTheme.typography.bodyMedium, color = FocusMarkPalette.Forest.copy(alpha = .8f))
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onPrimary, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("focus_primary_action"),
                colors = ButtonDefaults.buttonColors(containerColor = FocusMarkPalette.Forest, contentColor = Color.White),
                shape = RoundedCornerShape(16.dp),
            ) {
                Icon(when (summary.action) {
                    FocusAction.PAUSE -> Icons.Rounded.Pause
                    FocusAction.START -> Icons.Rounded.PlayArrow
                    FocusAction.CONNECT -> Icons.Rounded.Shield
                    FocusAction.CHOOSE_APPS -> Icons.Rounded.Add
                    FocusAction.SCHEDULE -> Icons.Rounded.Schedule
                    FocusAction.CANCEL_PAUSE -> Icons.Rounded.Close
                    FocusAction.RESUME -> Icons.Rounded.PlayArrow
                }, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(summary.actionLabel)
            }
        }
    }
}

@Composable
fun ActivityScreen(
    rule: FocusRule, runtime: BlockerRuntimeState, installed: Set<FeedKind>,
    onSetup: () -> Unit,
    onLiveCounterChange: (Boolean) -> Unit,
) {
    FocusList {
        item("title") {
            Column(Modifier.padding(top = 6.dp, bottom = 4.dp)) {
                Text("Your activity", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(4.dp))
                Text(runCatching { LocalDate.parse(rule.policyDate) }.getOrDefault(LocalDate.now()).format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.getDefault())),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item("allowance") { DailyAllowanceCard(rule, runtime.connected, onSetup) }
        item("stats") { TodayStats(rule, installed, large = true) }
        item("live_counter") {
            SurfaceCard {
                Row(
                    Modifier.fillMaxWidth().testTag("live_counter_toggle")
                        .toggleable(value = rule.showLiveCounter, role = Role.Switch, onValueChange = onLiveCounterChange)
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconTile(Icons.Rounded.Visibility)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Show floating counter", style = MaterialTheme.typography.titleMedium)
                        Text("Only hides the badge. Counting and the 100-video limit continue.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(checked = rule.showLiveCounter, onCheckedChange = null)
                }
            }
        }
        item("automatic_counting") {
            SurfaceCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text("Automatic counting", style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(if (rule.budgetTrackingEnabled) "Always on for Shorts and Reels. There is no in-app stop or reset control."
                        else "Accept feed recognition during protection setup to enable automatic counting.",
                        style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Both totals and the shared allowance keep increasing during breaks and with the floating badge hidden. The allowance resets on the next day of your plan.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (!runtime.connected) {
            item("setup") {
                SurfaceCard {
                    Column(Modifier.padding(20.dp)) {
                        Text("Connect to start counting", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(6.dp))
                        Text("Allow the protection service to recognize Shorts and Reels.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = onSetup) { Text("Set up protection"); Icon(Icons.Rounded.ChevronRight, null) }
                    }
                }
            }
        }
        item("info") {
            SurfaceCard {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.Insights, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Notice your habits", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("The fixed allowance counts Shorts and Reels together automatically, including during breaks. You can hide the floating badge; tracking and the limit stay on. Totals reset at the next plan day and stay on this device.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    runtime.lastDetectedFeed?.let {
                        Spacer(Modifier.height(12.dp))
                        Text("Last recognized: " + it.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    runtime.error?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    rule: FocusRule, runtime: BlockerRuntimeState, accessibilityAllowed: Boolean,
    onSetup: () -> Unit, onSchedule: () -> Unit,
) {
    FocusList {
        item("heading") { Text("Keep things simple.", Modifier.padding(vertical = 8.dp), style = MaterialTheme.typography.headlineMedium) }
        item("protection") {
            SurfaceCard {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconTile(Icons.Rounded.Shield)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Protection service", style = MaterialTheme.typography.titleMedium)
                            Text(if (runtime.connected) "Connected and ready" else if (accessibilityAllowed) "Allowed, but disconnected" else "Needs setup",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (runtime.connected) Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        if (accessibilityAllowed && !runtime.connected)
                            "Open Android Accessibility settings, then turn the FocusMark service off and on to reconnect."
                        else "Android Accessibility lets FocusMark recognize selected apps and the feeds you choose.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onSetup, modifier = Modifier.fillMaxWidth()) {
                        Text(if (runtime.connected) "Review access" else "Open setup")
                    }
                }
            }
        }
        item("schedule") { ScheduleLink(rule, onSchedule) }
        item("strict_policy") {
            SurfaceCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Strict plan rules", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("20-minute wait before a break · 10-minute break · automatic resume · 100 short videos per day combined.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Text("Blocking choices and hours stay locked during focus, waiting and breaks. Configure the next plan outside those periods.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("YouTube and Instagram web pages are guarded in supported browsers while the plan is locked. Use native apps during permitted breaks.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Plan time zone: " + rule.planZoneId.ifBlank { "This device" } + if (rule.clockVerified) " · network time checked" else " · protected elapsed time; network check pending", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("Restarting ends pending waits and active breaks. A device owner can still revoke Accessibility, clear app data or uninstall; managed-device setup is required to prevent those actions.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("steps") {
            SurfaceCard {
                Column(Modifier.padding(20.dp)) {
                    Text("Your plan in three steps", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(18.dp))
                    SetupStep("1", "Choose your blockers", "Pause entire apps or just Shorts and Reels.")
                    Spacer(Modifier.height(16.dp))
                    SetupStep("2", "Set your hours", "Choose a daily window that fits your routine.")
                    Spacer(Modifier.height(16.dp))
                    SetupStep("3", "Turn on focus", "Use the main button on the Focus tab.")
                }
            }
        }
        item("privacy") {
            SurfaceCard {
                Column(Modifier.padding(20.dp)) {
                    Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Private by default", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    Text("Your rules and daily totals stay on this phone. Protection checks visible feed labels and address labels in supported browsers. These labels are discarded after checking.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item("about") {
            Column(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Rounded.Bookmark, null, Modifier.size(26.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Text("FocusMark", style = MaterialTheme.typography.titleMedium)
                Text("Version " + BuildConfig.VERSION_NAME, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text("A little more intention. A little less scrolling.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BlockerTile(
    kind: FeedKind, checked: Boolean, installed: Boolean, wholeApp: Boolean, locked: Boolean,
    onChecked: (Boolean) -> Unit, onFeedOnly: () -> Unit,
) {
    SurfaceCard {
        Column {
            Row(
                Modifier.fillMaxWidth().toggleable(value = checked, enabled = installed && !locked, role = Role.Switch, onValueChange = onChecked).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlatformBadge(kind)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(kind.label, style = MaterialTheme.typography.titleMedium)
                    Text(if (!installed) "Not installed on this device" else if (locked) "Locked during this focus plan"
                        else if (kind == FeedKind.YOUTUBE_SHORTS) "Keep regular videos available" else "Keep posts and messages available",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(8.dp))
                Switch(checked = checked, onCheckedChange = null, enabled = installed && !locked, modifier = Modifier.testTag("block_" + kind.name))
            }
            if (wholeApp && installed) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(horizontal = 18.dp, vertical = 8.dp)) {
                    Text("Whole-app blocking is also selected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = onFeedOnly, enabled = !locked) { Text(if (kind == FeedKind.YOUTUBE_SHORTS) "Use Shorts blocking only" else "Use Reels blocking only") }
                }
            }
        }
    }
}

@Composable
private fun TodayStats(rule: FocusRule, installed: Set<FeedKind>, onClick: (() -> Unit)? = null, large: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatCard(FeedKind.YOUTUBE_SHORTS, rule.youtubeShortsToday, rule.countYouTubeShorts, FeedKind.YOUTUBE_SHORTS in installed, Modifier.weight(1f), large, onClick)
        StatCard(FeedKind.INSTAGRAM_REELS, rule.instagramReelsToday, rule.countInstagramReels, FeedKind.INSTAGRAM_REELS in installed, Modifier.weight(1f), large, onClick)
    }
}

@Composable
private fun StatCard(kind: FeedKind, count: Int, counting: Boolean, installed: Boolean, modifier: Modifier, large: Boolean, onClick: (() -> Unit)?) {
    SurfaceCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(if (large) 20.dp else 18.dp)) {
            PlatformBadge(kind, compact = true)
            Spacer(Modifier.height(if (large) 20.dp else 12.dp))
            Text(count.toString(), fontSize = if (large) 42.sp else 30.sp, lineHeight = if (large) 48.sp else 36.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(if (kind == FeedKind.YOUTUBE_SHORTS) "Shorts today" else "Reels today", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(if (!installed) "Not installed" else if (counting) "Counter enabled" else "Counter paused", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScheduleLink(rule: FocusRule, onClick: () -> Unit) {
    SurfaceCard(onClick = if (rule.rulesLocked) null else onClick) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(Icons.Rounded.Schedule)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Daily schedule", style = MaterialTheme.typography.titleMedium)
                Text(formatFocusTime(rule.startMinute) + " – " + formatFocusTime(rule.endMinute) + "  ·  Every day",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (rule.rulesLocked) Icons.Rounded.Lock else Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupStep(number: String, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.size(28.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
            Text(number, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun PlatformBadge(kind: FeedKind, compact: Boolean = false) {
    val color = if (kind == FeedKind.YOUTUBE_SHORTS) FocusMarkPalette.YouTube else FocusMarkPalette.Instagram
    Box(Modifier.size(if (compact) 32.dp else 42.dp).background(color.copy(alpha = .14f), RoundedCornerShape(if (compact) 10.dp else 13.dp)),
        contentAlignment = Alignment.Center) {
        Icon(if (kind == FeedKind.YOUTUBE_SHORTS) Icons.Rounded.PlayArrow else Icons.Rounded.CameraAlt,
            null, Modifier.size(if (compact) 20.dp else 24.dp), tint = color)
    }
}

@Composable
private fun IconTile(icon: ImageVector) {
    Box(Modifier.size(42.dp).background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = .6f), RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Column(Modifier.padding(top = 8.dp, bottom = 2.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SurfaceCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .55f))
    if (onClick == null) {
        Surface(modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
    } else {
        Surface(onClick = onClick, modifier = modifier.fillMaxWidth(), shape = shape, color = MaterialTheme.colorScheme.surface, border = border, content = content)
    }
}

@Composable
private fun FocusList(content: LazyListScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxHeight().widthIn(max = 760.dp).fillMaxWidth().align(Alignment.TopCenter),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}
