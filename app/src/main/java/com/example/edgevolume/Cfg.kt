package com.example.edgevolume

import android.content.Context

// all settings keys at one place, service + screen both use this
object Cfg {
    const val Y = "ypm"      // vertical pos, 0..1000 (0.1% steps)
    const val H = "h"        // height dp
    const val W = "w"        // width dp
    const val SHOW = "show"  // red strip on/off
    const val LEFT = "left"  // left edge instead of right
    const val VIB = "vib"    // vibrate on trigger

    const val DEF_Y = 500
    const val DEF_H = 150
    const val DEF_W = 24

    fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
}
