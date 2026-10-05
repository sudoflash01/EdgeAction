package com.sudoflash01.edgeaction

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

// edge strips. each strip is one tiny overlay window that runs its own action when you swipe on it
class EdgeService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var sp: SharedPreferences
    private val main = Handler(Looper.getMainLooper())

    private val zones = HashMap<Int, ZoneView>()   // strip id -> its window
    private var strips: Map<Int, Strip> = emptyMap()

    // keep this as field, local one gets garbage collected (learned the hard way)
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> draw() }

    override fun onServiceConnected() {
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        sp = Cfg.sp(this)
        sp.registerOnSharedPreferenceChangeListener(prefListener)
        draw()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    // rotation -> screen height changes, redo positions
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (zones.isNotEmpty()) draw()
    }

    override fun onDestroy() {
        if (::sp.isInitialized) sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        if (::wm.isInitialized) zones.values.forEach { runCatching { wm.removeView(it) } }
        zones.clear()
        torchCb?.let { cb ->
            runCatching { (getSystemService(Context.CAMERA_SERVICE) as CameraManager).unregisterTorchCallback(cb) }
        }
        torchCb = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ---- windows ----

    private fun makeParams(s: Strip): WindowManager.LayoutParams {
        val hPx = dp(s.h.coerceIn(Strips.MIN_H, Strips.MAX_EDGE_DP)) // android gives max ~200dp exclusion per edge
        val wPx = dp(s.w.coerceIn(Strips.MIN_W, Strips.MAX_W))
        val screenH = resources.displayMetrics.heightPixels
        val centerY = screenH * s.ypm / 1000f
        val top = (centerY - hPx / 2f).toInt().coerceIn(0, (screenH - hPx).coerceAtLeast(0))

        return WindowManager.LayoutParams(
            wPx, hPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (s.right) Gravity.END else Gravity.START)
            x = 0
            y = top
        }
    }

    // add / update / remove windows to match saved strips (no flicker when sliding)
    private fun draw() {
        if (!::wm.isInitialized) return
        val list = Strips.load(this)
        strips = list.associateBy { it.id }

        val wanted = list.filter { it.on }.map { it.id }.toSet()
        zones.keys.filter { it !in wanted }.forEach { id ->
            zones.remove(id)?.let { if (activeZone === it) activeZone = null; runCatching { wm.removeView(it) } }
        }

        for (s in list) {
            if (!s.on) continue
            val lp = makeParams(s)
            // alpha 1 not 0 when hidden, fully empty view = exclusion zone silently ignored. dont change!
            val color = if (s.shown) (s.color and 0xFFFFFF) or (110 shl 24) else Color.argb(1, 0, 0, 0)
            val old = zones[s.id]
            if (old == null) {
                val v = ZoneView(this, s.id)
                v.setBackgroundColor(color)
                v.setOnTouchListener(touch)
                wm.addView(v, lp)
                zones[s.id] = v
            } else {
                old.setBackgroundColor(color)
                wm.updateViewLayout(old, lp)
            }
        }
    }

    // ---- gestures ----

    // strip that owns the current touch. only one strip can run a gesture at a time, and it
    // uses settings of the strip where touch started (copy taken at touch down)
    private var activeZone: ZoneView? = null

    private val touch = View.OnTouchListener { v, e ->
        val z = v as ZoneView
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val s = strips[z.stripId]
                z.strip = s
                z.startX = e.rawX
                z.startY = e.rawY
                z.mode = ZoneView.IDLE
                z.lastVol = -1
                if (s != null) {
                    activeZone = z
                    if (s.along == 2) {
                        val am = audio()
                        z.startVol = am.getStreamVolume(s.alongStream)
                        z.maxVol = am.getStreamMaxVolume(s.alongStream).coerceAtLeast(1)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val s = z.strip
                if (s != null && activeZone === z) {
                    // inward = left on right edge, right on left edge
                    val dx = if (s.right) z.startX - e.rawX else e.rawX - z.startX
                    val dyUp = z.startY - e.rawY
                    if (z.mode == ZoneView.IDLE) {
                        if (s.swipeIn.kind != "none" && dx > dp(28) && dx > Math.abs(dyUp)) {
                            z.mode = ZoneView.INWARD
                            buzz(v, s)
                            run(s.swipeIn)
                        } else if (s.along != 0 && Math.abs(dyUp) > dp(14) && Math.abs(dyUp) > Math.abs(dx)) {
                            z.mode = ZoneView.ALONG
                            buzz(v, s)
                            showVolume(s.alongStream)
                        }
                    }
                    // volume changes only for the strip set to "Slide" which you are touching
                    if (z.mode == ZoneView.ALONG && s.along == 2) slideVolume(s, z, dyUp)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                z.mode = ZoneView.IDLE
                z.strip = null
                if (activeZone === z) activeZone = null
            }
        }
        true
    }

    // both global switch (settings) and this strip's own switch should be on
    private fun buzz(v: View, s: Strip) {
        if (s.vib && sp.getBoolean(Cfg.VIB, true)) v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    // 260dp finger movement from bottom to top = full volume range
    private fun slideVolume(s: Strip, z: ZoneView, dyUp: Float) {
        val pxPerStep = dp(260).toFloat() / z.maxVol
        val target = (z.startVol + Math.round(dyUp / pxPerStep)).coerceIn(0, z.maxVol)
        if (target == z.lastVol) return
        z.lastVol = target
        try {
            audio().setStreamVolume(s.alongStream, target, AudioManager.FLAG_SHOW_UI)
        } catch (_: SecurityException) {
            // e.g. do-not-disturb blocks ring volume changes
        }
    }

    // ---- actions ----

    private fun audio() = getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private fun say(t: String) = Toast.makeText(this, "Edge Action: $t", Toast.LENGTH_SHORT).show()

    private fun showVolume(stream: Int) {
        audio().adjustStreamVolume(stream, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    private fun run(a: Act) {
        try {
            when (a.kind) {
                "vol" -> showVolume(a.arg.toIntOrNull() ?: AudioManager.STREAM_MUSIC)
                "app" -> {
                    val i = packageManager.getLaunchIntentForPackage(a.arg)
                    if (i == null) say("app not found") else startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                "url" -> startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(a.arg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "intent" -> {
                    val i = Intent.parseUri(a.arg, Intent.URI_INTENT_SCHEME)
                    when (a.arg2) {
                        "service" -> startService(i)
                        "broadcast" -> sendBroadcast(i)
                        else -> startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                }
                "torch" -> toggleTorch()
                "media" -> mediaKey(
                    when (a.arg) {
                        "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
                        "prev" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
                        else -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                    }
                )
                "global" -> performGlobalAction(
                    when (a.arg) {
                        "back" -> GLOBAL_ACTION_BACK
                        "home" -> GLOBAL_ACTION_HOME
                        "recents" -> GLOBAL_ACTION_RECENTS
                        "notifications" -> GLOBAL_ACTION_NOTIFICATIONS
                        "quick" -> GLOBAL_ACTION_QUICK_SETTINGS
                        "power" -> GLOBAL_ACTION_POWER_DIALOG
                        "lock" -> GLOBAL_ACTION_LOCK_SCREEN
                        else -> GLOBAL_ACTION_TAKE_SCREENSHOT
                    }
                )
            }
        } catch (t: Throwable) {
            say(t.javaClass.simpleName + (t.message?.let { ": " + it.take(60) } ?: ""))
        }
    }

    private fun mediaKey(code: Int) {
        val t = SystemClock.uptimeMillis()
        val am = audio()
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(t, t, KeyEvent.ACTION_UP, code, 0))
    }

    // flashlight needs no permission. we get to know torch state only after first use
    private var torchId: String? = null
    private var torchOn = false
    private var torchCb: CameraManager.TorchCallback? = null

    private fun toggleTorch() {
        val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
        if (torchId == null) {
            torchId = cm.cameraIdList.firstOrNull { id ->
                val c = cm.getCameraCharacteristics(id)
                c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                    c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
            val id = torchId
            if (id == null) { say("no flashlight found"); return }
            val cb = object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
                    if (cameraId == id) torchOn = enabled
                }
            }
            torchCb = cb
            cm.registerTorchCallback(cb, main)
            // wait a little so callback can tell real state, then we flip it
            main.postDelayed({ runCatching { cm.setTorchMode(id, !torchOn) } }, 150)
            return
        }
        cm.setTorchMode(torchId!!, !torchOn)
    }

    // tells system "dont do back gesture here", and remembers the touch in progress
    private class ZoneView(ctx: Context, val stripId: Int) : View(ctx) {
        var strip: Strip? = null   // the strip settings this touch started with
        var startX = 0f
        var startY = 0f
        var mode = IDLE
        var startVol = 0
        var maxVol = 15
        var lastVol = -1

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
        }

        companion object {
            const val IDLE = 0
            const val INWARD = 1
            const val ALONG = 2
        }
    }
}
