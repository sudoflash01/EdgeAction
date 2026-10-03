package com.example.edgevolume

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.media.AudioManager
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

// swipe in from the edge strip -> volume panel. thats all it does
class EdgeService : AccessibilityService() {

    private lateinit var wm: WindowManager
    private lateinit var sp: SharedPreferences
    private var strip: ZoneView? = null
    private var left = false
    private var startX = 0f
    private var done = false

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

    // rotation -> screen height changes, redo position
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (strip != null) draw()
    }

    override fun onDestroy() {
        if (::sp.isInitialized) sp.unregisterOnSharedPreferenceChangeListener(prefListener)
        strip?.let { runCatching { wm.removeView(it) } }
        strip = null
        super.onDestroy()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun makeParams(): WindowManager.LayoutParams {
        val hPx = dp(sp.getInt(Cfg.H, Cfg.DEF_H).coerceIn(40, 200)) // android allows ~200dp exclusion max
        val wPx = dp(sp.getInt(Cfg.W, Cfg.DEF_W).coerceIn(4, 60))
        val screenH = resources.displayMetrics.heightPixels
        val centerY = screenH * sp.getInt(Cfg.Y, Cfg.DEF_Y) / 1000f
        val top = (centerY - hPx / 2f).toInt().coerceIn(0, screenH - hPx)
        left = sp.getBoolean(Cfg.LEFT, false)

        return WindowManager.LayoutParams(
            wPx, hPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or (if (left) Gravity.START else Gravity.END)
            x = 0
            y = top
        }
    }

    private val touch = View.OnTouchListener { v, e ->
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { startX = e.rawX; done = false }
            MotionEvent.ACTION_MOVE -> {
                // inward = left on right edge, right on left edge
                val dx = if (left) e.rawX - startX else startX - e.rawX
                if (!done && dx > dp(28)) {
                    done = true
                    if (sp.getBoolean(Cfg.VIB, true)) v.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    showVolume()
                }
            }
        }
        true
    }

    private fun showVolume() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_SAME, AudioManager.FLAG_SHOW_UI)
    }

    // create strip first time, after that just update it (no flicker while sliding)
    private fun draw() {
        val lp = makeParams()
        // alpha 1 not 0, fully empty view = exclusion zone silently ignored. dont change!
        val color = if (sp.getBoolean(Cfg.SHOW, true)) Color.argb(110, 255, 64, 64) else Color.argb(1, 0, 0, 0)
        val old = strip
        if (old == null) {
            val v = ZoneView(this)
            v.setBackgroundColor(color)
            v.setOnTouchListener(touch)
            wm.addView(v, lp)
            strip = v
        } else {
            old.setBackgroundColor(color)
            wm.updateViewLayout(old, lp)
        }
    }

    // tells system "dont do back gesture here"
    private class ZoneView(ctx: Context) : View(ctx) {
        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            super.onLayout(changed, l, t, r, b)
            systemGestureExclusionRects = listOf(Rect(0, 0, r - l, b - t))
        }
    }
}
