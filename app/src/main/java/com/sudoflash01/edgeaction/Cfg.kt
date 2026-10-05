package com.sudoflash01.edgeaction

import android.content.Context

// all setting keys here, service and screens both use these
object Cfg {
    const val Y = "ypm"      // vertical position, 0 to 1000 (0.1% steps)
    const val H = "h"        // height in dp
    const val W = "w"        // width in dp
    const val SHOW = "show"  // show strip preview on/off
    const val LEFT = "left"  // use left edge instead of right
    const val VIB = "vib"    // vibrate when swipe works
    const val DYN = "dyn"    // dynamic colour (material you) on/off, default is off

    const val DEF_Y = 500
    const val DEF_H = 150
    const val DEF_W = 24

    // true if user wants wallpaper colours. if this is off,
    // or phone cant do dynamic colour (android 11 and below, some roms), default colours are used
    fun dynamicWanted(c: Context) = sp(c).getBoolean(DYN, false)

    fun sp(c: Context) = c.getSharedPreferences("cfg", Context.MODE_PRIVATE)
}
