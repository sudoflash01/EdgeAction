package com.sudoflash01.edgeaction

import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.widget.NestedScrollView
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.transition.platform.MaterialFadeThrough
import com.google.android.material.R as MR

// 2 tabs in one screen (bottom nav). home = status + strips list, settings = app options
class MainActivity : BaseActivity() {

    companion object {
        private const val KEY_TAB = "tab"
        private const val TAB_HOME = 0
        private const val TAB_SETTINGS = 1
    }

    private var tab = TAB_HOME

    private lateinit var root: ViewGroup
    private lateinit var appBar: AppBarLayout
    private lateinit var toolbar: MaterialToolbar
    private lateinit var nav: NavigationBarView
    private lateinit var pageHome: NestedScrollView
    private lateinit var pageSettings: View
    private lateinit var fab: ExtendedFloatingActionButton

    private lateinit var list: LinearLayout
    private lateinit var empty: View
    private lateinit var statusCard: MaterialCardView
    private lateinit var statusIconWrap: View
    private lateinit var statusIcon: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusBody: TextView
    private lateinit var statusActions: View
    private lateinit var edgeCard: View
    private lateinit var leftText: TextView
    private lateinit var rightText: TextView
    private lateinit var leftBar: LinearProgressIndicator
    private lateinit var rightBar: LinearProgressIndicator
    private lateinit var previewCard: View
    private lateinit var previewIcon: ImageView
    private lateinit var previewSub: TextView
    private lateinit var previewSwitch: MaterialSwitch

    private val alias by lazy { ComponentName(packageName, "$packageName.LauncherAlias") }
    private var guard = false      // true when we change a switch from code, so its listener dont run
    private var statusOn: Boolean? = null
    private var entered = false    // home screen animation should play only one time
    private var summaryReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Strips.load(this) // moves old single strip settings to new format (first time only)
        setContentView(R.layout.activity_main)

        root = findViewById(R.id.root)
        appBar = findViewById(R.id.appBar)
        toolbar = findViewById(R.id.toolbar)
        nav = findViewById(R.id.nav)
        pageHome = findViewById(R.id.pageHome)
        pageSettings = findViewById(R.id.pageSettings)
        fab = findViewById(R.id.fab)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        statusCard = findViewById(R.id.statusCard)
        statusIconWrap = findViewById(R.id.statusIconWrap)
        statusIcon = findViewById(R.id.statusIcon)
        statusTitle = findViewById(R.id.statusTitle)
        statusBody = findViewById(R.id.statusBody)
        statusActions = findViewById(R.id.statusActions)
        edgeCard = findViewById(R.id.edgeCard)
        leftText = findViewById(R.id.leftText)
        rightText = findViewById(R.id.rightText)
        leftBar = findViewById(R.id.leftBar)
        rightBar = findViewById(R.id.rightBar)
        previewCard = findViewById(R.id.previewCard)
        previewIcon = findViewById(R.id.previewIcon)
        previewSub = findViewById(R.id.previewSub)
        previewSwitch = findViewById(R.id.previewSwitch)

        bindHome()
        bindSettings()

        // stay on same tab when screen is recreated (like after changing dynamic colour)
        tab = savedInstanceState?.getInt(KEY_TAB, TAB_HOME) ?: TAB_HOME
        nav.selectedItemId = if (tab == TAB_SETTINGS) R.id.nav_settings else R.id.nav_home
        nav.setOnItemSelectedListener {
            showTab(if (it.itemId == R.id.nav_settings) TAB_SETTINGS else TAB_HOME, animate = true)
            true
        }
        showTab(tab, animate = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(KEY_TAB, tab)
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
        render() // strip can change in editor so refresh
    }

    private fun showTab(t: Int, animate: Boolean) {
        tab = t
        val home = t == TAB_HOME
        if (animate) {
            // fade animation when switching pages
            TransitionManager.beginDelayedTransition(
                root,
                MaterialFadeThrough().apply { addTarget(pageHome); addTarget(pageSettings) }
            )
        }
        pageHome.visibility = if (home) View.VISIBLE else View.GONE
        pageSettings.visibility = if (home) View.GONE else View.VISIBLE
        toolbar.title = if (home) getString(R.string.app_name) else "Settings"
        appBar.setLiftOnScrollTargetViewId(if (home) R.id.pageHome else R.id.pageSettings)
        appBar.setExpanded(true, animate)
        if (home) fab.show() else fab.hide()
    }

