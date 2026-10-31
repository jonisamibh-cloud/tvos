package com.vibe.tvos

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

val WALLS = listOf(
    listOf(Color(0xFF0A0E1A), Color(0xFF1C1B3A), Color(0xFF3A1C4A)),
    listOf(Color(0xFF021B3B), Color(0xFF0B4F8A), Color(0xFF1AA6B7)),
    listOf(Color(0xFF2B0F3A), Color(0xFFB13B6E), Color(0xFFFF9A5A)),
    listOf(Color(0xFF04140F), Color(0xFF0F4D3A), Color(0xFF4FA36B)),
    listOf(Color(0xFF0D0D0F), Color(0xFF2A2A30), Color(0xFF4B4B55)),
    listOf(Color(0xFF061226), Color(0xFF2A1B6B), Color(0xFF12C2A0))
)

val ACCENTS = listOf(
    Color(0xFF4DA3FF), Color(0xFFB388FF), Color(0xFFFF6FA8),
    Color(0xFFFFA24D), Color(0xFF58D68D), Color(0xFFFFFFFF)
)

fun glass(dark: Boolean) = if (dark) Color(0xE61C1C24) else Color(0xE6F2F2F7)
fun ink(dark: Boolean) = if (dark) Color.White else Color(0xFF111114)
fun chip(dark: Boolean) = if (dark) Color(0x33FFFFFF) else Color(0x1F000000)

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val intent: Intent)

sealed interface Entry { val key: String }
class AppEntry(val app: AppItem) : Entry { override val key: String get() = "a:" + app.pkg }
class FolderEntry(val name: String, val apps: List<AppItem>) : Entry { override val key: String get() = "f:" + name }

fun parseFolders(s: String?): Map<String, List<String>> {
    val m = LinkedHashMap<String, List<String>>()
    s?.lines()?.forEach { l ->
        val p = l.split("\t")
        if (p.size == 2 && p[0].isNotBlank()) {
            val items = p[1].split(",").filter { it.isNotBlank() }
            if (items.isNotEmpty()) m[p[0]] = items
        }
    }
    return m
}

fun serializeFolders(m: Map<String, List<String>>): String =
    m.entries.joinToString("\n") { it.key + "\t" + it.value.joinToString(",") }

class Store(private val sp: SharedPreferences) {
    var pinned by mutableStateOf(sp.getStringSet("pinned", emptySet())!!.toSet())
        private set
    var hidden by mutableStateOf(sp.getStringSet("hidden", emptySet())!!.toSet())
        private set
    var cursor by mutableStateOf(sp.getString("cursor_mode", "AUTO")!!)
        private set
    var wallpaper by mutableIntStateOf(sp.getInt("wallpaper", 0))
        private set
    var photo by mutableStateOf<String?>(sp.getString("photo", null))
        private set
    var accent by mutableIntStateOf(sp.getInt("accent", 0))
        private set
    var dark by mutableStateOf(sp.getBoolean("dark", true))
        private set
    var folders by mutableStateOf(parseFolders(sp.getString("folders", "")))
        private set

    private fun saveFolders(m: Map<String, List<String>>) {
        val clean = LinkedHashMap<String, List<String>>()
        for ((k, v) in m) if (v.isNotEmpty()) clean[k] = v
        folders = clean
        sp.edit().putString("folders", serializeFolders(clean)).apply()
    }

    fun folderOf(pkg: String): String? = folders.entries.firstOrNull { pkg in it.value }?.key

    fun moveToFolder(pkg: String, name: String) {
        val m = LinkedHashMap<String, List<String>>()
        for ((k, v) in folders) m[k] = v - pkg
        m[name] = (m[name] ?: emptyList()) + pkg
        saveFolders(m)
    }

    fun removeFromFolder(pkg: String) {
        saveFolders(folders.mapValues { it.value - pkg })
    }

    fun deleteFolder(name: String) {
        saveFolders(folders - name)
    }

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

    fun chooseWallpaper(i: Int) {
        wallpaper = i
        photo = null
        sp.edit().putInt("wallpaper", i).remove("photo").apply()
    }

