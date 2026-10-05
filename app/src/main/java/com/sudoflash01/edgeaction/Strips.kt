package com.sudoflash01.edgeaction

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

// what a gesture does. kind: none | vol | app | url | intent | torch | media | global
data class Act(val kind: String = "none", val arg: String = "", val arg2: String = "") {
    fun toJson(): JSONObject = JSONObject().put("k", kind).put("a", arg).put("b", arg2)

    companion object {
        fun from(o: JSONObject?): Act =
            if (o == null) Act() else Act(o.optString("k", "none"), o.optString("a"), o.optString("b"))
    }
}

// one touch strip on screen edge
data class Strip(
    val id: Int,
    val name: String,
    val on: Boolean = true,          // strip is on screen (window added)
    val shown: Boolean = true,       // preview colour visible. false = invisible but still works
    val right: Boolean = true,       // right edge, else left
    val ypm: Int = 500,              // centre position, 0..1000 (0.1% steps)
    val h: Int = 100,                // in dp
    val w: Int = 24,                 // in dp
    val color: Int,                  // colour (ARGB, no transparency)
    val swipeIn: Act = Act("none"),  // swipe inward from the strip
    val along: Int = 0,              // up/down along the strip: 0 off, 1 show volume panel, 2 slide to change volume
    val alongStream: Int = 3,        // which volume stream the up/down gesture uses
    val vib: Boolean = true          // vibrate when swipe starts (global switch should be on also)
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("on", on).put("shown", shown).put("right", right)
        .put("y", ypm).put("h", h).put("w", w).put("c", color)
        .put("in", swipeIn.toJson()).put("al", along).put("as", alongStream).put("vb", vib)

    companion object {
        fun from(o: JSONObject): Strip = Strip(
            id = o.getInt("id"),
            name = o.optString("name", "Strip"),
            on = o.optBoolean("on", true),
            shown = o.optBoolean("shown", true),
            right = o.optBoolean("right", true),
            ypm = o.optInt("y", 500).coerceIn(0, 1000),
            h = o.optInt("h", 100).coerceIn(Strips.MIN_H, Strips.MAX_EDGE_DP),
            w = o.optInt("w", 24).coerceIn(Strips.MIN_W, Strips.MAX_W),
            color = o.optInt("c", Strips.rgb(255, 64, 64)),
            swipeIn = Act.from(o.optJSONObject("in")),
            along = o.optInt("al", 0).coerceIn(0, 2),
            alongStream = o.optInt("as", 3),
            vib = o.optBoolean("vb", true)
        )
    }
}

// saving + rules so strips dont overlap each other
object Strips {
    const val KEY = "strips"
    const val MIN_H = 40
    const val MIN_W = 4
    const val MAX_W = 60

    // android gives only ~200dp back-gesture exclusion per edge
    const val MAX_EDGE_DP = 200

    fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    val PALETTE: List<Int> = listOf(
        rgb(255, 64, 64),    // red
        rgb(66, 133, 244),   // blue
        rgb(52, 168, 83),    // green
        rgb(251, 140, 0),    // orange
        rgb(171, 71, 188),   // purple
        rgb(0, 188, 212),    // cyan
        rgb(255, 214, 0),    // yellow
        rgb(240, 98, 146),   // pink
        rgb(38, 166, 154),   // teal
        rgb(156, 204, 101),  // lime
        rgb(141, 110, 99),   // brown
        rgb(120, 144, 156)   // grey-blue
    )

    // ---- colours ----

