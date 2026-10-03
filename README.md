# Edge Volume

Swipe in from a small spot on your screen edge and the volume panel opens. That's it.
Back gesture still works on the rest of the edge. **No root needed.**

## Why I made this (the honest reason)

I love my Pixel. I do not want its volume button to break. Pressing that button again and again every day felt like slowly killing it, and I didn't like that, lol. So now I just swipe on the screen edge and the button gets to live a long happy life.

Every edge swipe = Back with gesture navigation, so I carved out a tiny strip just for volume.

## Who is this useful for

- People who want to **save their volume buttons** from wearing out (hi, me)
- People whose **volume button is already broken** or stuck, this gives you a working volume control with no repair needed
- People who find the buttons hard to reach, or use the phone one handed
- Anyone who just likes edge gestures

<p align="center">
  <img src="screenshots/screenshot1.png" width="300" alt="Edge Volume setup screen">
</p>

---

## What it does

- Puts a small invisible strip on the **right edge** (or left, your choice)
- Swipe **inward** from that strip = system volume panel pops up
- Back gesture is turned off **only inside the strip**, everywhere else on the edge it works like normal
- You choose where the strip is: position, height, width
- A **red preview strip** helps you place it exactly where you want
- Can **hide the app icon** from inside the app (no root, no adb)

## Why it's light on battery and RAM

- No background loop, no timers, no polling
- No foreground service, so **no notification**
- Doesn't listen to any accessibility events
- The strip is just one tiny window, and it only does something when you touch it
- Release build has minify + resource shrinking on

I haven't put exact numbers here because they depend on your phone. You can check yourself:

```
adb shell dumpsys meminfo com.example.edgevolume | head -25
```

Look at the TOTAL PSS line.

## Privacy

- **No internet permission.** The app can't send anything anywhere
- It's an accessibility service, but it has **no access to your screen content** and doesn't subscribe to any events. It only exists to place the strip window
- No ads, no analytics, nothing stored except your 6 settings on the phone

---

## Requirements

- Android **10 or newer**
- **Gesture navigation** turned on (if you use 3-button nav you don't need this app anyway)

## Install

1. Download the APK from the [Releases](../../releases) page
2. Install it and **open it once** (some phones won't let you enable the service if the app was never opened)
3. Follow the setup below

## Setup

1. Tap **Open Accessibility settings** in the app and turn on **Edge Volume**
2. A red strip shows up on the edge. Move it with the sliders and the +/- buttons until it's where you like
3. Swipe inward from the red strip. Volume panel should open
4. Happy? Untick **Show red strip**. Done

### Toggle greyed out / "Restricted setting" message?

Android 13+ blocks accessibility for apps not installed from the Play Store.

1. Try turning the service on once (it will show the restricted message, press OK)
2. Go to **Settings > Apps > Edge Volume**
3. Tap the **3 dot menu** (top right) > **Allow restricted settings**
4. Go back to Accessibility and turn it on

The app has an **Open App info** button that takes you straight to step 2. The 3 dot menu only shows up after you've tried step 1, that's an Android thing, not a bug.

Rooted? You can skip the menu with:

```
su -c "cmd appops set com.example.edgevolume ACCESS_RESTRICTED_SETTINGS allow"
```

## Settings

| Setting | What it does |
|---|---|
| Vertical position | Where the strip is on the edge (0% top, 100% bottom, 0.1% steps) |
| Height | 40 to 200 dp. 200 is the max Android allows for gesture exclusion |
| Width | 4 to 60 dp. Thin = fewer accidental swipes, but harder to hit |
| Show red strip | Preview for setup. Turn off when you're done |
| Left edge | Use the left edge instead of the right |
| Vibrate | Small vibration when the swipe is detected |
| Hide app icon | Removes the icon from the app drawer. Service keeps working |

## Hiding the icon

Press **Hide app icon** in the app. The service keeps running.

To open the settings again later: **Settings > Accessibility > Edge Volume > Settings** (the gear/settings row on that page). You can also bring the icon back from there with **Show app icon**.

---

## Troubleshooting

**Nothing happens when I swipe**
- Is the service actually on? Open the app, the top line says ON or OFF
- Turn the red strip on and check it's visible and where you expect
- Start the swipe from inside the strip, not just near it

**Back still fires inside the strip**
- Make the strip a bit wider, some phones have a wide back-gesture area
- Start the swipe a little inside the strip, not on the very last pixel
- Some ROMs ignore the exclusion rule from apps. If yours does, open an issue with your phone model + Android version

**Service turned itself off**
- Installing or updating the app can turn it off. Just toggle it on again
- Don't **Force stop** the app, Android disables the service when you do. Swiping it away from recents is fine
- Set the app's battery to **Unrestricted** (Settings > Apps > Edge Volume > App battery usage). Some phones (Xiaomi, Oppo, Vivo, Realme, Samsung, etc) kill background stuff aggressively, you might also need to allow autostart

**Volume panel shows but it's the wrong volume**
- It opens the **media** volume panel

---

## Known limits

- Max zone height is about 200dp, that's an Android rule, not mine
- Needs gesture navigation
- Phones with their own custom gesture system might not respect the exclusion
- Only opens the volume panel for now, no other actions yet

## How it works (short version)

1. The accessibility service adds a tiny overlay window on the screen edge
2. That window tells Android "don't treat touches here as Back" using `setSystemGestureExclusionRects`
3. A touch listener checks if you swiped inward more than about 28dp
4. If yes, it calls `AudioManager.adjustStreamVolume(..., ADJUST_SAME, FLAG_SHOW_UI)` which shows the panel without changing the volume

One gotcha I hit: the strip view must actually draw something (even an almost invisible color), otherwise Android silently ignores the exclusion zone. That's why the "invisible" strip has a 1-alpha background.

## Build it yourself

1. Open the project in Android Studio and let Gradle sync
2. **Build > Build APK(s)** for a quick debug build

For a signed release, make a `keystore.properties` file in the project root:

```
storeFile=/path/to/my.jks
storePassword=your_password
keyAlias=your_alias
keyPassword=your_key_password
```

Then run `./gradlew assembleRelease`. If there's no `gradlew`, run `gradle wrapper` once.

Want your own package name? Change `applicationId` (and `namespace`) in `app/build.gradle.kts`.

Project layout:

```
app/src/main/java/com/example/edgevolume/
  EdgeService.kt    the strip + swipe + volume panel
  MainActivity.kt   setup screen
  Cfg.kt            setting keys and defaults
```

## Tested on

- Google Pixel 8 (Android 14+)
- add yours here, PRs welcome

## Contributing

Issues and PRs are welcome. If something doesn't work on your phone, please include:
- phone model
- Android version / ROM
- what happened vs what you expected

Ideas I might add: more actions (not just volume), different swipe directions, multiple zones, ring/call volume option.

## License

MIT. Do whatever you want, just keep the license text.
