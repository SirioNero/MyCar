#!/usr/bin/env bash
# Run by .github/workflows/car-preview.yml on the emulator: renders the car UI through the
# debug-only CarPreviewActivity at a few car screen sizes, tapping the dock between shots, and
# collects the PNGs it writes (plus the app's log and any crash) into previews/.
set -u
PKG=io.github.sirionero.mycar.dev
ACT=$PKG/io.github.sirionero.mycar.CarPreviewActivity
FILES=/sdcard/Android/data/$PKG/files

adb install -r build/myCar-debug.apk
mkdir -p previews

# shoot <name> <width> <height> <dpi> <taps "x,y;x,y" in screen pixels>
shoot() {
    adb shell am force-stop "$PKG"
    adb shell rm -rf "$FILES" 2>/dev/null
    adb logcat -c
    adb shell am start -n "$ACT" --ei w "$2" --ei h "$3" --ei dpi "$4" --es taps "$5" --ez skipIntro true
    sleep 25
    mkdir -p "previews/$1"
    adb pull "$FILES/." "previews/$1/" || echo "nothing to pull for $1"
    adb logcat -d -s myCar > "previews/$1/log.txt" 2>&1
    adb logcat -d -b crash > "previews/$1/crash.txt" 2>&1
}

# Dock taps: the app grid button at the foot, then the clock at the top (dashboard).
shoot 800x480 800 480 160 "48,436;48,44"
shoot 1920x720 1920 720 240 "72,654;72,66"
ls -lR previews
