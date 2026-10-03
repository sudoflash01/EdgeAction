package com.example.edgevolume

import android.app.Activity
import android.app.AlertDialog
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

// setup screen. nothing fancy, plain views (no compose, keeps apk tiny)
class MainActivity : Activity() {

    private lateinit var sp: SharedPreferences
    private lateinit var status: TextView
    private lateinit var iconBtn: Button

    private val alias by lazy { ComponentName(packageName, "$packageName.LauncherAlias") }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sp = Cfg.sp(this)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }

        status = TextView(this).apply { textSize = 16f }
        root.addView(status)
        root.addView(TextView(this).apply {
            setPadding(0, dp(8), 0, dp(8))
            text = "1. Turn on Edge Volume in Accessibility\n" +
                "2. Red strip shows on the screen edge, move it where you like\n" +
                "3. Swipe in from the strip = volume panel\n" +
                "4. Done? untick red strip (and hide icon if you want)"
        })

        root.addView(btn("Open Accessibility settings") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        // android 13+ blocks sideloaded apps, allow it from 3 dot menu here
        root.addView(btn("Open App info (for \"Allow restricted settings\")") {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        })

        root.addView(title("Swipe zone"))
        stepper(root, "Vertical position", Cfg.Y, Cfg.DEF_Y, 0, 1000,
            listOf("-1%" to -10, "-0.1%" to -1, "+0.1%" to 1, "+1%" to 10)) { "${it / 10.0}%" }
        stepper(root, "Height (max 200, android limit)", Cfg.H, Cfg.DEF_H, 40, 200,
            listOf("-10" to -10, "-1" to -1, "+1" to 1, "+10" to 10)) { "$it dp" }
        stepper(root, "Width", Cfg.W, Cfg.DEF_W, 4, 60,
            listOf("-5" to -5, "-1" to -1, "+1" to 1, "+5" to 5)) { "$it dp" }

        root.addView(title("Options"))
        root.addView(check("Show red strip (for setup)", Cfg.SHOW, true))
        root.addView(check("Use left edge instead of right", Cfg.LEFT, false))
        root.addView(check("Vibrate on swipe", Cfg.VIB, true))

        root.addView(title("App icon"))
        iconBtn = btn("") { toggleIcon() }
        root.addView(iconBtn)
        root.addView(TextView(this).apply {
            text = "If you hide the icon, open this screen from Settings > Accessibility > Edge Volume > Settings."
        })

        setContentView(ScrollView(this).apply { addView(root) })
    }

    override fun onResume() {
        super.onResume()
        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        status.text = if (on) "Service is ON" else "Service is OFF, turn it on in Accessibility"
        iconBtn.text = if (iconVisible()) "Hide app icon" else "Show app icon"
    }

    private fun iconVisible() =
        packageManager.getComponentEnabledSetting(alias) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setIcon(show: Boolean) {
        packageManager.setComponentEnabledSetting(
            alias,
            if (show) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        iconBtn.text = if (show) "Hide app icon" else "Show app icon"
    }

    private fun toggleIcon() {
        if (!iconVisible()) { setIcon(true); return }
        AlertDialog.Builder(this)
            .setTitle("Hide icon?")
            .setMessage("Service keeps working. To open settings later go to Settings > Accessibility > Edge Volume > Settings.")
            .setPositiveButton("Hide") { _, _ -> setIcon(false) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- small ui helpers ----

    private fun btn(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t
        setOnClickListener { onClick() }
    }

    private fun title(t: String) = TextView(this).apply {
        text = t
        textSize = 18f
        setPadding(0, dp(20), 0, dp(4))
        setTypeface(typeface, android.graphics.Typeface.BOLD)
    }

    private fun check(t: String, key: String, def: Boolean) = CheckBox(this).apply {
        text = t
        isChecked = sp.getBoolean(key, def)
        setOnCheckedChangeListener { _, c -> sp.edit().putBoolean(key, c).apply() }
    }

    // slider + nudge buttons, slider alone is too jumpy for exact position
    private fun stepper(parent: LinearLayout, label: String, key: String, def: Int, lo: Int, hi: Int,
                        steps: List<Pair<String, Int>>, fmt: (Int) -> String) {
        val tv = TextView(this)
        val sb = SeekBar(this).apply { max = hi - lo }

        fun showVal(v: Int) { tv.text = "$label: ${fmt(v)}" }
        fun save(v: Int) {
            val c = v.coerceIn(lo, hi)
            showVal(c)
            sb.progress = c - lo
            sp.edit().putInt(key, c).apply()
        }

        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) save(p + lo) }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })

        val row = LinearLayout(this)
        steps.forEach { (name, delta) ->
            row.addView(btn(name) { save(sp.getInt(key, def) + delta) },
                LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }

        val cur = sp.getInt(key, def).coerceIn(lo, hi)
        showVal(cur)
        sb.progress = cur - lo
        parent.addView(tv); parent.addView(sb); parent.addView(row)
    }
}
