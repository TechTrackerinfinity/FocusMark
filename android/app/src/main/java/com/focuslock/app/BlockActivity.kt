package com.focuslock.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.focuslock.app.data.FocusPreferences
import com.focuslock.app.ui.FocusMarkPalette
import com.focuslock.app.ui.FocusMarkTheme
import com.focuslock.app.ui.formatFocusTime

class BlockActivity : ComponentActivity() {
    private var blockedAppLabel by mutableStateOf("This app")
    private var featureBlock by mutableStateOf(false)
    private var endMinute by mutableIntStateOf(0)
    private var reason by mutableStateOf(REASON_SCHEDULE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        readTarget(intent)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goHome()
        })
        setContent {
            FocusMarkTheme { BlockPage(blockedAppLabel, featureBlock, endMinute, reason, ::goHome) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readTarget(intent)
    }

    private fun readTarget(intent: Intent) {
        endMinute = FocusPreferences(this).rule().endMinute
        reason = intent.getStringExtra(EXTRA_BLOCK_REASON) ?: REASON_SCHEDULE
        featureBlock = intent.getStringExtra(EXTRA_BLOCKED_FEATURE) != null
        blockedAppLabel = intent.getStringExtra(EXTRA_BLOCKED_FEATURE) ?: runCatching {
            val pkg = intent.getStringExtra(EXTRA_BLOCKED_PACKAGE).orEmpty()
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault("This app")
    }

    private fun goHome() {
        startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }

    companion object {
        const val EXTRA_BLOCKED_PACKAGE = "blocked_package"
        const val EXTRA_BLOCKED_FEATURE = "blocked_feature"
        const val EXTRA_BLOCK_REASON = "block_reason"
        const val REASON_SCHEDULE = "schedule"
        const val REASON_DAILY_LIMIT = "daily_limit"
        const val REASON_WEB_GUARD = "web_guard"
    }
}

@Composable
internal fun BlockPage(appLabel: String, featureBlock: Boolean, endMinute: Int,
    reason: String = BlockActivity.REASON_SCHEDULE, onGoHome: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp), contentAlignment = Alignment.Center) {
            Column(Modifier.widthIn(max = 460.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(painterResource(R.drawable.ic_focus_mark), null, Modifier.size(32.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("FocusMark", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(48.dp))
                Surface(color = FocusMarkPalette.Lime, contentColor = FocusMarkPalette.Forest, shape = CircleShape) {
                    Box(Modifier.size(96.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Bookmark, null, Modifier.size(44.dp))
                    }
                }
                Spacer(Modifier.height(28.dp))
                Text("A moment for\nyour focus.", style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Text(appLabel + " is paused", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(when (reason) {
                    BlockActivity.REASON_DAILY_LIMIT -> "You've reached 100 Shorts and Reels combined today. Your allowance resets on the next day of your plan. A break does not reset this limit."
                    BlockActivity.REASON_WEB_GUARD -> "This website is guarded to keep short videos within your plan. Use the native app for permitted content and breaks."
                    else -> "Your daily focus window is on until " + formatFocusTime(endMinute) + ". Request a break in FocusMark: 20 minutes of waiting, then 10 minutes of access."
                },
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                if (featureBlock && reason != BlockActivity.REASON_WEB_GUARD) {
                    Spacer(Modifier.height(12.dp))
                    Text("The rest of the app is still available.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(36.dp))
                Button(onClick = onGoHome, modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp), shape = MaterialTheme.shapes.medium) {
                    Icon(Icons.Rounded.Home, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text("Return home")
                }
            }
        }
    }
}