    fun choosePhoto(u: String) {
        photo = u
        sp.edit().putString("photo", u).apply()
    }

    fun chooseAccent(i: Int) {
        accent = i
        sp.edit().putInt("accent", i).apply()
    }

    fun toggleDark() {
        dark = !dark
        sp.edit().putBoolean("dark", dark).apply()
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
            val bmp = runCatching { ri.loadIcon(pm).toBitmap(128, 128).asImageBitmap() }.getOrNull() ?: continue
            out[pkg] = AppItem(ri.loadLabel(pm).toString(), pkg, bmp, launch)
        }
    }
    return out.values.toList()
}

fun netStatus(ctx: Context): String {
    return runCatching {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            else -> "Online"
        }
    }.getOrDefault("Wi-Fi")
}

fun loadPhoto(ctx: Context, uri: Uri): ImageBitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var s = 1
    while (bounds.outWidth / (s * 2) >= 1920) s *= 2
    val o = BitmapFactory.Options().apply { inSampleSize = s }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }?.asImageBitmap()
}.getOrNull()

fun openSys(ctx: Context, action: String) {
    runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

class MainActivity : ComponentActivity() {
    private var tick by mutableIntStateOf(0)
    private val store by lazy { Store(getSharedPreferences("tvos", MODE_PRIVATE)) }

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            store.choosePhoto(uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Home(store, tick) { pickPhoto() } }
    }

    override fun onResume() {
        super.onResume()
        tick++
    }

    private fun pickPhoto() {
        try {
            picker.launch(arrayOf("image/*"))
        } catch (e: Exception) {
            Toast.makeText(this, "This TV has no photo picker", Toast.LENGTH_LONG).show()
        }
    }
}

@Composable
fun Wallpaper(store: Store) {
    val ctx = LocalContext.current
    var img by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(store.photo) {
        val p = store.photo
        img = if (p == null) null else withContext(Dispatchers.IO) { loadPhoto(ctx, Uri.parse(p)) }
    }
    val bmp = img
    if (bmp != null) {
        Image(bmp, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.fillMaxSize().background(Brush.linearGradient(WALLS[store.wallpaper % WALLS.size])))
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color(0xB3000000))
        )
    )
}

