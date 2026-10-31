package com.vibe.tvos

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Environment
import android.os.StatFs
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

private val BLUE = Color(0xFF5CC8FF)

private fun boltPath(o: Offset, w: Float, h: Float): Path = Path().apply {
    moveTo(o.x + w * 0.58f, o.y)
    lineTo(o.x + w * 0.10f, o.y + h * 0.56f)
    lineTo(o.x + w * 0.46f, o.y + h * 0.56f)
    lineTo(o.x + w * 0.36f, o.y + h)
    lineTo(o.x + w * 0.92f, o.y + h * 0.38f)
    lineTo(o.x + w * 0.54f, o.y + h * 0.38f)
    close()
}

private fun DrawScope.drawBolt(c: Offset, bw: Float, bh: Float, glow: Float) {
    val o = Offset(c.x - bw / 2f, c.y - bh / 2f)
    val p = boltPath(o, bw, bh)
    drawCircle(
        Brush.radialGradient(listOf(BLUE.copy(alpha = 0.45f * glow), Color.Transparent), center = c, radius = bw * 1.1f),
        radius = bw * 1.1f,
        center = c
    )
    drawPath(p, BLUE.copy(alpha = 0.40f * glow), style = Stroke(width = 5.dp.toPx(), join = StrokeJoin.Round))
    drawPath(p, Brush.verticalGradient(listOf(Color.White, Color(0xFF7CD4FF)), startY = o.y, endY = o.y + bh))
}

@Composable
fun BoostButton(onClick: () -> Unit) {
    var f by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (f) 1.18f else 1f, label = "bscale")
    val glow by animateFloatAsState(if (f) 1f else 0.5f, label = "bglow")
    Box(
        Modifier
            .size(48.dp)
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .onFocusChanged { f = it.isFocused }
            .clip(CircleShape)
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val r = size.minDimension / 2f
            drawCircle(Brush.verticalGradient(listOf(Color(0x59FFFFFF), Color(0x14FFFFFF))), radius = r)
            drawCircle(
                Brush.radialGradient(listOf(BLUE.copy(alpha = 0.50f * glow), Color.Transparent), center = center, radius = r),
                radius = r
            )
            drawOval(
                Brush.verticalGradient(0f to Color(0x80FFFFFF), 0.45f to Color.Transparent),
                topLeft = Offset(size.width * 0.2f, size.height * 0.06f),
                size = Size(size.width * 0.6f, size.height * 0.34f)
            )
            drawBolt(center, size.width * 0.34f, size.height * 0.52f, glow)
            val sw = (if (f) 3.dp else 1.5.dp).toPx()
            drawCircle(Color.White.copy(alpha = if (f) 0.95f else 0.4f), radius = r - sw / 2f, style = Stroke(sw))
        }
    }
}

class BoostResult(val total: Long, val before: Long, val after: Long, val checked: Int, val storageFree: Long)

fun memInfo(am: ActivityManager): ActivityManager.MemoryInfo =
    ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }

fun storageFree(): Long = runCatching { StatFs(Environment.getDataDirectory().path).availableBytes }.getOrDefault(0L)

fun fmt(b: Long): String =
    if (b >= (1L shl 30)) String.format(Locale.US, "%.1f GB", b / 1073741824.0) else "${b / 1048576} MB"

