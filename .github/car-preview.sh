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
# EXTRA adds intent extras for the preview activity; BEFORE is a command run just before it starts.
# ONLY (the workflow's "shots" input) names the shots to take, all of them when empty.
shoot() {
    case " ${ONLY:-$1} " in *" $1 "*) ;; *) return ;; esac
    adb shell am force-stop "$PKG"
    for p in $VELA $PLANCIA $XONOS; do adb shell am force-stop "$p"; done
    adb shell rm -rf "$FILES" 2>/dev/null
    adb logcat -c
    if [ -n "${BEFORE:-}" ]; then eval "$BEFORE"; fi
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
# The full player on the made-up song, opened from the cover beside the map, then its Browse tab:
# the cover blurred behind it all, and still in sight beside the list.
EXTRA="--ez demoSong true" shoot media 1920 720 240 "$WAIT;$WAIT;1635,200;$WAIT;1230,48;$WAIT"
# Home with an app chosen to sit beside the map instead (car Settings > Beside the map).
installed $PLANCIA && EXTRA="--es sidePane $PLANCIA" shoot split-plancia 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
installed $XONOS && EXTRA="--es sidePane $XONOS" shoot split-xonos 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"

# Home's pages either side of the map, the ready-made ones: the dock's map first, as its touch
# brings back the dots that fade over the map, then a dot turns to its page - the weather and
# the agenda left of the map, Plancia and the browser right of it.
DOCK_MAP="72,174"
shoot pages 1920 720 240 "$DOCK_MAP;966,675;$WAIT;$DOCK_MAP;1098,675;$WAIT;$WAIT;$WAIT"
shoot pages-800x480 800 480 160 "48,116;404,450;$WAIT"
# Changing a page: the pencil beside the dots, then a part, for the list of what can go there.
shoot pages-edit 1920 720 240 "$DOCK_MAP;966,675;1138,675;585,400;$WAIT"

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
# as the swipe up from a bottom corner does, through SystemUI. First with the Settings app standing
# in for an assistant that's an activity; the phone's own screen, shot after the tap, should show it.
MIC_BUTTON="72,549"   # just above the dock's foot, where it always is
default_assistant=$(adb shell settings get secure assistant | tr -d '\r')
echo "default assistant: $default_assistant"
adb shell settings put secure assistant com.android.settings/com.android.settings.Settings
EXTRA="--es assistant phone" shoot assistant 1920 720 240 "$GRID_BUTTON;$MIC_BUTTON;$WAIT" \
    "sleep 6; mkdir -p previews/assistant; adb exec-out screencap -p > previews/assistant/phone-screen.png"
if [ "$default_assistant" = "null" ]; then adb shell settings delete secure assistant
else adb shell settings put secure assistant "$default_assistant"; fi

