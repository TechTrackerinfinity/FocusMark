package com.focuslock.app.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class SelectableApp(val packageName: String, val label: String, val icon: ImageBitmap? = null)

@Composable
fun AppPicker(initialSelection: Set<String>, onSave: (Set<String>) -> Unit, onBack: () -> Unit, locked: Boolean = false) {
    val context = LocalContext.current
    val apps by produceState<List<SelectableApp>?>(initialValue = null) {
        value = withContext(Dispatchers.IO) { loadApps(context) }
    }
    AppPickerContent(apps, initialSelection, onSave, onBack, locked)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AppPickerContent(
    apps: List<SelectableApp>?,
    initialSelection: Set<String>,
    onSave: (Set<String>) -> Unit,
    onBack: () -> Unit,
    locked: Boolean = false,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var selection by rememberSaveable { mutableStateOf(initialSelection.toList()) }
    val filtered = remember(apps, query) {
        apps.orEmpty().filter { it.label.contains(query.trim(), ignoreCase = true) || it.packageName.contains(query.trim(), ignoreCase = true) }
    }
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Block entire apps", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.background) {
                Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding(), contentAlignment = Alignment.Center) {
                    Button(
                        onClick = { onSave(selection.toSet()) }, enabled = !locked,
                        modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(20.dp).heightIn(min = 52.dp).testTag("save_apps"),
                    ) { Text("Save selection (" + selection.size + ")") }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxWidth().fillMaxHeight().align(Alignment.TopCenter)) {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(if (locked) "Choices are locked during focus, waiting and breaks." else "Selected apps pause during your daily focus window.", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("app_search"),
                        placeholder = { Text("Search installed apps") },
                        leadingIcon = { Icon(Icons.Rounded.Search, null) },
                        trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear search") } }) else null,
                        shape = MaterialTheme.shapes.medium,
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(selection.size.toString() + " selected", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (selection.isNotEmpty()) TextButton(onClick = { selection = emptyList() }, enabled = !locked) { Text("Clear all") }
                    }
                }
                when {
                    apps == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    filtered.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No apps match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            val selected = app.packageName in selection
                            Row(
                                Modifier.fillMaxWidth().toggleable(
                                    value = selected, role = Role.Checkbox, enabled = !locked,
                                    onValueChange = { checked ->
                                        selection = if (checked) (selection + app.packageName).distinct() else selection - app.packageName
                                    },
                                ).padding(horizontal = 12.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (app.icon != null) Image(app.icon, null, Modifier.size(40.dp))
                                else Icon(Icons.Rounded.Apps, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(16.dp))
                                Text(app.label, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge)
                                Checkbox(checked = selected, onCheckedChange = null, enabled = !locked)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadApps(context: Context): List<SelectableApp> {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        context.packageManager.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
            .map { it.activityInfo.applicationInfo }
            .filter { it.packageName != context.packageName }
            .distinctBy { it.packageName }
            .map { app ->
                SelectableApp(
                    app.packageName, app.loadLabel(context.packageManager).toString(),
                    runCatching { app.loadIcon(context.packageManager).toBitmap(80, 80).asImageBitmap() }.getOrNull(),
                )
            }.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }.getOrDefault(emptyList())
}
