package com.sudoflash01.edgeaction

// all things a swipe can do + how they show in ui
object Acts {

    class Choice(val label: String, val kind: String, val arg: String = "", val ask: Boolean = false)

    val choices: List<Choice> = listOf(
        Choice("Nothing", "none"),
        Choice("Show volume panel...", "vol", ask = true),
        Choice("Open an app...", "app", ask = true),
        Choice("Open a web link...", "url", ask = true),
        Choice("Custom intent: activity / service / broadcast...", "intent", ask = true),
        Choice("Flashlight on / off", "torch"),
        Choice("Media: play / pause", "media", "play"),
        Choice("Media: next track", "media", "next"),
        Choice("Media: previous track", "media", "prev"),
        Choice("System: Back", "global", "back"),
        Choice("System: Home", "global", "home"),
        Choice("System: Recent apps", "global", "recents"),
        Choice("System: Notifications", "global", "notifications"),
        Choice("System: Quick settings", "global", "quick"),
        Choice("System: Power menu", "global", "power"),
        Choice("System: Lock screen", "global", "lock"),
        Choice("System: Screenshot", "global", "screenshot")
    )

    // AudioManager stream ids: media 3, ring 2, alarm 4, notification 5, call 0, system 1
    val streams: List<Pair<String, Int>> = listOf(
        "Media" to 3, "Ring" to 2, "Alarm" to 4, "Notification" to 5, "Call" to 0, "System" to 1
    )

    fun streamName(id: Int) = streams.firstOrNull { it.second == id }?.first ?: "Media"

    val alongLabels = listOf("Off", "Show volume panel", "Slide to change volume")

    fun indexOf(a: Act): Int {
        val i = choices.indexOfFirst {
            it.kind == a.kind && (it.ask || it.arg == a.arg)
        }
        return if (i < 0) 0 else i
    }

    // icon for the action in lists
    fun icon(kind: String, arg: String = ""): Int = when (kind) {
        "none" -> R.drawable.ic_block
        "vol" -> R.drawable.ic_volume
        "app" -> R.drawable.ic_apps
        "url" -> R.drawable.ic_link
        "intent" -> R.drawable.ic_code
        "torch" -> R.drawable.ic_bolt
        "media" -> when (arg) {
            "next" -> R.drawable.ic_skip_next
            "prev" -> R.drawable.ic_skip_previous
            else -> R.drawable.ic_play_pause
        }
        "global" -> when (arg) {
            "back" -> R.drawable.ic_arrow_back
            "home" -> R.drawable.ic_nav_home
            "recents" -> R.drawable.ic_crop_square
            "notifications" -> R.drawable.ic_notifications
            "quick" -> R.drawable.ic_tune
            "power" -> R.drawable.ic_power
            "lock" -> R.drawable.ic_lock
            "screenshot" -> R.drawable.ic_screenshot
            else -> R.drawable.ic_bolt
        }
        else -> R.drawable.ic_bolt
    }

    fun icon(a: Act): Int = icon(a.kind, a.arg)

    fun describe(a: Act): String = when (a.kind) {
        "none" -> "Nothing"
        "vol" -> "Volume panel (${streamName(a.arg.toIntOrNull() ?: 3)})"
        "app" -> "Open app: " + a.arg2.ifBlank { a.arg }
        "url" -> "Open link: " + a.arg
        "intent" -> "Custom intent (${a.arg2.ifBlank { "activity" }}): " + a.arg.take(40)
        else -> choices[indexOf(a)].label
    }
}