# The swipe itself, on the phone: gesture navigation, then from the very corner up and in, as a
# thumb would, from the Home screen.
adb shell settings put secure assist_touch_gesture_enabled 1
adb shell cmd overlay enable-exclusive --category com.android.internal.systemui.navbar.gestural || true
sleep 3
size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*x[0-9]*\)$/\1/p' | tail -1)
W=${size%x*}; H=${size#*x}
echo "phone screen ${W}x${H}, $(adb shell wm density | tr -d '\r' | tail -1), navigation_mode=$(adb shell settings get secure navigation_mode | tr -d '\r')"
# A fast "input swipe" from the corner came out as the quick switch between apps: this one goes a
# step at a time, as a finger does, so the launcher has time to see it as the assistant's.
corner_swipe() {
    adb shell input keyevent KEYCODE_HOME
    sleep 2
    adb shell "input motionevent DOWN 4 $((H - 4)); for i in 1 2 3 4 5 6 7 8 9 10; do input motionevent MOVE \$((4 + 15 * i)) \$(($H - 4 - 15 * i)); done; input motionevent UP 154 $((H - 154))"
    sleep 4
}
focus() { adb shell dumpsys window | grep -E "mCurrentFocus|mFocusedApp" | tr -d '\r'; }
# Whether the launcher, which turns the swipe into the assistant, thinks there is one to start.
echo "--- launcher: assistant gesture state ---"
adb shell dumpsys activity service TouchInteractionService | tr -d '\r' | grep -iE "assistant|sysui.*state|navmode|gesture" | head -20

# The emulator's own assistant, the Google app: the phone's screen after the microphone, then
# after the swipe. The two should show the assistant the same way.
mkdir -p previews/assistant-google
EXTRA="--es assistant phone" shoot assistant-google 1920 720 240 "$GRID_BUTTON;$MIC_BUTTON;$WAIT" \
    "sleep 6; adb exec-out screencap -p > previews/assistant-google/phone-mic.png; focus > previews/assistant-google/focus-mic.txt"
corner_swipe
adb exec-out screencap -p > previews/assistant-google/phone-swipe.png
focus > previews/assistant-google/focus-swipe.txt
echo "--- the Google app: what had the phone's focus after the microphone, then after the swipe ---"
cat previews/assistant-google/focus-mic.txt previews/assistant-google/focus-swipe.txt

# How Android showed the assistant, from the microphone and from the swipe: the debug build's
# stand-in assistant (debug/TestAssistant.kt) logs the flags and arguments a real assistant reads
# to decide whether to open listening. It's made the phone's assistant only once the preview runs:
# force-stopping the assistant's app, as each shot starts by doing, hands the role back to the
# Google app.
make_test_assistant() {
    adb shell cmd role add-role-holder --user 0 android.app.role.ASSISTANT "$PKG" || echo "could not make the test assistant the default"
    sleep 1
    echo "assistant: $(adb shell settings get secure assistant | tr -d '\r')"
    echo "voice interaction service: $(adb shell settings get secure voice_interaction_service | tr -d '\r')"
}
EXTRA="--es assistant phone" shoot assistant-mic 1920 720 240 "$WAIT;$WAIT;$GRID_BUTTON;$MIC_BUTTON;$WAIT" make_test_assistant
mkdir -p previews/assistant-gesture
adb logcat -c
corner_swipe
adb exec-out screencap -p > previews/assistant-gesture/phone-screen.png
adb logcat -d -s myCar > previews/assistant-gesture/log.txt
echo "--- how the assistant was shown: microphone, then the swipe ---"
grep -h "test assistant: shown" previews/assistant-mic/log.txt || echo "microphone: no test assistant line"
grep -h "test assistant: shown" previews/assistant-gesture/log.txt || echo "swipe: no test assistant line"
adb shell cmd role remove-role-holder --user 0 android.app.role.ASSISTANT "$PKG" || true

# The microphone with the phone locked by a PIN, myCar on under Accessibility (which is what lets
# it light the phone from the background): the phone should ask to be unlocked, and the assistant
# start once it is. The PIN is typed on the phone as the driver would; the stand-in assistant logs
# when it's shown, which should be after the unlock.
PIN=1357
A11Y_SERVICE="$PKG/io.github.sirionero.mycar.mirror.TouchRelayService"
lock_phone() {
    make_test_assistant
    adb shell locksettings set-pin $PIN
    adb shell input keyevent KEYCODE_SLEEP
}
BEFORE="adb shell settings put secure enabled_accessibility_services $A11Y_SERVICE; \
    adb shell settings put secure accessibility_enabled 1; sleep 3" \
    EXTRA="--es assistant phone" shoot assistant-locked 1920 720 240 \
    "$WAIT;$WAIT;$WAIT;$GRID_BUTTON;$MIC_BUTTON;$WAIT;$WAIT;$WAIT" \
    "lock_phone; sleep 10; mkdir -p previews/assistant-locked; \
     adb exec-out screencap -p > previews/assistant-locked/phone-unlock.png; \
     adb shell input text $PIN; adb shell input keyevent KEYCODE_ENTER; sleep 4; \
     adb exec-out screencap -p > previews/assistant-locked/phone-after.png"
adb shell locksettings clear --old $PIN || true
adb shell input keyevent KEYCODE_WAKEUP
adb shell wm dismiss-keyguard || true
adb shell settings delete secure enabled_accessibility_services
adb shell settings put secure accessibility_enabled 0
adb shell cmd role remove-role-holder --user 0 android.app.role.ASSISTANT "$PKG" || true
echo "--- the microphone with the phone locked ---"
grep -h "assistant:\|test assistant: shown" previews/assistant-locked/log.txt 2>/dev/null ||
    echo "assistant-locked: nothing in the log"

# The light theme isn't reachable from here; the dark default is what ships.
ls -lR previews
