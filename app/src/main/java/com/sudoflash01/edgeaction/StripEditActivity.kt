package com.sudoflash01.edgeaction

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.LayerDrawable
import android.animation.ValueAnimator
import android.transition.AutoTransition
import android.transition.TransitionManager
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.R as MR
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.progressindicator.LinearProgressIndicator
import com.google.android.material.slider.Slider
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputEditText
import java.util.Locale

// one strip, one screen: gestures, position and size, appearance
class StripEditActivity : BaseActivity() {

    companion object { const val EXTRA_ID = "strip_id" }

    private var id = -1
    private var updating = false // true when we fill widgets from code, so listeners dont fire
    private var firstRefresh = true // no animation when screen is filled first time
    private var dotColor = 0
    private var popColor = false    // next colour grid will pop the chosen colour a little

    private lateinit var root: View
    private lateinit var enabledIcon: ImageView
    private lateinit var shownIcon: ImageView
    private lateinit var swipeIcon: ImageView
    private lateinit var alongIcon: ImageView
    private lateinit var vibIcon: ImageView
    private lateinit var vibSub: TextView
    private lateinit var vibSwitch: MaterialSwitch
    private lateinit var headerDot: View
    private lateinit var enabledSwitch: MaterialSwitch
    private lateinit var shownSwitch: MaterialSwitch
    private lateinit var nameInput: TextInputEditText
    private lateinit var actionValue: TextView
    private lateinit var alongGroup: MaterialButtonToggleGroup
    private lateinit var streamLayout: View
    private lateinit var streamDrop: MaterialAutoCompleteTextView
    private lateinit var edgeGroup: MaterialButtonToggleGroup
    private lateinit var budgetBar: LinearProgressIndicator
    private lateinit var budgetText: TextView
    private lateinit var notice: TextView
    private lateinit var colorGrid: LinearLayout
    private lateinit var posRow: Row
    private lateinit var heightRow: Row
    private lateinit var widthRow: Row

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun get(): Strip? = Strips.load(this).firstOrNull { it.id == id }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        id = intent.getIntExtra(EXTRA_ID, -1)
        val first = get()
        if (first == null) { finish(); return }
        setContentView(R.layout.activity_strip_edit)

        root = findViewById(R.id.root)
        enabledIcon = findViewById(R.id.enabledIcon)
        shownIcon = findViewById(R.id.shownIcon)
        swipeIcon = findViewById(R.id.swipeIcon)
        alongIcon = findViewById(R.id.alongIcon)
        vibIcon = findViewById(R.id.vibIcon)
        vibSub = findViewById(R.id.vibSub)
        vibSwitch = findViewById(R.id.vibSwitch)
        headerDot = findViewById(R.id.headerDot)
        enabledSwitch = findViewById(R.id.enabledSwitch)
        shownSwitch = findViewById(R.id.shownSwitch)
        nameInput = findViewById(R.id.nameInput)
        actionValue = findViewById(R.id.actionValue)
        alongGroup = findViewById(R.id.alongGroup)
        streamLayout = findViewById(R.id.streamLayout)
        streamDrop = findViewById(R.id.streamDrop)
        edgeGroup = findViewById(R.id.edgeGroup)
        budgetBar = findViewById(R.id.budgetBar)
        budgetText = findViewById(R.id.budgetText)
        notice = findViewById(R.id.notice)
        colorGrid = findViewById(R.id.colorGrid)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        toolbar.setNavigationOnClickListener { finish() }
        toolbar.setOnMenuItemClickListener {
            if (it.itemId == R.id.action_delete) { confirmDelete(); true } else false
        }

        nameInput.setText(first.name)
        nameInput.setOnFocusChangeListener { _, has -> if (!has) saveName() }
        nameInput.setOnEditorActionListener { v, _, _ -> v.clearFocus(); false }

        enabledSwitch.setOnCheckedChangeListener { _, c ->
            if (updating) return@setOnCheckedChangeListener
            Strips.setOn(this, id, c)?.let { snack(it) }
            enabledSwitch.post { refresh() }
        }
        shownSwitch.setOnCheckedChangeListener { _, c ->
            if (!updating) change { it.copy(shown = c) }
        }

