package com.vibe.tvos

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val intent: Intent)

class Store(private val sp: SharedPreferences) {
    var pinned by mutableStateOf(sp.getStringSet("pinned", emptySet())!!.toSet())
        private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet())
        private set
    var cursor by mutableStateOf(sp.getString("cursor_mode", "AUTO")!!)
        private set

    fun togglePin(p: String) {
        pinned = if (p in pinned) pinned - p else pinned + p
        sp.edit().putStringSet("pinned", pinned).apply()
    }

    fun hide(p: String) {
        hidden = hidden + p
        sp.edit().putStringSet("hidden", hidden).apply()
    }

    fun unhideAll() {
        hidden = emptySet()
        sp.edit().putStringSet("hidden", hidden).apply()
    }

    fun cycleCursor() {
        cursor = when (cursor) { "AUTO" -> "ON"; "ON" -> "OFF"; else -> "AUTO" }
        sp.edit().putString("cursor_mode", cursor).apply()
    }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val out = LinkedHashMap<String, AppItem>()
    for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
        val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
        for (ri in pm.queryIntentActivities(q, 0)) {
            val pkg = ri.activityInfo.packageName
            if (pkg == ctx.packageName || out.containsKey(pkg)) continue
            val launch = Intent(Intent.ACTION_MAIN).addCategory(cat)
                .setClassName(pkg, ri.activityInfo.name)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val bmp = runCatching { ri.loadIcon(pm).toBitmap(160, 160).asImageBitmap() }.getOrNull() ?: continue
            out[pkg] = AppItem(ri.loadLabel(pm).toString(), pkg, bmp, launch)
        }
    }
    return out.values.sortedBy { it.label.lowercase() }
}

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = Store(getSharedPreferences("tvos", MODE_PRIVATE))
        setContent { Home(store, tick) }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }
}

@Composable
fun Home(store: Store, tick: Int) {
    val ctx = LocalContext.current
    val all = remember(tick) { loadApps(ctx) }
    val visible = all.filter { it.pkg !in store.hidden }
    val pinned = visible.filter { it.pkg in store.pinned }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    val first = remember { FocusRequester() }
    var now by remember { mutableStateOf(Date()) }

    BackHandler(true) { }
    LaunchedEffect(all.size) { delay(200); runCatching { first.requestFocus() } }
    LaunchedEffect(Unit) { while (true) { now = Date(); delay(10_000) } }

    Box(
        Modifier.fillMaxSize().background(
            Brush.linearGradient(listOf(Color(0xFF0A0E1A), Color(0xFF1C1B3A), Color(0xFF3A1C4A)))
        )
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 56.dp, vertical = 32.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        SimpleDateFormat("h:mm a", Locale.getDefault()).format(now),
                        color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light
                    )
                    Text(
                        SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                        color = Color(0xB3FFFFFF), fontSize = 16.sp
                    )
                }
                if (store.hidden.isNotEmpty()) {
                    ActionButton("Show hidden (${store.hidden.size})") { store.unhideAll() }
                    Spacer(Modifier.width(12.dp))
                }
                ActionButton("Cursor: ${store.cursor}") { store.cycleCursor() }
            }
            Spacer(Modifier.height(20.dp))

            if (pinned.isNotEmpty()) {
                Text("Favourites", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)
                ) {
                    items(pinned, key = { it.pkg }) { app ->
                        Tile(
                            app, 150.dp,
                            if (app.pkg == pinned.first().pkg) Modifier.focusRequester(first) else Modifier,
                            onClick = { launch(ctx, app) }, onLong = { menuApp = app }
                        )
                    }
                }
            }

            Text("All apps", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
            LazyVerticalGrid(
                columns = GridCells.Adaptive(130.dp),
                contentPadding = PaddingValues(vertical = 16.dp, horizontal = 8.dp)
            ) {
                items(visible, key = { it.pkg }) { app ->
                    Tile(
                        app, 130.dp,
                        if (pinned.isEmpty() && app.pkg == visible.first().pkg) Modifier.focusRequester(first) else Modifier,
                        onClick = { launch(ctx, app) }, onLong = { menuApp = app }
                    )
                }
            }
        }
    }

    menuApp?.let { app ->
        Dialog(onDismissRequest = { menuApp = null }) {
            val fr = remember { FocusRequester() }
            LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
            Column(
                Modifier.clip(RoundedCornerShape(24.dp)).background(Color(0xFF1E2233)).padding(28.dp).width(340.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(app.label, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                ActionButton(
                    if (app.pkg in store.pinned) "Remove from favourites" else "Add to favourites",
                    Modifier.fillMaxWidth().focusRequester(fr)
                ) { store.togglePin(app.pkg); menuApp = null }
                ActionButton("Hide from home", Modifier.fillMaxWidth()) { store.hide(app.pkg); menuApp = null }
                ActionButton("App info", Modifier.fillMaxWidth()) {
                    menuApp = null
                    runCatching {
                        ctx.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
                ActionButton("Uninstall", Modifier.fillMaxWidth()) {
                    menuApp = null
                    runCatching {
                        ctx.startActivity(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}"))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                }
            }
        }
    }
}

fun launch(ctx: Context, app: AppItem) {
    runCatching { ctx.startActivity(app.intent) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(app: AppItem, size: Dp, modifier: Modifier, onClick: () -> Unit, onLong: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.15f else 1f, label = "scale")
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .width(size)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = onLong)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(size - 12.dp).clip(shape)
                .background(if (focused) Color(0x44FFFFFF) else Color(0x22FFFFFF))
                .border(if (focused) 3.dp else 0.dp, Color.White, shape)
        ) {
            Image(app.icon, app.label, Modifier.fillMaxSize().padding(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            app.label, color = if (focused) Color.White else Color(0xB3FFFFFF),
            fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ActionButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        modifier
            .onFocusChanged { f = it.isFocused }
            .clip(RoundedCornerShape(12.dp))
            .background(if (f) Color.White else Color(0x33FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(text, color = if (f) Color.Black else Color.White, fontSize = 15.sp)
    }
}