    private fun snack(msg: String) {
        val s = Snackbar.make(root, msg, Snackbar.LENGTH_LONG)
        if (fab.isShown) s.setAnchorView(fab)
        s.show()
    }

    // ---- home ----

    private fun bindHome() {
        findViewById<View>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        // android 13+ blocks apps installed outside playstore, user has to allow from 3 dot menu in app info
        findViewById<View>(R.id.btnRestricted).setOnClickListener { openAppInfo() }
        fab.setOnClickListener { addStrip() }
        findViewById<View>(R.id.btnEmptyAdd).setOnClickListener { addStrip() }

        // fab shows full "Add strip" at top, becomes only icon when we scroll down
        pageHome.setOnScrollChangeListener(NestedScrollView.OnScrollChangeListener { _, _, y, _, oldY ->
            if (y > oldY + 6) fab.shrink() else if (y < oldY - 6 || y == 0) fab.extend()
        })

        // one switch for all previews, eye icon only shows on/off state
        previewSwitch.setOnCheckedChangeListener { _, checked ->
            if (!guard) showAll(checked)
        }
        previewCard.setOnClickListener { if (previewSwitch.isEnabled) previewSwitch.toggle() }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            snack("No app found to open the link")
        }
    }

    private fun openAppInfo() =
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))

    private fun updateStatus() {
        val on = (Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "")
            .contains(packageName)
        val changed = statusOn != null && statusOn != on
        statusOn = on

        val bg = MaterialColors.getColor(statusCard, if (on) MR.attr.colorPrimaryContainer else MR.attr.colorErrorContainer)
        val fg = MaterialColors.getColor(statusCard, if (on) MR.attr.colorOnPrimaryContainer else MR.attr.colorOnErrorContainer)
        Anim.tintCard(statusCard, bg, animate = changed)
        statusIconWrap.backgroundTintList = ColorStateList.valueOf(ColorUtils.setAlphaComponent(fg, 40))
        Anim.swapIcon(statusIcon, if (on) R.drawable.ic_check_circle else R.drawable.ic_error, animate = changed)
        statusIcon.imageTintList = ColorStateList.valueOf(fg)
        statusTitle.setTextColor(fg)
        statusBody.setTextColor(fg)
        statusTitle.text = if (on) "Edge Action is on" else "Edge Action is off"
        statusBody.text = if (on) "Swipe in from a strip" else "Turn it on in Accessibility"

        val actions = if (on) View.GONE else View.VISIBLE
        if (statusActions.visibility != actions) {
            if (changed) TransitionManager.beginDelayedTransition(statusCard, AutoTransition())
            statusActions.visibility = actions
        }
        if (changed) Anim.pop(statusIconWrap)
    }

    private fun render() {
        val all = Strips.load(this)
        list.removeAllViews()
        all.forEachIndexed { i, s -> list.addView(stripCard(s, i, all.size)) }
        empty.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        refreshSummary(all, animate = summaryReady)
        summaryReady = true

        if (!entered) {
            entered = true
            var n = 0
            Anim.enter(statusCard, n++)
            Anim.enter(edgeCard, n++)
            Anim.enter(previewCard, n++)
            for (i in 0 until list.childCount) Anim.enter(list.getChildAt(i), n++)
            if (all.isEmpty()) Anim.enter(empty, n)
        }
    }

    // edge meters + preview row. its light work, so every strip change just calls this and we dont rebuild whole list
    private fun refreshSummary(all: List<Strip>, animate: Boolean) {
        bindEdge(leftText, leftBar, Strips.edgeUsed(all, false))
        bindEdge(rightText, rightBar, Strips.edgeUsed(all, true))

        val shown = all.count { it.shown }
        val any = shown > 0
        previewSub.text = when {
            all.isEmpty() -> "No strips yet"
            any -> "$shown of ${all.size} visible"
            else -> "Hidden, still working"
        }
        previewSwitch.isEnabled = all.isNotEmpty()
        guard = true
        previewSwitch.isChecked = any
        guard = false
        Anim.swapIcon(previewIcon, if (any) R.drawable.ic_visibility else R.drawable.ic_visibility_off, animate)
        Anim.leading(previewIcon, any, animate)
    }

    private fun bindEdge(text: TextView, bar: LinearProgressIndicator, used: Int) {
        text.text = "$used / ${Strips.MAX_EDGE_DP} dp"
        bar.setProgressCompat(used.coerceAtMost(Strips.MAX_EDGE_DP), true)
    }

    private fun stripCard(s: Strip, index: Int, count: Int): View {
        val v = layoutInflater.inflate(R.layout.item_strip, list, false) as MaterialCardView
        Anim.segment(v, index, count)

        // round coloured icon, shows what swipe does
        val dot = v.findViewById<ImageView>(R.id.dot)
        dot.setBackgroundResource(R.drawable.bg_dot)
        dot.backgroundTintList = ColorStateList.valueOf(s.color)
        dot.setImageResource(Acts.icon(s.swipeIn))
        dot.imageTintList = ColorStateList.valueOf(
            if (ColorUtils.calculateLuminance(s.color) > 0.5) Color.BLACK else Color.WHITE
        )

        v.findViewById<TextView>(R.id.name).text = s.name
        // small line below title: which edge + what swipe does
        v.findViewById<TextView>(R.id.edge).text =
            (if (s.right) "Right" else "Left") + " \u00B7 " + Acts.describe(s.swipeIn)

        val sw = v.findViewById<MaterialSwitch>(R.id.enabled)
        sw.isChecked = s.on
        styleRow(v, s, animate = false)
        sw.setOnCheckedChangeListener { _, c ->
            if (guard) return@setOnCheckedChangeListener
            Strips.setOn(this, s.id, c)?.let { snack(it) }
            // strip can get refused if no space, so show what is actually saved
            val now = Strips.load(this)
            val cur = now.firstOrNull { it.id == s.id } ?: return@setOnCheckedChangeListener
            if (cur.on != c) {
                guard = true
                sw.isChecked = cur.on
                guard = false
            }
            styleRow(v, cur, animate = true) // update the same row, if we rebuild then switch animation will cut
            refreshSummary(now, animate = true)
        }
        v.setOnClickListener { openEditor(s.id) }
        return v
    }

    // row look. on = strip colour tint, full opacity. off = plain and dim
    private fun styleRow(v: MaterialCardView, s: Strip, animate: Boolean) {
        val base = Anim.color(v, MR.attr.colorSurfaceContainerHigh)
        val target = if (s.on) ColorUtils.compositeColors(ColorUtils.setAlphaComponent(s.color, 44), base) else base
        Anim.tintCard(v, target, animate)
        val content = v.findViewById<View>(R.id.rowContent)
        val alpha = if (s.on) 1f else 0.55f
        if (animate) Anim.fade(content, alpha) else content.alpha = alpha
    }

    private fun openEditor(id: Int) {
        startActivity(Intent(this, StripEditActivity::class.java).putExtra(StripEditActivity.EXTRA_ID, id))
    }

    private fun addStrip() {
        val all = Strips.load(this)
        val id = (all.maxOfOrNull { it.id } ?: 0) + 1
        val base = Strip(id, "Strip $id", right = true, ypm = 300, h = 100, color = Strips.nextColor(all))
        val placed = Strips.place(all, base, Strips.refDp(this))
        val s = placed ?: run {
            snack("No room left, so the new strip starts off")
            base.copy(on = false)
        }
        Strips.save(this, all + s)
        openEditor(id)
    }

    private fun showAll(show: Boolean) {
        Strips.save(this, Strips.load(this).map { it.copy(shown = show) })
        refreshSummary(Strips.load(this), animate = true)
        snack(if (show) "Previews shown" else "Previews hidden")
    }

    // ---- settings ----

    private fun iconVisible() =
        packageManager.getComponentEnabledSetting(alias) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    private fun setIcon(show: Boolean) {
        packageManager.setComponentEnabledSetting(
            alias,
            if (show) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
    }

    private fun bindSettings() {
        val sp = Cfg.sp(this)

        findViewById<View>(R.id.rowAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<View>(R.id.rowRestricted).setOnClickListener { openAppInfo() }
        findViewById<View>(R.id.rowHelp).setOnClickListener { showHelp() }
        findViewById<View>(R.id.rowDev).setOnClickListener { openUrl("https://github.com/sudoflash01") }
        findViewById<View>(R.id.rowStar).setOnClickListener { openUrl("https://github.com/sudoflash01/EdgeVolume") }

        // dynamic colour on/off. if phone cant do it, switch stays off and default colours are used
        val dynSwitch = findViewById<MaterialSwitch>(R.id.dynSwitch)
        val dynSupport = findViewById<TextView>(R.id.dynSupport)
        val dynIcon = findViewById<ImageView>(R.id.dynIcon)
        if (DynamicColors.isDynamicColorAvailable()) {
            dynSwitch.isChecked = Cfg.dynamicWanted(this)
            dynSupport.text = if (dynSwitch.isChecked) "Wallpaper colours" else "Default colours"
            Anim.leading(dynIcon, dynSwitch.isChecked, animate = false)
            dynSwitch.setOnCheckedChangeListener { _, c ->
                sp.edit().putBoolean(Cfg.DYN, c).apply()
                dynSupport.text = if (c) "Wallpaper colours" else "Default colours"
                Anim.leading(dynIcon, c)
                Anim.pop(dynIcon)
                // wait for switch animation to finish, then recreate with new colours
                dynSwitch.postDelayed({ if (!isFinishing) recreate() }, 320)
            }
            findViewById<View>(R.id.rowDynamic).setOnClickListener { dynSwitch.toggle() }
        } else {
            dynSwitch.isChecked = false
            dynSwitch.isEnabled = false
            dynSupport.text = "Not available, using default colours"
            Anim.leading(dynIcon, false, animate = false)
        }

        // vibrate, icon shows the state
        val vibIcon = findViewById<ImageView>(R.id.vibrateIcon)
        val vib = findViewById<MaterialSwitch>(R.id.vibrateSwitch)
        vib.isChecked = sp.getBoolean(Cfg.VIB, true)
        fun vibVisual(on: Boolean, animate: Boolean) {
            Anim.swapIcon(vibIcon, if (on) R.drawable.ic_vibration else R.drawable.ic_vibration_off, animate)
            Anim.leading(vibIcon, on, animate)
        }
        vibVisual(vib.isChecked, animate = false)
        vib.setOnCheckedChangeListener { _, c ->
            sp.edit().putBoolean(Cfg.VIB, c).apply()
            vibVisual(c, animate = true)
        }
        findViewById<View>(R.id.rowVibrate).setOnClickListener { vib.toggle() }

        // hide app icon, eye closed = hidden
        val iconIcon = findViewById<ImageView>(R.id.iconIcon)
        val iconSwitch = findViewById<MaterialSwitch>(R.id.iconSwitch)
        fun iconVisual(hidden: Boolean, animate: Boolean) {
            Anim.swapIcon(iconIcon, if (hidden) R.drawable.ic_visibility_off else R.drawable.ic_visibility, animate)
            Anim.leading(iconIcon, hidden, animate)
        }
        iconSwitch.isChecked = !iconVisible()
        iconVisual(iconSwitch.isChecked, animate = false)
        fun revert() { guard = true; iconSwitch.isChecked = false; guard = false }
        iconSwitch.setOnCheckedChangeListener { _, hide ->
            if (guard) return@setOnCheckedChangeListener
            if (!hide) { setIcon(true); iconVisual(false, true); return@setOnCheckedChangeListener }
            MaterialAlertDialogBuilder(this)
                .setTitle("Hide app icon?")
                .setMessage("Open it later from Accessibility > Edge Action > Settings.")
                .setPositiveButton("Hide") { _, _ -> setIcon(false); iconVisual(true, true) }
                .setNegativeButton("Cancel") { _, _ -> revert() }
                .setOnCancelListener { revert() }
                .show()
        }
        findViewById<View>(R.id.rowIcon).setOnClickListener { iconSwitch.toggle() }

        val v = try { packageManager.getPackageInfo(packageName, 0).versionName } catch (e: Exception) { "" }
        findViewById<TextView>(R.id.version).text = "Edge Action $v"
    }
}
