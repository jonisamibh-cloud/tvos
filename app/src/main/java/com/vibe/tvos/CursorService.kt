package com.vibe.tvos

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

class CursorService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var overlay: CursorView
    private val handler = Handler(Looper.getMainLooper())
    private var cx = 0f
    private var cy = 0f
    private var appIsMobile = false
    private var onHome = false
    private var ignored = setOf<String>()
    private var best: Rect? = null
    private var bestArea = Long.MAX_VALUE
    private val hl = Runnable { updateHighlight() }

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val m = resources.displayMetrics
        cx = m.widthPixels / 2f
        cy = m.heightPixels / 2f
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        ignored = imm.enabledInputMethodList.map { it.packageName }.toSet() +
                setOf("com.android.systemui", "android")
        overlay = CursorView(this)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(overlay, lp)
        overlay.visibility = View.GONE
        refresh()
    }

    private fun mode(): String =
        getSharedPreferences("tvos", MODE_PRIVATE).getString("cursor_mode", "AUTO") ?: "AUTO"

    // The launcher itself never shows the cursor, so it can never get stuck there.
    private fun active(): Boolean = if (onHome) false else when (mode()) {
        "ON" -> true
        "OFF" -> false
        else -> appIsMobile
    }

    private fun isHome(pkg: String): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return packageManager.queryIntentActivities(i, 0).any { it.activityInfo.packageName == pkg }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent?) {
        if (e == null || e.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = e.packageName?.toString() ?: return
        if (pkg in ignored) return
        onHome = (pkg == packageName)
        appIsMobile = if (pkg == packageName || isHome(pkg)) false
        else packageManager.getLeanbackLaunchIntentForPackage(pkg) == null
        refresh()
    }

    private fun refresh() {
        if (!::overlay.isInitialized) return
        if (active()) {
            overlay.visibility = View.VISIBLE
            scheduleHighlight(100)
        } else {
            overlay.visibility = View.GONE
        }
    }

    override fun onKeyEvent(ev: KeyEvent): Boolean {
        if (!::overlay.isInitialized) return false
        val a = active()
        if ((overlay.visibility == View.VISIBLE) != a) refresh()
        val k = ev.keyCode

        if (k == KeyEvent.KEYCODE_MENU || k == KeyEvent.KEYCODE_PROG_RED) {
            if (ev.action == KeyEvent.ACTION_DOWN && ev.repeatCount == 0) cycleMode()
            return true
        }
        if (!a) return false

        when (k) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (ev.action == KeyEvent.ACTION_DOWN) move(k, ev.repeatCount)
                return true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                if (ev.action == KeyEvent.ACTION_UP) {
                    val long = ev.eventTime - ev.downTime > 600
                    tap(cx, cy, if (long) 700L else 50L)
                    scheduleHighlight(450)
                }
                return true
            }
        }
        return false
    }

    private fun cycleMode() {
        val next = when (mode()) { "AUTO" -> "ON"; "ON" -> "OFF"; else -> "AUTO" }
        getSharedPreferences("tvos", MODE_PRIVATE).edit().putString("cursor_mode", next).apply()
        Toast.makeText(this, "Cursor: $next", Toast.LENGTH_SHORT).show()
        refresh()
    }

    private fun move(k: Int, rep: Int) {
        val m = resources.displayMetrics
        val w = m.widthPixels
        val h = m.heightPixels
        val step = (30 + minOf(rep, 25) * 8).toFloat()
        var dx = 0f
        var dy = 0f
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP -> dy = -step
            KeyEvent.KEYCODE_DPAD_DOWN -> dy = step
            KeyEvent.KEYCODE_DPAD_LEFT -> dx = -step
            else -> dx = step
        }
        val atEdge = (k == KeyEvent.KEYCODE_DPAD_UP && cy <= 2f) ||
                (k == KeyEvent.KEYCODE_DPAD_DOWN && cy >= h - 3f) ||
                (k == KeyEvent.KEYCODE_DPAD_LEFT && cx <= 2f) ||
                (k == KeyEvent.KEYCODE_DPAD_RIGHT && cx >= w - 3f)
        if (atEdge) {
            if (rep % 6 == 0) scroll(k, w, h)
            return
        }
        cx = (cx + dx).coerceIn(0f, (w - 1).toFloat())
        cy = (cy + dy).coerceIn(0f, (h - 1).toFloat())
        overlay.moveTo(cx, cy)
        scheduleHighlight(60)
    }

    private fun scroll(k: Int, w: Int, h: Int) {
        when (k) {
            KeyEvent.KEYCODE_DPAD_UP -> swipe(w / 2f, h * 0.3f, w / 2f, h * 0.7f)
            KeyEvent.KEYCODE_DPAD_DOWN -> swipe(w / 2f, h * 0.7f, w / 2f, h * 0.3f)
            KeyEvent.KEYCODE_DPAD_LEFT -> swipe(w * 0.3f, h / 2f, w * 0.7f, h / 2f)
            else -> swipe(w * 0.7f, h / 2f, w * 0.3f, h / 2f)
        }
        scheduleHighlight(500)
    }

    private fun tap(x: Float, y: Float, dur: Long) {
        val p = Path().apply { moveTo(x, y) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, dur)).build()
        dispatchGesture(g, null, null)
    }

    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 300)).build()
        dispatchGesture(g, null, null)
    }

    private fun scheduleHighlight(delay: Long) {
        handler.removeCallbacks(hl)
        handler.postDelayed(hl, delay)
    }

    private fun updateHighlight() {
        if (!::overlay.isInitialized || overlay.visibility != View.VISIBLE) return
        best = null
        bestArea = Long.MAX_VALUE
        val x = cx.toInt()
        val y = cy.toInt()
        val ws = windows
        if (ws.isNullOrEmpty()) scan(rootInActiveWindow, x, y, 0)
        else for (w in ws) scan(w.root, x, y, 0)
        overlay.set(cx, cy, best)
    }

    private fun scan(n: AccessibilityNodeInfo?, x: Int, y: Int, depth: Int) {
        if (n == null || depth > 40) return
        val r = Rect()
        n.getBoundsInScreen(r)
        if (!r.contains(x, y)) return
        if (n.isClickable || n.isCheckable || n.isEditable) {
            val area = r.width().toLong() * r.height().toLong()
            if (area in 1 until bestArea) {
                bestArea = area
                best = Rect(r)
            }
        }
        for (i in 0 until n.childCount) scan(n.getChild(i), x, y, depth + 1)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        handler.removeCallbacks(hl)
        if (::overlay.isInitialized) runCatching { wm.removeView(overlay) }
        super.onDestroy()
    }
}