@Composable
fun Home(store: Store, tick: Int, pickPhoto: () -> Unit) {
    val ctx = LocalContext.current
    val all = remember(tick) { loadApps(ctx) }
    val entries = remember(all, store.hidden, store.pinned, store.folders) {
        val inFolder = store.folders.values.flatten().toSet()
        val visible = all.filter { it.pkg !in store.hidden }
        val sorted = visible.filter { it.pkg !in inFolder }.sortedBy { it.label.lowercase() }
        val pinned = sorted.filter { it.pkg in store.pinned }
        val rest = sorted.filter { it.pkg !in store.pinned }
        val folderEntries = store.folders.mapNotNull { (name, pkgs) ->
            val apps = pkgs.mapNotNull { p -> visible.firstOrNull { it.pkg == p } }
            if (apps.isEmpty()) null else FolderEntry(name, apps)
        }
        buildList<Entry> {
            addAll(pinned.map { AppEntry(it) })
            addAll(folderEntries)
            addAll(rest.map { AppEntry(it) })
        }
    }
    var panel by remember { mutableStateOf<String?>(null) }
    var menuApp by remember { mutableStateOf<AppItem?>(null) }
    var hero by remember { mutableStateOf("") }
    var pickApp by remember { mutableStateOf<AppItem?>(null) }
    var openFolder by remember { mutableStateOf<String?>(null) }
    var folderMenu by remember { mutableStateOf<String?>(null) }
    var boostOpen by remember { mutableStateOf(false) }
    var now by remember { mutableStateOf(Date()) }
    var net by remember { mutableStateOf("Wi-Fi") }
    val first = remember { FocusRequester() }

    BackHandler(true) { }
    LaunchedEffect(entries.size) { delay(250); runCatching { first.requestFocus() } }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            net = netStatus(ctx)
            delay(10_000)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Wallpaper(store)

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 56.dp, vertical = 32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    SimpleDateFormat("h:mm a", Locale.getDefault()).format(now),
                    color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Light
                )
                Text(
                    SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(now),
                    color = Color(0xCCFFFFFF), fontSize = 16.sp
                )
            }
            BoostButton { boostOpen = true }
            Spacer(Modifier.width(16.dp))
            Pill("\uD83D\uDCF6  $net   \u2699", store) { panel = "control" }
        }

        Column(Modifier.align(Alignment.BottomStart)) {
            Text(
                hero, color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(start = 56.dp)
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                contentPadding = PaddingValues(start = 56.dp, end = 56.dp, top = 22.dp, bottom = 40.dp)
            ) {
                itemsIndexed(entries, key = { _, e -> e.key }) { idx, e ->
                    val fm = if (idx == 0) Modifier.focusRequester(first) else Modifier
                    when (e) {
                        is AppEntry -> Tile(
                            e.app, store, fm,
                            onFocus = { hero = e.app.label },
                            onClick = { runCatching { ctx.startActivity(e.app.intent) } },
                            onLong = { menuApp = e.app }
                        )
                        is FolderEntry -> FolderTile(
                            e, store, fm,
                            onFocus = { hero = e.name },
                            onClick = { openFolder = e.name },
                            onLong = { folderMenu = e.name }
                        )
                    }
                }
            }
        }
    }

    if (boostOpen) BoostDialog(store) { boostOpen = false }

    FolderDialogs(
        store, entries, openFolder, folderMenu, pickApp,
        { openFolder = it }, { folderMenu = it }, { pickApp = it }, { menuApp = it }
    )

    when (panel) {
        "control" -> Panel(store, { panel = null }) {
            Text("Control Center", color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("\uD83D\uDCF6", "Wi-Fi", net, store, Modifier.weight(1f)) {
                    panel = null; openSys(ctx, Settings.ACTION_WIFI_SETTINGS)
                }
                CcTile("\uD83D\uDD35", "Bluetooth", "", store, Modifier.weight(1f)) {
                    panel = null; openSys(ctx, Settings.ACTION_BLUETOOTH_SETTINGS)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("\uD83D\uDDBC", "Wallpaper", "", store, Modifier.weight(1f)) { panel = "wall" }
                CcTile("\uD83C\uDFA8", "Theme", "", store, Modifier.weight(1f)) { panel = "theme" }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("\uD83D\uDDB1", "Cursor", store.cursor, store, Modifier.weight(1f)) { store.cycleCursor() }
                CcTile("\uD83D\uDD0A", "Sound", "", store, Modifier.weight(1f)) {
                    panel = null; openSys(ctx, Settings.ACTION_SOUND_SETTINGS)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CcTile("\uD83D\uDDA5", "Display", "", store, Modifier.weight(1f)) {
                    panel = null; openSys(ctx, Settings.ACTION_DISPLAY_SETTINGS)
                }
                CcTile("\u2699", "All settings", "", store, Modifier.weight(1f)) {
                    panel = null; openSys(ctx, Settings.ACTION_SETTINGS)
                }
            }
            if (store.hidden.isNotEmpty()) {
                CcTile("\uD83D\uDC41", "Show hidden apps", "${store.hidden.size} hidden", store, Modifier.fillMaxWidth()) {
                    store.unhideAll()
                }
            }
        }

        "wall" -> Panel(store, { panel = null }) {
            Text("Wallpaper", color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0..2) Swatch(i, store, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 3..5) Swatch(i, store, Modifier.weight(1f))
            }
            CcTile("\uD83D\uDCF7", "Choose a photo", "From USB or storage", store, Modifier.fillMaxWidth()) { pickPhoto() }
            CcTile("\u25C0", "Back", "", store, Modifier.fillMaxWidth()) { panel = "control" }
        }

        "theme" -> Panel(store, { panel = null }) {
            Text("Theme", color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 0..2) Dot(i, store, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                for (i in 3..5) Dot(i, store, Modifier.weight(1f))
            }
            CcTile("\uD83C\uDF13", "Panels", if (store.dark) "Dark" else "Light", store, Modifier.fillMaxWidth()) {
                store.toggleDark()
            }
            CcTile("\u25C0", "Back", "", store, Modifier.fillMaxWidth()) { panel = "control" }
        }
    }

    menuApp?.let { app ->
        Panel(store, { menuApp = null }, Alignment.Center) {
            Text(app.label, color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            CcTile(
                "\u2B50", if (app.pkg in store.pinned) "Remove from favourites" else "Add to favourites",
                "", store, Modifier.fillMaxWidth()
            ) { store.togglePin(app.pkg); menuApp = null }
            CcTile("\uD83D\uDCC1", "Move to folder", "", store, Modifier.fillMaxWidth()) {
                menuApp = null; pickApp = app
            }
            if (store.folderOf(app.pkg) != null) {
                CcTile("\u21A9", "Remove from folder", store.folderOf(app.pkg) ?: "", store, Modifier.fillMaxWidth()) {
                    store.removeFromFolder(app.pkg); menuApp = null; openFolder = null
                }
            }
            CcTile("\uD83D\uDE48", "Hide from home", "", store, Modifier.fillMaxWidth()) {
                store.hide(app.pkg); menuApp = null
            }
            CcTile("\u2139", "App info", "", store, Modifier.fillMaxWidth()) {
                menuApp = null
                runCatching {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
            CcTile("\uD83D\uDDD1", "Uninstall", "", store, Modifier.fillMaxWidth()) {
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

@Composable
fun Panel(
    store: Store,
    onClose: () -> Unit,
    alignment: Alignment = Alignment.TopEnd,
    width: Dp = 440.dp,
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val fm = LocalFocusManager.current
        LaunchedEffect(Unit) { delay(200); runCatching { fm.moveFocus(FocusDirection.Next) } }
        Box(Modifier.fillMaxSize().padding(28.dp), contentAlignment = alignment) {
            Column(
                Modifier.width(width).heightIn(max = 480.dp).clip(RoundedCornerShape(30.dp)).background(glass(store.dark))
                    .then(if (scroll) Modifier.verticalScroll(scrollState) else Modifier)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
fun Pill(text: String, store: Store, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Box(
        Modifier
            .onFocusChanged { f = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (f) ACCENTS[store.accent % ACCENTS.size] else Color(0x55000000))
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 10.dp)
    ) {
        Text(text, color = if (f) Color.Black else Color.White, fontSize = 16.sp)
    }
}

@Composable
fun CcTile(emoji: String, title: String, sub: String, store: Store, modifier: Modifier, onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    Row(
        modifier
            .onFocusChanged { f = it.isFocused }
            .clip(RoundedCornerShape(18.dp))
            .background(if (f) ACCENTS[store.accent % ACCENTS.size] else chip(store.dark))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(emoji, fontSize = 22.sp)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, color = if (f) Color.Black else ink(store.dark), fontSize = 15.sp, fontWeight = FontWeight.Medium)
            if (sub.isNotEmpty()) {
                Text(sub, color = if (f) Color(0xB3000000) else ink(store.dark).copy(alpha = 0.65f), fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun Swatch(i: Int, store: Store, modifier: Modifier) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    val sel = store.photo == null && store.wallpaper == i
    Box(
        modifier
            .height(64.dp)
            .onFocusChanged { f = it.isFocused }
            .clip(shape)
            .background(Brush.linearGradient(WALLS[i]))
            .border(
                if (f) 3.dp else if (sel) 2.dp else 0.dp,
                if (f) Color.White else ACCENTS[store.accent % ACCENTS.size],
                shape
            )
            .clickable { store.chooseWallpaper(i) },
        contentAlignment = Alignment.Center
    ) {
        if (sel) Text("\u2713", color = Color.White, fontSize = 20.sp)
    }
}

@Composable
fun Dot(i: Int, store: Store, modifier: Modifier) {
    var f by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .height(52.dp)
            .onFocusChanged { f = it.isFocused }
            .clip(shape)
            .background(ACCENTS[i])
            .border(if (f) 3.dp else 0.dp, if (store.dark) Color.White else Color.Black, shape)
            .clickable { store.chooseAccent(i) },
        contentAlignment = Alignment.Center
    ) {
        if (store.accent == i) Text("\u2713", color = Color.Black, fontSize = 20.sp)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Tile(
    app: AppItem,
    store: Store,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.28f else 1f, label = "scale")
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .size(92.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(shape)
            .background(Color(0x33FFFFFF))
            .border(if (focused) 3.dp else 0.dp, ACCENTS[store.accent % ACCENTS.size], shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong)
    ) {
        Image(app.icon, app.label, Modifier.fillMaxSize().padding(10.dp))
    }
}


@Composable
fun FolderDialogs(
    store: Store,
    entries: List<Entry>,
    openFolder: String?,
    folderMenu: String?,
    pickApp: AppItem?,
    setOpenFolder: (String?) -> Unit,
    setFolderMenu: (String?) -> Unit,
    setPickApp: (AppItem?) -> Unit,
    setMenuApp: (AppItem?) -> Unit
) {
    val ctx = LocalContext.current

    if (openFolder != null) {
        val apps = entries.filterIsInstance<FolderEntry>().firstOrNull { it.name == openFolder }?.apps
        if (apps != null) {
            Panel(store, { setOpenFolder(null) }, Alignment.Center, 620.dp, false) {
                Text(openFolder, color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(110.dp),
                    modifier = Modifier.height(260.dp),
                    contentPadding = PaddingValues(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(apps, key = { it.pkg }) { app ->
                        Tile(
                            app, store, Modifier,
                            onFocus = { },
                            onClick = {
                                setOpenFolder(null)
                                runCatching { ctx.startActivity(app.intent) }
                            },
                            onLong = { setMenuApp(app) }
                        )
                    }
                }
            }
        }
    }

    if (folderMenu != null) {
        Panel(store, { setFolderMenu(null) }, Alignment.Center) {
            Text(folderMenu, color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            CcTile("\uD83D\uDCC2", "Open", "", store, Modifier.fillMaxWidth()) {
                setOpenFolder(folderMenu); setFolderMenu(null)
            }
            CcTile("\uD83D\uDDD1", "Delete folder", "Apps go back to the row", store, Modifier.fillMaxWidth()) {
                store.deleteFolder(folderMenu); setFolderMenu(null)
            }
        }
    }

    if (pickApp != null) {
        val names = (listOf("Games", "Movies", "Music", "Tools", "Social", "Work") + store.folders.keys).distinct()
        Panel(store, { setPickApp(null) }, Alignment.Center) {
            Text("Move ${pickApp.label} to", color = ink(store.dark), fontSize = 20.sp, fontWeight = FontWeight.Medium)
            for (n in names) {
                val exists = store.folders.containsKey(n)
                CcTile(
                    if (exists) "\uD83D\uDCC1" else "\u2795", n,
                    if (exists) "${store.folders[n]?.size ?: 0} apps" else "New folder",
                    store, Modifier.fillMaxWidth()
                ) {
                    store.moveToFolder(pickApp.pkg, n)
                    setPickApp(null)
                    setOpenFolder(null)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderTile(
    f: FolderEntry,
    store: Store,
    modifier: Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLong: () -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.28f else 1f, label = "fscale")
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier
            .size(92.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocus()
            }
            .clip(shape)
            .background(Color(0x55FFFFFF))
            .border(if (focused) 3.dp else 0.dp, ACCENTS[store.accent % ACCENTS.size], shape)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            for (r in 0..1) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    for (c in 0..1) {
                        val a = f.apps.getOrNull(r * 2 + c)
                        Box(
                            Modifier.size(34.dp).clip(RoundedCornerShape(8.dp))
                                .background(if (a == null) Color(0x22FFFFFF) else Color.Transparent)
                        ) {
                            if (a != null) Image(a.icon, null, Modifier.fillMaxSize())
                        }
                    }
                }
            }
        }
    }
}