    private fun hsv(h: Float, s: Float, v: Float): Int {
        val c = v * s
        val x = c * (1 - Math.abs((h / 60f) % 2 - 1))
        val m = v - c
        val (r, g, b) = when {
            h < 60 -> Triple(c, x, 0f)
            h < 120 -> Triple(x, c, 0f)
            h < 180 -> Triple(0f, c, x)
            h < 240 -> Triple(0f, x, c)
            h < 300 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        return rgb(((r + m) * 255).toInt(), ((g + m) * 255).toInt(), ((b + m) * 255).toInt())
    }

    // how far 2 colours are from each other (redmean formula). 0 = same, ~765 = black vs white
    private fun distance(a: Int, b: Int): Double {
        val r1 = (a shr 16) and 0xFF; val g1 = (a shr 8) and 0xFF; val b1 = a and 0xFF
        val r2 = (b shr 16) and 0xFF; val g2 = (b shr 8) and 0xFF; val b2 = b and 0xFF
        val rm = (r1 + r2) / 2.0
        val dr = (r1 - r2).toDouble(); val dg = (g1 - g2).toDouble(); val db = (b1 - b2).toDouble()
        return Math.sqrt((2 + rm / 256) * dr * dr + 4 * dg * dg + (2 + (255 - rm) / 256) * db * db)
    }

    // if a colour is too close to a used one it looks same, so count it as used
    private const val SAME_LOOK = 110.0
    // colours we show should also be different from each other
    private const val OFFER_GAP = 70.0

    // colours which are still free: not used by other strips (except [exceptId]) and not too close to used ones.
    // palette first, then extra hues, each one clearly different from before
    fun freeColors(all: List<Strip>, exceptId: Int = -1): List<Int> {
        val used = all.filter { it.id != exceptId }.map { it.color }
        val extra = (0 until 360 step 15).map { hsv(it.toFloat(), 0.7f, 0.95f) }
        val picked = ArrayList<Int>()
        for (c in PALETTE + extra) {
            if (used.any { distance(it, c) < SAME_LOOK }) continue
            if (picked.any { distance(it, c) < OFFER_GAP }) continue
            picked.add(c)
        }
        return picked
    }

    // colour no other strip uses. if all are taken, pick the one most different from used ones
    fun nextColor(all: List<Strip>): Int {
        freeColors(all).firstOrNull()?.let { return it }
        val used = all.map { it.color }
        return PALETTE.maxByOrNull { c -> used.minOf { distance(it, c) } } ?: PALETTE[0]
    }

    // ---- geometry rules ----

    fun topDp(s: Strip, refDp: Int): Float {
        val centre = refDp * s.ypm / 1000f
        val maxTop = (refDp - s.h).coerceAtLeast(0).toFloat()
        return (centre - s.h / 2f).coerceIn(0f, maxTop)
    }

    fun overlap(a: Strip, b: Strip, refDp: Int): Boolean {
        if (a.right != b.right) return false
        val at = topDp(a, refDp)
        val bt = topDp(b, refDp)
        return at < bt + b.h && bt < at + a.h
    }

    // dp already used on one edge by other enabled strips (all except [id])
    fun usedByOthers(all: List<Strip>, id: Int, right: Boolean): Int =
        all.filter { it.id != id && it.on && it.right == right }.sumOf { it.h }

    // dp still free on an edge for strip [id]. 200dp is TOTAL per edge, all strips share it
    fun roomOn(all: List<Strip>, id: Int, right: Boolean): Int =
        (MAX_EDGE_DP - usedByOthers(all, id, right)).coerceAtLeast(0)

    // dp used on an edge by all enabled strips
    fun edgeUsed(all: List<Strip>, right: Boolean): Int =
        all.filter { it.on && it.right == right }.sumOf { it.h }

    // reason why [s] cant be active with other strips, null if its fine
    fun conflict(all: List<Strip>, s: Strip, refDp: Int): String? {
        if (!s.on) return null
        val others = all.filter { it.id != s.id && it.on && it.right == s.right }
        val used = others.sumOf { it.h }
        if (used + s.h > MAX_EDGE_DP) {
            val room = (MAX_EDGE_DP - used).coerceAtLeast(0)
            val edge = if (s.right) "right" else "left"
            val who = others.joinToString { "\"${it.name}\"" }
            val fix = if (room >= MIN_H) "Make this strip $room dp or smaller, or use the other edge."
                      else "Use the other edge, or shrink another strip."
            return "Only $room dp is left on the $edge edge. Android allows $MAX_EDGE_DP dp per edge " +
                "in total, shared by all strips, and $who already ${if (others.size == 1) "uses" else "use"} $used dp. $fix"
        }
        others.firstOrNull { overlap(s, it, refDp) }?.let { return "Overlaps \"${it.name}\". Move it up or down." }
        return null
    }

    // find centre position (permille) on same edge where s fits, nearest to current one
    fun findSpot(all: List<Strip>, s: Strip, refDp: Int): Int? {
        val candidates = (0..1000 step 10).sortedBy { Math.abs(it - s.ypm) }
        return candidates.firstOrNull { conflict(all, s.copy(ypm = it, on = true), refDp) == null }
    }

    // put s at a valid place. same edge first, then other edge, then try smaller height.
    // returns null if no space at all
    fun place(all: List<Strip>, s: Strip, refDp: Int): Strip? {
        val heights = listOf(s.h, 100, 70, MIN_H).filter { it <= s.h }.distinct()
        for (h in heights) {
            for (right in listOf(s.right, !s.right)) {
                val t = s.copy(h = h, right = right, on = true)
                val spot = findSpot(all, t, refDp)
                if (spot != null) return t.copy(ypm = spot)
            }
        }
        return null
    }

    // move s to other edge. make it smaller if needed, then find a spot. null if not possible
    fun moveToEdge(all: List<Strip>, s: Strip, right: Boolean, refDp: Int): Strip? {
        val h = minOf(s.h, roomOn(all, s.id, right))
        if (h < MIN_H) return null
        val t = s.copy(right = right, h = h)
        val spot = findSpot(all, t, refDp) ?: return null
        return t.copy(ypm = spot)
    }

    // switch strip on/off and save. returns message to show user, or null
    fun setOn(c: Context, id: Int, on: Boolean): String? {
        val all = load(c)
        val s = all.firstOrNull { it.id == id } ?: return null
        if (!on) {
            save(c, all.map { if (it.id == id) it.copy(on = false) else it })
            return null
        }
        val ref = refDp(c)
        val t = s.copy(on = true)
        if (conflict(all, t, ref) == null) {
            save(c, all.map { if (it.id == id) t else it })
            return null
        }
        val moved = place(all, t, ref)
            ?: return "No free room for this strip. Shrink or switch off another strip first."
        save(c, all.map { if (it.id == id) moved else it })
        return "Its old spot was taken, so it moved to a free spot."
    }

    // ---- storage ----

    // screen height (dp) for overlap check. always the long side so portrait rules work
    fun refDp(c: Context): Int {
        val m = c.resources.displayMetrics
        return (maxOf(m.widthPixels, m.heightPixels) / m.density).toInt()
    }

    fun load(c: Context): List<Strip> {
        val sp = Cfg.sp(c)
        val raw = sp.getString(KEY, null)
        if (raw == null) {
            // first run of this version: turn the old single-strip settings into strip #1
            val first = Strip(
                id = 1,
                name = "Volume",
                shown = sp.getBoolean(Cfg.SHOW, true),
                right = !sp.getBoolean(Cfg.LEFT, false),
                ypm = sp.getInt(Cfg.Y, Cfg.DEF_Y).coerceIn(0, 1000),
                h = sp.getInt(Cfg.H, Cfg.DEF_H).coerceIn(MIN_H, MAX_EDGE_DP),
                w = sp.getInt(Cfg.W, Cfg.DEF_W).coerceIn(MIN_W, MAX_W),
                color = PALETTE[0],
                swipeIn = Act("vol", "3")
            )
            save(c, listOf(first))
            return listOf(first)
        }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Strip.from(arr.getJSONObject(it)) }.sortedBy { it.id }
        } catch (t: Throwable) {
            emptyList()
        }
    }

    fun save(c: Context, list: List<Strip>) {
        val arr = JSONArray()
        list.sortedBy { it.id }.forEach { arr.put(it.toJson()) }
        Cfg.sp(c).edit().putString(KEY, arr.toString()).apply()
    }
}
