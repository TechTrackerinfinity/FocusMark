package com.focuslock.app

import android.app.TimePickerDialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.focuslock.app.data.FocusPreferences
import com.focuslock.app.enforcement.BlockerRuntime
import com.focuslock.app.enforcement.FeedKind
import com.focuslock.app.enforcement.FocusAccessibilityService
import com.focuslock.app.ui.*
import java.util.Calendar
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val destination = FocusDestination.entries.firstOrNull {
            it.name.equals(intent.getStringExtra(EXTRA_DESTINATION), ignoreCase = true)
        } ?: FocusDestination.FOCUS
        setContent { FocusMarkTheme { FocusMarkApp(destination) } }
    }

    companion object {
        const val EXTRA_DESTINATION = "focusmark_destination"
    }
}

@Composable
internal fun FocusMarkApp(initialDestination: FocusDestination = FocusDestination.FOCUS) {
    val context = LocalContext.current
    val preferences = remember { FocusPreferences(context.applicationContext) }
    var rule by remember { mutableStateOf(preferences.rule()) }
    var destination by rememberSaveable { mutableStateOf(initialDestination) }
    var showPicker by rememberSaveable { mutableStateOf(false) }
    var showSchedule by rememberSaveable { mutableStateOf(false) }
    var showAccessibility by rememberSaveable { mutableStateOf(false) }
    var showPauseConfirm by rememberSaveable { mutableStateOf(false) }
    var pendingControl by rememberSaveable { mutableStateOf<FeedControl?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var currentMinute by remember { mutableIntStateOf(minuteNow()) }
    val runtime by BlockerRuntime.state.collectAsState()
    val lifecycle = LocalLifecycleOwner.current

    DisposableEffect(preferences) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> rule = preferences.rule() }
        preferences.registerChangeListener(listener)
        onDispose { preferences.unregisterChangeListener(listener) }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refresh++
                rule = preferences.rule()
                currentMinute = minuteNow()
            }
        }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) {
        while (true) {
            currentMinute = minuteNow()
            rule = preferences.rule()
            delay(1_000)
        }
    }

    val allowed = remember(refresh) { accessibilityEnabled(context) }
    val installed = remember(refresh) {
        FeedKind.entries.filter { kind ->
            runCatching { context.packageManager.getApplicationInfo(kind.packageName, 0) }.isSuccess
        }.toSet()
    }
    val appNames = remember(rule.blockedPackages, refresh) {
        rule.blockedPackages.map { pkg ->
            runCatching {
                context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
            }.getOrDefault("App unavailable")
        }.sorted()
    }
    val summary = focusSummary(rule, runtime.connected, currentMinute, installed)

    fun applyControl(control: FeedControl, enabled: Boolean) {
        val applied = when (control) {
            FeedControl.COUNT_REELS -> preferences.setCountInstagramReels(enabled)
            FeedControl.COUNT_SHORTS -> preferences.setCountYouTubeShorts(enabled)
            FeedControl.BLOCK_REELS -> preferences.setBlockInstagramReels(enabled)
            FeedControl.BLOCK_SHORTS -> preferences.setBlockYouTubeShorts(enabled)
        }
        if (!applied) Toast.makeText(context, "Blocking choices are locked during focus, waiting and breaks.", Toast.LENGTH_LONG).show()
        rule = preferences.rule()
    }

    fun changeControl(control: FeedControl, enabled: Boolean) {
        if (enabled && !preferences.hasContentConsent()) pendingControl = control
        else applyControl(control, enabled)
    }

    if (showPicker) {
        AppPicker(
            initialSelection = rule.blockedPackages,
            locked = rule.rulesLocked,
            onSave = {
                if (!preferences.setBlockedPackages(it)) Toast.makeText(context, "Choices are locked. Your plan was not changed.", Toast.LENGTH_LONG).show()
                rule = preferences.rule()
                showPicker = false
            },
            onBack = { showPicker = false },
        )
        return
    }

    BackHandler(enabled = destination != FocusDestination.FOCUS && !showSchedule) {
        destination = FocusDestination.FOCUS
    }

    Scaffold(
        topBar = { FocusMarkHeader(runtime.connected) { destination = FocusDestination.SETTINGS } },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.background, tonalElevation = 0.dp) {
                FocusDestination.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = destination == tab,
                        onClick = { destination = tab },
                        icon = {
                            Icon(when (tab) {
                                FocusDestination.FOCUS -> Icons.Rounded.Bookmark
                                FocusDestination.ACTIVITY -> Icons.Rounded.BarChart
                                FocusDestination.SETTINGS -> Icons.Rounded.Tune
                            }, contentDescription = null)
                        },
                        label = { Text(tab.title) },
                        modifier = Modifier.testTag("tab_" + tab.name.lowercase()),
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = MaterialTheme.colorScheme.secondaryContainer,
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.onBackground,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            when (destination) {
                FocusDestination.FOCUS -> FocusScreen(
                    rule, summary, runtime, installed, appNames,
                    onPrimary = {
                        when (summary.action) {
                            FocusAction.CONNECT -> showAccessibility = true
                            FocusAction.CHOOSE_APPS -> showPicker = true
                            FocusAction.SCHEDULE -> showSchedule = true
                            FocusAction.START -> preferences.setEnabled(true)
                            FocusAction.PAUSE -> showPauseConfirm = true
                            FocusAction.CANCEL_PAUSE, FocusAction.RESUME -> preferences.cancelPause()
                        }
                        rule = preferences.rule()
                    },
                    onSchedule = { if (!rule.rulesLocked) showSchedule = true },
                    onApps = { if (!rule.rulesLocked) showPicker = true },
                    onControl = ::changeControl,
                    onFeedOnly = { kind ->
                        if (!preferences.setBlockedPackages(rule.blockedPackages - kind.packageName)) Toast.makeText(context, "Choices are locked during this focus plan.", Toast.LENGTH_LONG).show()
                        rule = preferences.rule()
                    },
                    onActivity = { destination = FocusDestination.ACTIVITY },
                    onSetup = { showAccessibility = true },
                )
                FocusDestination.ACTIVITY -> ActivityScreen(
                    rule, runtime, installed, { showAccessibility = true },
                    onLiveCounterChange = {
                        preferences.setShowLiveCounter(it)
                        rule = preferences.rule()
                    },
                )
                FocusDestination.SETTINGS -> SettingsScreen(rule, runtime, allowed, { showAccessibility = true }, { if (!rule.rulesLocked) showSchedule = true })
            }
        }
    }

    if (showSchedule) {
        ScheduleEditor(rule.startMinute, rule.endMinute,
            locked = rule.rulesLocked,
            onSave = { start, end ->
                if (!preferences.setSchedule(start, end)) Toast.makeText(context, "Schedule locked. Your hours were not changed.", Toast.LENGTH_LONG).show()
                rule = preferences.rule()
                showSchedule = false
            },
            onDismiss = { showSchedule = false },
        )
    }
    if (showPauseConfirm) {
        AlertDialog(
            onDismissRequest = { showPauseConfirm = false },
            title = { Text("Request a 10-minute break?") },
            text = { Text("Blocking stays on for a 20-minute wait. A 10-minute break then starts automatically, after which blocking resumes. Your blocking choices stay locked through the wait and break. The daily limit of 100 short videos still applies.") },
            confirmButton = { TextButton(onClick = {
                preferences.requestPause()
                rule = preferences.rule()
                showPauseConfirm = false
            }) { Text("Start 20-minute wait") } },
            dismissButton = { TextButton(onClick = { showPauseConfirm = false }) { Text("Keep blocking") } },
        )
    }
    if (showAccessibility) {
        AccessibilityDisclosure(
            onConfirm = {
                preferences.grantContentConsent()
                showAccessibility = false
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            },
            onDismiss = { showAccessibility = false },
        )
    }
    pendingControl?.let { control ->
        ContentDisclosure(
            onConfirm = {
                preferences.grantContentConsent()
                applyControl(control, true)
                pendingControl = null
            },
            onDismiss = { pendingControl = null },
        )
    }
}