class CursorView(ctx: Context) : View(ctx) {
    private var px = 0f
    private var py = 0f
    private var rect: Rect? = null
    private val d = ctx.resources.displayMetrics.density

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 4 * d; color = 0xFF00E5FF.toInt()
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = 0x3300E5FF
    }
    private val arrowFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = Color.WHITE
    }
    private val arrowLine = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.5f; color = Color.BLACK
    }
    private val arrow = Path().apply {
        moveTo(0f, 0f); lineTo(0f, 30f); lineTo(8f, 23f); lineTo(14f, 36f)
        lineTo(19f, 34f); lineTo(13f, 21f); lineTo(23f, 21f); close()
    }

    fun set(x: Float, y: Float, r: Rect?) {
        px = x; py = y; rect = r; invalidate()
    }

    fun moveTo(x: Float, y: Float) {
        px = x; py = y; invalidate()
    }

    override fun onDraw(c: Canvas) {
        rect?.let {
            val rf = RectF(it)
            c.drawRoundRect(rf, 10 * d, 10 * d, fill)
            c.drawRoundRect(rf, 10 * d, 10 * d, stroke)
        }
        c.save()
        c.translate(px, py)
        c.scale(d * 1.2f, d * 1.2f)
        c.drawPath(arrow, arrowFill)
        c.drawPath(arrow, arrowLine)
        c.restore()
    }
}
