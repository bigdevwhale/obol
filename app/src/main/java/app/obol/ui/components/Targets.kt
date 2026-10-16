package app.obol.ui.components

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import app.obol.R
import app.obol.domain.Site
import app.obol.ui.theme.U
import app.obol.ui.theme.UType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** An installed app that can be launched. */
data class AppInfo(val pkg: String, val label: String)

object Apps {
    private var cache: List<AppInfo>? = null
    private val icons = HashMap<String, ImageBitmap?>()

    suspend fun launchable(context: Context): List<AppInfo> = cache ?: withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm.queryIntentActivities(intent, 0)
            .map { AppInfo(it.activityInfo.packageName, it.loadLabel(pm).toString()) }
            .filter { it.pkg != context.packageName }
            .distinctBy { it.pkg }
            .sortedBy { it.label.lowercase() }
            .also { cache = it }
    }

    fun label(context: Context, pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    fun installed(context: Context, pkg: String): Boolean = runCatching {
        context.packageManager.getApplicationInfo(pkg, 0)
        true
    }.getOrDefault(false)

    suspend fun icon(context: Context, pkg: String): ImageBitmap? {
        if (icons.containsKey(pkg)) return icons[pkg]
        val bmp = withContext(Dispatchers.IO) {
            runCatching { context.packageManager.getApplicationIcon(pkg).toBitmap(96, 96).asImageBitmap() }.getOrNull()
        }
        icons[pkg] = bmp
        return bmp
    }
}

@Composable
fun AppIcon(pkg: String, size: androidx.compose.ui.unit.Dp = 32.dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(null, pkg) { value = Apps.icon(context, pkg) }
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        val i = icon
        if (i != null) Image(i, null, Modifier.size(size))
        else Icon(Icons.Rounded.Apps, null, tint = U.c.faint, modifier = Modifier.size(size * 0.7f))
    }
}

/** The apps and sites of a patron or a stream, with ways to add and remove them. */
@Composable
fun TargetsEditor(apps: List<String>, sites: List<String>, onApps: (List<String>) -> Unit, onSites: (List<String>) -> Unit, siteHint: String) {
    val context = LocalContext.current
    var picking by remember { mutableStateOf(false) }

    Eyebrow(stringResource(R.string.targets_apps))
    Gap(6.dp)
    // Installed apps first; the ones that aren't on this phone are kept, just quieter.
    val sorted = remember(apps) { apps.sortedByDescending { Apps.installed(context, it) } }
    sorted.forEach { pkg ->
        val installed = remember(pkg) { Apps.installed(context, pkg) }
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIcon(pkg, 30.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(remember(pkg) { Apps.label(context, pkg) }, style = UType.Body.copy(color = if (installed) U.c.ink else U.c.faint), maxLines = 1)
                if (!installed) Text(stringResource(R.string.targets_not_installed), style = UType.Small)
            }
            RemoveDot { onApps(apps - pkg) }
        }
    }
    Gap(4.dp)
    GhostButton(stringResource(R.string.targets_add_app), Modifier.fillMaxWidth(), icon = Icons.Rounded.Add) { picking = true }

    Gap(18.dp)
    Eyebrow(stringResource(R.string.targets_sites))
    Gap(6.dp)
    sites.forEach { s ->
        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Language, null, tint = U.c.muted, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
            Text(s, style = UType.Body, modifier = Modifier.weight(1f))
            RemoveDot { onSites(sites - s) }
        }
    }
    var draft by remember { mutableStateOf("") }
    val parsed = Site.parse(draft)
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.trim() },
            placeholder = { Text(siteHint, style = UType.Muted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            textStyle = UType.Body,
            colors = fieldColors(),
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            Modifier.size(48.dp).clip(CircleShape)
                .background(if (parsed != null) U.c.accent else U.c.surface2)
                .clickable(enabled = parsed != null) {
                    val v = parsed!!.host + parsed.path
                    if (v !in sites) onSites(sites + v)
                    draft = ""
                },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Add, null, tint = if (parsed != null) U.c.onAccent else U.c.faint) }
    }
    Text(stringResource(R.string.targets_site_help), style = UType.Small, modifier = Modifier.padding(top = 6.dp))

    if (picking) {
        AppPicker(apps.toSet(), onDismiss = { picking = false }) {
            onApps(it.toList())
            picking = false
        }
    }
}

@Composable
private fun RemoveDot(onClick: () -> Unit) {
    Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Close, null, tint = U.c.faint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = U.c.accent,
    unfocusedBorderColor = U.c.line,
    cursorColor = U.c.accent,
    focusedTextColor = U.c.ink,
    unfocusedTextColor = U.c.ink,
)

/** A searchable list of installed apps to tick. */
@Composable
fun AppPicker(initial: Set<String>, onDismiss: () -> Unit, onDone: (Set<String>) -> Unit) {
    val context = LocalContext.current
    var chosen by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var all by remember { mutableStateOf<List<AppInfo>?>(null) }
    LaunchedEffect(Unit) { all = Apps.launchable(context) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(U.c.bg).navigationBarsPadding().imePadding(),
        ) {
            TopBar(stringResource(R.string.picker_title), back = onDismiss) {
                Text(
                    stringResource(R.string.action_done),
                    style = UType.Button.copy(color = U.c.accent),
                    modifier = Modifier.clip(U.RXl).clickable { onDone(chosen) }.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.picker_search), style = UType.Muted) },
                singleLine = true,
                textStyle = UType.Body,
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Gap(8.dp)
            val list = all
            if (list == null) {
                Text(stringResource(R.string.picker_loading), style = UType.Muted, modifier = Modifier.padding(20.dp))
            } else {
                val q = query.trim().lowercase()
                val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.pkg.contains(q) }
                    .sortedByDescending { it.pkg in initial }
                LazyColumn(Modifier.weight(1f)) {
                    items(shown, key = { it.pkg }) { app ->
                        val on = app.pkg in chosen
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable { chosen = if (on) chosen - app.pkg else chosen + app.pkg }
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AppIcon(app.pkg, 36.dp)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(app.label, style = UType.Body, maxLines = 1)
                                Text(app.pkg, style = UType.Small, maxLines = 1)
                            }
                            Icon(
                                if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                null,
                                tint = if (on) U.c.accent else U.c.line,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A thin bordered surface used by small pickers. */
@Composable
fun Swatch(selected: Boolean, color: androidx.compose.ui.graphics.Color, size: androidx.compose.ui.unit.Dp = 34.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape)
            .border(2.dp, if (selected) U.c.ink else androidx.compose.ui.graphics.Color.Transparent, CircleShape)
            .padding(4.dp)
            .background(color, CircleShape)
            .clickable(onClick = onClick),
    )
}