// Asks Android to close background processes of user apps. System services are never touched.
fun killBackground(ctx: Context, am: ActivityManager): Int {
    val pm = ctx.packageManager
    val skip = HashSet<String>()
    skip.add(ctx.packageName)
    val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    pm.queryIntentActivities(home, 0).forEach { skip.add(it.activityInfo.packageName) }
    (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
        .enabledInputMethodList.forEach { skip.add(it.packageName) }
    val keep = listOf(
        "com.google.android.gms", "com.google.android.gsf", "com.android.vending",
        "com.google.android.tvlauncher", "com.google.android.apps.tv.launcherx",
        "com.google.android.inputmethod", "com.google.android.tts", "com.mi.", "com.xiaomi."
    )
    var n = 0
    for (ai in pm.getInstalledApplications(0)) {
        val pkg = ai.packageName
        if (pkg in skip || keep.any { pkg.startsWith(it) }) continue
        val isSystem = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val updated = (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        val launchable = pm.getLaunchIntentForPackage(pkg) != null || pm.getLeanbackLaunchIntentForPackage(pkg) != null
        if (isSystem && !(updated && launchable)) continue
        am.killBackgroundProcesses(pkg)
        n++
    }
    return n
}

@Composable
fun StatRow(label: String, value: String, store: Store) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = ink(store.dark).copy(alpha = 0.7f), fontSize = 15.sp)
        Text(value, color = ink(store.dark), fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun SpinRing() {
    val t = rememberInfiniteTransition(label = "ring")
    val rot by t.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "rot"
    )
    Canvas(Modifier.size(140.dp)) {
        val sw = 8.dp.toPx()
        val r = size.minDimension / 2f - sw
        drawCircle(Color(0x33FFFFFF), radius = r, style = Stroke(sw))
        drawArc(
            BLUE, rot, 110f, false,
            topLeft = Offset(center.x - r, center.y - r), size = Size(2f * r, 2f * r),
            style = Stroke(sw, cap = StrokeCap.Round)
        )
        drawBolt(center, size.width * 0.34f, size.height * 0.46f, 1f)
    }
}

@Composable
fun DoneRing() {
    Canvas(Modifier.size(140.dp)) {
        val sw = 8.dp.toPx()
        val r = size.minDimension / 2f - sw
        drawCircle(Color(0xFF58D68D), radius = r, style = Stroke(sw))
        drawBolt(center, size.width * 0.34f, size.height * 0.46f, 1f)
    }
}

@Composable
fun BoostDialog(store: Store, onClose: () -> Unit) {
    val ctx = LocalContext.current
    var result by remember { mutableStateOf<BoostResult?>(null) }
    var step by remember { mutableStateOf("Checking apps") }
    val fr = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val before = memInfo(am)
        step = "Checking apps"
        delay(500)
        step = "Freeing memory"
        val checked = withContext(Dispatchers.Default) {
            ctx.cacheDir.listFiles()?.forEach { it.deleteRecursively() }
            killBackground(ctx, am)
        }
        delay(1200)
        val after = memInfo(am)
        result = BoostResult(before.totalMem, before.availMem, after.availMem, checked, storageFree())
    }
    LaunchedEffect(result) {
        if (result != null) {
            delay(150)
            runCatching { fr.requestFocus() }
        }
    }

    Panel(store, onClose, Alignment.Center, 460.dp) {
        val r = result
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (r == null) SpinRing() else DoneRing()
        }
        if (r == null) {
            Text(
                "Boosting", color = ink(store.dark), fontSize = 26.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            Text(
                step, color = ink(store.dark).copy(alpha = 0.7f), fontSize = 15.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        } else {
            val freed = (r.after - r.before).coerceAtLeast(0L)
            Text(
                if (freed > 0L) "Freed ${fmt(freed)}" else "Already optimized",
                color = ink(store.dark), fontSize = 26.sp, fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
            StatRow("Free memory", "${fmt(r.before)} to ${fmt(r.after)}", store)
            StatRow("Total memory", fmt(r.total), store)
            StatRow("Apps checked", "${r.checked}", store)
            StatRow("Storage free", fmt(r.storageFree), store)
            Text(
                "Android only lets this app close background apps. Cache of other apps is cleared in Settings.",
                color = ink(store.dark).copy(alpha = 0.6f), fontSize = 12.sp
            )
            CcTile("\u2714", "Done", "", store, Modifier.fillMaxWidth().focusRequester(fr)) { onClose() }
            CcTile("\uD83D\uDCBE", "Storage settings", "", store, Modifier.fillMaxWidth()) {
                openSys(ctx, Settings.ACTION_INTERNAL_STORAGE_SETTINGS)
                onClose()
            }
        }
    }
}