@Composable
private fun FocusMarkHeader(connected: Boolean, onStatus: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.Center) {
            Row(
                Modifier.widthIn(max = 760.dp).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(painterResource(R.drawable.ic_focus_mark), null, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("FocusMark", style = MaterialTheme.typography.titleLarge)
                    Text("Your time, on purpose.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onStatus) {
                    Box(Modifier.size(6.dp).background(
                        if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                    Spacer(Modifier.width(6.dp))
                    Text(if (connected) "Ready" else "Setup", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScheduleEditor(initialStart: Int, initialEnd: Int, onSave: (Int, Int) -> Unit, onDismiss: () -> Unit, locked: Boolean = false) {
    val context = LocalContext.current
    var start by rememberSaveable { mutableIntStateOf(initialStart) }
    var end by rememberSaveable { mutableIntStateOf(initialEnd) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).navigationBarsPadding().padding(bottom = 24.dp)) {
            Text("Your focus hours", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(6.dp))
            Text(if (locked) "Your hours are locked during focus, waiting and breaks." else "Repeat a daily window that works for you.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeChoice("START", start, Modifier.weight(1f), !locked) { pickTime(context, start) { start = it } }
                TimeChoice("END", end, Modifier.weight(1f), !locked) { pickTime(context, end) { end = it } }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(Triple("Morning", 540, 720), Triple("Workday", 540, 1020), Triple("Evening", 1080, 1380)).forEach { preset ->
                    FilterChip(selected = start == preset.second && end == preset.third, enabled = !locked,
                        onClick = { start = preset.second; end = preset.third }, label = { Text(preset.first) })
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                when {
                    start == end -> "Choose different start and end times."
                    start > end -> "Ends the following day. Uses this phone's local time."
                    else -> "Repeats every day. Uses this phone's local time."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (start == end) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { onSave(start, end) }, enabled = start != end && !locked,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("save_schedule")) { Text("Save schedule") }
        }
    }
}

@Composable
private fun TimeChoice(title: String, minute: Int, modifier: Modifier, enabled: Boolean = true, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 88.dp), shape = MaterialTheme.shapes.medium) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(formatFocusTime(minute), fontSize = 25.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccessibilityDisclosure(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Rounded.Shield, null) },
        title = { Text("Connect protection") },
        text = { Text("FocusMark uses Android Accessibility to recognize apps and enforce a daily allowance of 100 Shorts and Reels combined. It inspects visible labels in Instagram and YouTube, even when display counters are hidden, and visible address labels in supported browsers to guard web versions. Labels are discarded after checks. Rules and daily totals stay on this phone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Open Android settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

@Composable
private fun ContentDisclosure(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = { Icon(Icons.Rounded.Visibility, null) },
        title = { Text("Allow feed recognition?") },
        text = { Text("FocusMark inspects visible labels in Instagram and YouTube to enforce a daily limit of 100 short videos combined. The allowance stays active during breaks and when display counters are off. Supported browser address labels are checked to guard web versions. Labels are discarded; daily totals stay on this phone.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Allow and enable") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun accessibilityEnabled(context: Context): Boolean {
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    val component = ComponentName(context, FocusAccessibilityService::class.java)
    return manager.getEnabledAccessibilityServiceList(-1).any {
        it.resolveInfo.serviceInfo.packageName == component.packageName && it.resolveInfo.serviceInfo.name == component.className
    }
}

private fun minuteNow(): Int = Calendar.getInstance().let { it.get(Calendar.HOUR_OF_DAY) * 60 + it.get(Calendar.MINUTE) }

private fun pickTime(context: Context, initial: Int, onSelected: (Int) -> Unit) {
    TimePickerDialog(context, { _, hour, minute -> onSelected(hour * 60 + minute) }, initial / 60, initial % 60, true).show()
}