        vibSwitch.setOnCheckedChangeListener { _, c ->
            if (!updating) { change { it.copy(vib = c) }; refresh(keepNotice = true) }
        }
        findViewById<View>(R.id.vibRow).setOnClickListener { vibSwitch.toggle() }

        findViewById<View>(R.id.swipeRow).setOnClickListener { chooseAction() }
        findViewById<View>(R.id.enabledRow).setOnClickListener { enabledSwitch.toggle() }
        findViewById<View>(R.id.shownRow).setOnClickListener { shownSwitch.toggle() }

        alongGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updating || !isChecked) return@addOnButtonCheckedListener
            val a = when (checkedId) { R.id.alongPanel -> 1; R.id.alongSlide -> 2; else -> 0 }
            alongGroup.post { change { it.copy(along = a) }; refresh() }
        }
        streamDrop.setSimpleItems(Acts.streams.map { it.first }.toTypedArray())
        streamDrop.setOnItemClickListener { _, _, pos, _ ->
            val st = Acts.streams[pos].second
            change { it.copy(alongStream = st) }
        }

        edgeGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (updating || !isChecked) return@addOnButtonCheckedListener
            edgeGroup.post { moveEdge(checkedId == R.id.edgeRight) }
        }

        val box = findViewById<LinearLayout>(R.id.slidersBox)
        posRow = Row(box, "Position", 1, { it.ypm }, { st, v -> st.copy(ypm = v) }) {
            String.format(Locale.US, "%.1f%%", it / 10.0)
        }
        heightRow = Row(box, "Height", 1, { it.h }, { st, v -> st.copy(h = v) }) { "$it dp" }
        widthRow = Row(box, "Width", 1, { it.w }, { st, v -> st.copy(w = v) }) { "$it dp" }

        refresh()
    }

    override fun onPause() {
        saveName()
        super.onPause()
    }

    private fun snack(msg: String) = Snackbar.make(root, msg, Snackbar.LENGTH_LONG).show()

    private fun showNotice(msg: String) {
        notice.text = msg
        if (notice.visibility != View.VISIBLE) {
            TransitionManager.beginDelayedTransition(notice.parent as ViewGroup, AutoTransition())
            notice.visibility = View.VISIBLE
        }
    }

    private fun hideNotice() {
        if (notice.visibility == View.GONE) return
        TransitionManager.beginDelayedTransition(notice.parent as ViewGroup, AutoTransition())
        notice.visibility = View.GONE
    }

    private fun saveName() {
        if (!::nameInput.isInitialized) return
        val n = nameInput.text?.toString()?.trim().orEmpty()
        val cur = get() ?: return
        if (n.isNotEmpty() && n != cur.name) change { it.copy(name = n) }
    }

    // apply the edit only if overlap / 200dp rules are ok. if refused, reason shows under sliders
    private fun change(edit: (Strip) -> Strip): Boolean {
        val all = Strips.load(this)
        val old = all.firstOrNull { it.id == id } ?: return false
        val new = edit(old)
        val err = Strips.conflict(all, new, Strips.refDp(this))
        if (err != null) { showNotice(err); return false }
        Strips.save(this, all.map { if (it.id == id) new else it })
        hideNotice()
        return true
    }

    // ---- screen state ----

    private fun refresh(keepNotice: Boolean = false) {
        val all = Strips.load(this)
        val s = all.firstOrNull { it.id == id } ?: run { finish(); return }
        val was = updating
        updating = true
        val anim = !firstRefresh
        firstRefresh = false
        if (!keepNotice) hideNotice()

        setDotColor(s.color, anim)
        enabledSwitch.isChecked = s.on
        shownSwitch.isChecked = s.shown
        // icons follow the state, tonal circle + icon change
        Anim.leading(enabledIcon, s.on, anim)
        Anim.swapIcon(shownIcon, if (s.shown) R.drawable.ic_visibility else R.drawable.ic_visibility_off, anim)
        Anim.leading(shownIcon, s.shown, anim)
        Anim.swapIcon(swipeIcon, Acts.icon(s.swipeIn), anim)
        Anim.leading(swipeIcon, s.swipeIn.kind != "none", anim)
        actionValue.text = Acts.describe(s.swipeIn)
        Anim.leading(alongIcon, true, anim) // same filled look like other icons

        vibSwitch.isChecked = s.vib
        val globalVib = Cfg.sp(this).getBoolean(Cfg.VIB, true)
        vibSub.text = if (globalVib) "Buzz when a swipe starts" else "Off for all strips in Settings"
        Anim.swapIcon(vibIcon, if (s.vib) R.drawable.ic_vibration else R.drawable.ic_vibration_off, anim)
        Anim.leading(vibIcon, s.vib, anim)
        alongGroup.check(when (s.along) { 1 -> R.id.alongPanel; 2 -> R.id.alongSlide; else -> R.id.alongOff })
        val wantStream = if (s.along != 0) View.VISIBLE else View.GONE
        if (streamLayout.visibility != wantStream) {
            if (anim) TransitionManager.beginDelayedTransition(streamLayout.parent as ViewGroup, AutoTransition())
            streamLayout.visibility = wantStream
        }
        streamDrop.setText(Acts.streamName(s.alongStream), false)
        edgeGroup.check(if (s.right) R.id.edgeRight else R.id.edgeLeft)

        // how much of this edge's 200 dp is left for this strip
        val edge = if (s.right) "Right" else "Left"
        val others = Strips.usedByOthers(all, s.id, s.right)
        val room = Strips.roomOn(all, s.id, s.right)
        budgetBar.setProgressCompat((others + if (s.on) s.h else 0).coerceAtMost(Strips.MAX_EDGE_DP), false)
        budgetText.text = if (room < Strips.MIN_H) {
            "$edge edge is full. Use the other edge or shrink a strip."
        } else {
            "$edge edge: ${others + if (s.on) s.h else 0} / ${Strips.MAX_EDGE_DP} dp used, up to $room dp tall"
        }

        // height slider cant go more than free space, so user never gets "not enough room"
        posRow.setRange(0, 1000, s.ypm)
        heightRow.setRange(Strips.MIN_H, minOf(Strips.MAX_EDGE_DP, room), s.h)
        widthRow.setRange(Strips.MIN_W, Strips.MAX_W, s.w)

        buildColors(all, s)
        updating = was
    }

    // header circle slowly changes to new picked colour
    private fun setDotColor(c: Int, animate: Boolean) {
        val bg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(if (dotColor == 0) c else dotColor) }
        headerDot.background = bg
        val from = if (dotColor == 0) c else dotColor
        dotColor = c
        if (!animate || from == c) { bg.setColor(c); return }
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 300
            interpolator = Anim.emphasized
            addUpdateListener { bg.setColor(ColorUtils.blendARGB(from, c, it.animatedFraction)) }
        }.start()
    }

    private fun moveEdge(toRight: Boolean) {
        val all = Strips.load(this)
        val cur = all.firstOrNull { it.id == id } ?: return
        if (cur.right == toRight) return
        val edge = if (toRight) "right" else "left"
        val moved = Strips.moveToEdge(all, cur, toRight, Strips.refDp(this))
        if (moved == null) {
            val room = Strips.roomOn(all, id, toRight)
            showNotice(
                if (room < Strips.MIN_H)
                    "No room on the $edge edge: strips there already use ${Strips.MAX_EDGE_DP - room} of " +
                        "${Strips.MAX_EDGE_DP} dp. Shrink one of them first."
                else "No gap on the $edge edge that is big enough without overlapping another strip."
            )
            refresh(keepNotice = true)
        } else {
            Strips.save(this, all.map { if (it.id == id) moved else it })
            if (moved.h != cur.h) snack("Height reduced to ${moved.h} dp to fit on the $edge edge.")
            refresh()
        }
    }

    // ---- slider row: label, value, slider, fine +/- buttons ----

    private inner class Row(
        container: LinearLayout,
        title: String,
        private val unitStep: Int,
        private val read: (Strip) -> Int,
        private val write: (Strip, Int) -> Strip,
        private val fmt: (Int) -> String
    ) {
        private val view: View = layoutInflater.inflate(R.layout.row_slider, container, false)
        private val value: TextView = view.findViewById(R.id.value)
        private val slider: Slider = view.findViewById(R.id.slider)
        private val minus: View = view.findViewById(R.id.minus)
        private val plus: View = view.findViewById(R.id.plus)

        init {
            view.findViewById<TextView>(R.id.label).text = title
            container.addView(view)
            slider.setLabelFormatter { fmt(it.toInt()) }
            slider.addOnChangeListener { _, v, fromUser -> if (fromUser && !updating) commit(v.toInt()) }
            slider.addOnSliderTouchListener(object : Slider.OnSliderTouchListener {
                override fun onStartTrackingTouch(slider: Slider) {}
                override fun onStopTrackingTouch(slider: Slider) { refresh(keepNotice = true) }
            })
            minus.setOnClickListener { commit(current() - unitStep); refresh(keepNotice = true) }
            plus.setOnClickListener { commit(current() + unitStep); refresh(keepNotice = true) }
        }

        private fun current(): Int = get()?.let(read) ?: 0

        private fun commit(v: Int) {
            val c = v.coerceIn(slider.valueFrom.toInt(), slider.valueTo.toInt())
            if (change { write(it, c) }) {
                value.text = fmt(c)
            } else { // refused, go back to saved value
                val cur = current()
                setSlider(cur)
                value.text = fmt(cur)
            }
        }

        private fun setSlider(v: Int) {
            val was = updating
            updating = true
            slider.value = v.coerceIn(slider.valueFrom.toInt(), slider.valueTo.toInt()).toFloat()
            updating = was
        }

        fun setRange(lo: Int, hi: Int, v: Int) {
            val usable = hi > lo
            slider.isEnabled = usable
            minus.isEnabled = usable
            plus.isEnabled = usable
            val was = updating
            updating = true
            slider.valueFrom = lo.toFloat()
            slider.valueTo = (if (usable) hi else lo + 1).toFloat() // a slider needs from < to
            slider.value = v.coerceIn(lo, if (usable) hi else lo).toFloat()
            updating = was
            value.text = fmt(v)
        }
    }

    // ---- colour ----

    // every strip has own colour, only colours not used by others are shown
    // 5 equal columns so grid is in centre and lines up with card
    private fun buildColors(all: List<Strip>, s: Strip) {
        colorGrid.removeAllViews()
        val colors = (listOf(s.color) + Strips.freeColors(all, s.id)).distinct().take(15)
        colors.chunked(5).forEach { rowColors ->
            val r = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            }
            for (i in 0 until 5) {
                val cell = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(56), 1f)
                }
                rowColors.getOrNull(i)?.let { c -> cell.addView(swatch(c, c == s.color)) }
                r.addView(cell)
            }
            colorGrid.addView(r)
        }
    }

    private fun swatch(c: Int, selected: Boolean): View = ImageView(this).apply {
        val fill = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c) }
        background = if (selected) {
            val ring = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.TRANSPARENT)
                setStroke(dp(3), MaterialColors.getColor(this@StripEditActivity, MR.attr.colorOnSurface, Color.WHITE))
            }
            LayerDrawable(arrayOf(ring, fill)).also { it.setLayerInset(1, dp(5), dp(5), dp(5), dp(5)) }
        } else fill
        contentDescription = if (selected) "Selected colour" else "Pick this colour"
        if (selected) {
            setImageResource(R.drawable.ic_check)
            val onColor = if (ColorUtils.calculateLuminance(c) > 0.5) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            imageTintList = ColorStateList.valueOf(onColor)
            setPadding(dp(13), dp(13), dp(13), dp(13))
        }
        layoutParams = FrameLayout.LayoutParams(dp(48), dp(48), Gravity.CENTER)
        setOnClickListener {
            popColor = true
            if (change { it.copy(color = c) }) refresh() else popColor = false
        }
        if (selected && popColor) { popColor = false; post { Anim.pop(this) } }
    }

    // ---- delete ----

    private fun confirmDelete() {
        val s = get() ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete \"${s.name}\"?")
            .setMessage("The strip and its settings are removed.")
            .setPositiveButton("Delete") { _, _ ->
                Strips.save(this, Strips.load(this).filter { it.id != id })
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ---- action pickers ----

    // bottom sheet with list items: icon, label, tick on current one
    private fun chooseAction() {
        val s = get() ?: return
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_actions, null)
        val box = view.findViewById<LinearLayout>(R.id.actionList)
        val selected = Acts.indexOf(s.swipeIn)
        Acts.choices.forEachIndexed { i, c ->
            val row = layoutInflater.inflate(R.layout.item_action, box, false)
            row.findViewById<TextView>(R.id.label).text = c.label
            val icon = row.findViewById<ImageView>(R.id.icon)
            icon.setImageResource(Acts.icon(c.kind, c.arg))
            Anim.leading(icon, i == selected, animate = false)
            row.findViewById<View>(R.id.check).visibility = if (i == selected) View.VISIBLE else View.INVISIBLE
            row.setOnClickListener { sheet.dismiss(); onActionChosen(c, s) }
            box.addView(row)
        }
        sheet.setContentView(view)
        sheet.setOnShowListener { sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED }
        sheet.show()
    }

    private fun onActionChosen(c: Acts.Choice, s: Strip) {
        if (!c.ask) { setAction(Act(c.kind, c.arg)); return }
        when (c.kind) {
            "vol" -> pickStream { st -> setAction(Act("vol", st.toString())) }
            "app" -> pickApp { pkg, label -> setAction(Act("app", pkg, label)) }
            "url" -> askUrl(s)
            "intent" -> askIntent(s)
        }
    }

    private fun setAction(a: Act) {
        change { it.copy(swipeIn = a) }
        refresh(keepNotice = true)
    }

    private fun pickStream(onPick: (Int) -> Unit) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Which volume?")
            .setItems(Acts.streams.map { it.first }.toTypedArray()) { _, i -> onPick(Acts.streams[i].second) }
            .show()
    }

    private fun pickApp(onPick: (String, String) -> Unit) {
        val pm = packageManager
        val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = pm.queryIntentActivities(q, 0)
            .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
        MaterialAlertDialogBuilder(this)
            .setTitle("Open which app?")
            .setItems(apps.map { it.first }.toTypedArray()) { _, i -> onPick(apps[i].second, apps[i].first) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun askUrl(s: Strip) {
        val view = layoutInflater.inflate(R.layout.dialog_text, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        if (s.swipeIn.kind == "url") input.setText(s.swipeIn.arg)
        MaterialAlertDialogBuilder(this)
            .setTitle("Web link")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                var u = input.text?.toString()?.trim().orEmpty()
                if (u.isEmpty()) return@setPositiveButton
                if (!u.contains(":")) u = "https://$u"
                setAction(Act("url", u))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // anything else: intent URI + how to fire it. this is for "any app / any service"
    private fun askIntent(s: Strip) {
        val view = layoutInflater.inflate(R.layout.dialog_intent, null)
        val input = view.findViewById<TextInputEditText>(R.id.input)
        val group = view.findViewById<RadioGroup>(R.id.kindGroup)
        if (s.swipeIn.kind == "intent") input.setText(s.swipeIn.arg)
        group.check(when (s.swipeIn.arg2) {
            "service" -> R.id.rbService
            "broadcast" -> R.id.rbBroadcast
            else -> R.id.rbActivity
        })
        MaterialAlertDialogBuilder(this)
            .setTitle("Custom intent")
            .setView(view)
            .setPositiveButton("OK") { _, _ ->
                val uri = input.text?.toString()?.trim().orEmpty()
                if (uri.isEmpty()) return@setPositiveButton
                try {
                    Intent.parseUri(uri, Intent.URI_INTENT_SCHEME)
                } catch (t: Exception) {
                    snack("That is not a valid intent URI")
                    return@setPositiveButton
                }
                val kind = when (group.checkedRadioButtonId) {
                    R.id.rbService -> "service"
                    R.id.rbBroadcast -> "broadcast"
                    else -> "activity"
                }
                setAction(Act("intent", uri, kind))
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
