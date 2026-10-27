#!/usr/bin/env bash
# Run by .github/workflows/car-preview.yml on the emulator: renders the car UI through the
# debug-only CarPreviewActivity at a few car screen sizes, tapping between shots, and collects
# the PNGs it writes (plus the app's log and any crash) into previews/. Car apps the workflow
# downloaded into third-party/ are installed first and opened from the app grid.
set -u
PKG=io.github.sirionero.mycar.dev
ACT=$PKG/io.github.sirionero.mycar.CarPreviewActivity
FILES=/sdcard/Android/data/$PKG/files
VELA=app.vela
PLANCIA=it.plancia.ha
XONOS=it.xonos.ma

# -g grants the runtime permissions up front, as onboarding would: phone, contacts, location.
adb install -r -g build/myCar-debug.apk
for apk in third-party/*.apk; do
    [ -f "$apk" ] || continue
    adb install -r -g "$apk" || echo "could not install $apk"
done
# Somewhere to be, for the maps and the weather: Milan.
adb emu geo fix 9.19 45.464
mkdir -p previews

installed() { adb shell pm list packages "$1" | tr -d '\r' | grep -qx "package:$1"; }

# The 1920x720 app grid, in the launcher's order (phone, maps, music, messaging, web, screen
# sharing, others, settings, exit), as many as are installed.
grid=(phone)
installed $VELA && grid+=(vela)
installed $XONOS && grid+=(xonos)
grid+=(ytmusic ytmusic-browse messages browser screen)
installed $PLANCIA && grid+=(plancia)
grid+=(settings exit)
echo "1920x720 grid: ${grid[*]}"

# Where an app's icon is on that grid, as "x,y": 7 columns 247px apart, 2 rows 324px apart.
at() {
    local i
    for i in "${!grid[@]}"; do
        if [ "${grid[$i]}" = "$1" ]; then
            echo "$((291 + 247 * (i % 7))),$((159 + 324 * (i / 7)))"
            return
        fi
    done
    echo "no $1 on the grid" >&2
    echo "-100,-100"
}

GRID_BUTTON="72,654"   # the dock's foot, at 1920x720
WAIT="-100,-100"       # a tap off the screen: only gives an app time before the next shot

# shoot <name> <width> <height> <dpi> <taps "x,y;x,y" in screen pixels> [command, run 5s in]
# EXTRA adds intent extras for the preview activity.
shoot() {
    adb shell am force-stop "$PKG"
    for p in $VELA $PLANCIA $XONOS; do adb shell am force-stop "$p"; done
    adb shell rm -rf "$FILES" 2>/dev/null
    adb logcat -c
    # One string for the device's shell, with the taps quoted for it: their ";" would end the command.
    adb shell "am start -n $ACT --ei w $2 --ei h $3 --ei dpi $4 --ez skipIntro true ${EXTRA:-} --es taps '$5'"
    if [ -n "${6:-}" ]; then
        sleep 5
        eval "$6"
    fi
    # The activity shoots 2s in, then taps every 2.5s and shoots 1.5s after each tap.
    local taps
    taps=$(($(tr -cd ';' <<< "$5" | wc -c) + 1))
    sleep $((12 + 3 * taps))
    mkdir -p "previews/$1"
    adb pull "$FILES/." "previews/$1/" || echo "nothing to pull for $1"
    adb logcat -d -s myCar > "previews/$1/log.txt" 2>&1
    adb logcat -d -b crash > "previews/$1/crash.txt" 2>&1
}

# Home, then the app grid (dock's foot), then the dashboard (the clock at its top).
shoot 800x480 800 480 160 "48,436;48,44"
shoot 1920x720 1920 720 240 "$GRID_BUTTON;72,66"

# Each car app, opened from the grid and given a few seconds to draw.
installed $VELA && shoot vela 1920 720 240 "$GRID_BUTTON;$(at vela);$WAIT;$WAIT;$WAIT;$WAIT"
installed $PLANCIA && shoot plancia 1920 720 240 "$GRID_BUTTON;$(at plancia);$WAIT;$WAIT;$WAIT"
installed $XONOS && shoot xonos 1920 720 240 "$GRID_BUTTON;$(at xonos);$WAIT;$WAIT;$WAIT"

# Home with music playing: the real map beside the real player, a made-up song in the dock.
# Shot over 15s, as the map app takes a few seconds to draw its first map.
EXTRA="--ez demoSong true" shoot split 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
# Home with an app chosen to sit beside the map instead (car Settings > Beside the map).
installed $PLANCIA && EXTRA="--es sidePane $PLANCIA" shoot split-plancia 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
installed $XONOS && EXTRA="--es sidePane $XONOS" shoot split-xonos 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"

# myCar's own screens: the phone and the car settings.
shoot phone 1920 720 240 "$GRID_BUTTON;$(at phone);$WAIT"
shoot settings 1920 720 240 "$GRID_BUTTON;$(at settings);$WAIT"

# The app grid over each kind of moving wallpaper: the same screen shot 2.5s apart, to compare.
EXTRA="--es wallpaper waves" shoot motion-waves 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
EXTRA="--es wallpaper fireflies" shoot motion-fireflies 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
EXTRA="--es wallpaper lava" shoot motion-lava 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
# The wallpaper picker, still and moving swatches.
EXTRA="--ez wallpapers true" shoot wallpapers 1920 720 240 "$WAIT;$WAIT"

# The player docked beside the map, playing a made-up song (the emulator has none of its own).
EXTRA="--ez player true" shoot player 1920 720 240 "$WAIT"
EXTRA="--ez player true" shoot player-800x480 800 480 160 "$WAIT"

# An incoming call from an unknown number, rung on the emulator's modem once the car UI is up.
shoot call 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT" "adb emu gsm call 3331234567"
adb emu gsm cancel 3331234567

# The dock's microphone with "Phone's assistant" chosen: myCar should wake the phone's assistant
# as the swipe up from a bottom corner does, through SystemUI. The emulator has no assistant, so
# the Settings app stands in for one; the phone's own screen, shot after the tap, should show it.
MIC_BUTTON="72,549"   # just above the dock's foot, where it always is
adb shell settings put secure assistant com.android.settings/com.android.settings.Settings
EXTRA="--es assistant phone" shoot assistant 1920 720 240 "$GRID_BUTTON;$MIC_BUTTON;$WAIT" \
    "sleep 6; mkdir -p previews/assistant; adb exec-out screencap -p > previews/assistant/phone-screen.png"
adb shell settings delete secure assistant

# Does the microphone start the phone's assistant just as the swipe up from a bottom corner does?
# The debug build carries a stand-in assistant (debug/TestAssistant.kt) that logs how Android
# showed it: the flags and arguments a real assistant reads to decide whether to open listening.
# It's made the phone's assistant, started once from myCar's microphone and once by the swipe
# itself, and the two lines are printed side by side.
adb shell cmd role add-role-holder --user 0 android.app.role.ASSISTANT "$PKG" || echo "could not make the test assistant the default"
echo "assistant: $(adb shell settings get secure voice_interaction_service | tr -d '\r')"
EXTRA="--es assistant phone" shoot assistant-mic 1920 720 240 "$GRID_BUTTON;$MIC_BUTTON;$WAIT"
mkdir -p previews/assistant-gesture
adb shell settings put secure assist_touch_gesture_enabled 1
echo "navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')"
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural || true
sleep 3
adb shell input keyevent KEYCODE_HOME
sleep 2
size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*x[0-9]*\)$/\1/p' | tail -1)
W=${size%x*}; H=${size#*x}
echo "phone screen ${W}x${H}"
adb logcat -c
# From the very corner up and in, as a thumb would: left corner, slower, then the right one.
for swipe in "5 $((H - 5)) $((W / 4)) $((H - W / 4)) 250" \
             "15 $((H - 15)) $((W / 3)) $((H - W / 3)) 450" \
             "$((W - 5)) $((H - 5)) $((W - W / 4)) $((H - W / 4)) 250"; do
    adb shell input swipe $swipe
    sleep 3
    if adb logcat -d -s myCar | grep -q "test assistant: shown"; then echo "the swipe started it: $swipe"; break; fi
done
adb exec-out screencap -p > previews/assistant-gesture/phone-screen.png
adb logcat -d -s myCar > previews/assistant-gesture/log.txt
echo "--- how the assistant was shown: microphone, then the swipe ---"
grep -h "test assistant: shown" previews/assistant-mic/log.txt || echo "microphone: no test assistant line"
grep -h "test assistant: shown" previews/assistant-gesture/log.txt || echo "swipe: no test assistant line"
adb shell cmd role remove-role-holder --user 0 android.app.role.ASSISTANT "$PKG" || true

# The light theme isn't reachable from here; the dark default is what ships.
ls -lR previews
