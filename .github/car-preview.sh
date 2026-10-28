#!/usr/bin/env bash
# Run by .github/workflows/car-preview.yml on the emulator: renders the car UI through the
# debug-only CarPreviewActivity at a few car screen sizes, tapping between shots, and collects
# the PNGs it writes (plus the app's log and any crash) into previews/. Car apps the workflow
# downloaded into third-party/ are installed first and opened from the app grid.
set -u
# An emulator that goes away (it has, once or twice, in the middle of Xonos playing) would leave
# adb waiting for good, and the run with it: every adb call gives up after three minutes.
ADB_BIN=$(command -v adb)
adb() { timeout 180 "$ADB_BIN" "$@"; }
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
grid+=(ytmusic ytmusic-browse messages browser cinema screen)
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
# EXTRA adds intent extras for the preview activity; BEFORE is a command run just before it starts;
# LOGTAGS, more log tags to keep besides myCar's.
# ONLY (the workflow's "shots" input) names the shots to take, all of them when empty.
shoot() {
    case " ${ONLY:-$1} " in *" $1 "*) ;; *) return ;; esac
    if ! adb get-state >/dev/null 2>&1; then
        echo "no emulator any more: $1 left out"
        return
    fi
    adb shell am force-stop "$PKG"
    for p in $VELA $PLANCIA $XONOS; do adb shell am force-stop "$p"; done
    adb shell rm -rf "$FILES" 2>/dev/null
    adb logcat -c
    # The log as it comes, kept only if the emulator is gone by the end of the shot.
    mkdir -p "previews/$1"
    timeout 600 "$ADB_BIN" logcat -v time -s myCar ${LOGTAGS:-} AndroidRuntime > "previews/$1/stream.txt" 2>&1 &
    local streaming=$!
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
    adb pull "$FILES/." "previews/$1/" || echo "nothing to pull for $1"
    if adb logcat -d -s myCar ${LOGTAGS:-} > "previews/$1/log.txt" 2>&1; then
        rm -f "previews/$1/stream.txt"
    else
        echo "$1: the emulator went away during the shot, its log so far is in stream.txt"
    fi
    kill "$streaming" 2>/dev/null
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
# The same at 800x480, where the tabs are narrow: "Now playing" on two lines, not "Now playi…".
EXTRA="--ez demoSong true" shoot media-800x480 800 480 160 "$WAIT;$WAIT;703,133;$WAIT;$WAIT"
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
# The layouts a page can take: the pencil, the Layout button at the start of its bar, the list of
# layouts; then "Half left" from it (the fourth tile of the first row).
shoot pages-layouts 1920 720 240 "$DOCK_MAP;966,675;1138,675;690,72;$WAIT;1430,252;$WAIT"
# Each layout beside the two halves and the four parts, on the page left of the map (--es pageLayout):
# the weather, the agenda, then empty parts.
for layout in full half-quarters quarters-half strip-quarters quarters-strip; do
    EXTRA="--es pageLayout $layout" shoot "layout-$layout" 1920 720 240 "$WAIT;$DOCK_MAP;966,675;$WAIT;$WAIT"
done
EXTRA="--es pageLayout half-quarters" shoot layout-800x480 800 480 160 "48,116;404,450;$WAIT"

# Plancia inside myCar, on the run's own Home Assistant (.github/home-assistant.sh), which the
# emulator reaches at 10.0.2.2: full screen from the grid, where the Plancia app was, then its
# menu (Home Assistant's ☰, in its bar under myCar's); on its ready-made page right of the map,
# beside the browser; and beside the map. Its page is made 2.5s after the car's screen is up.
if [ -n "${HA_TOKEN:-}" ]; then
    HA="--es haUrl http://10.0.2.2:8123 --es haToken $HA_TOKEN"
    PLANCIA_MENU="197,108"
    EXTRA="$HA" shoot plancia-app 1920 720 240 "$WAIT;$WAIT;$GRID_BUTTON;$(at plancia);$WAIT;$WAIT;$WAIT;$PLANCIA_MENU;$WAIT"
    EXTRA="$HA" shoot plancia-page 1920 720 240 "$WAIT;$WAIT;$DOCK_MAP;1098,675;$WAIT;$WAIT;$WAIT"
    EXTRA="$HA --es sidePane mycar:plancia" shoot plancia-side 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
    echo "--- Plancia inside myCar ---"
    grep -h "plancia:\|web:" previews/plancia-app/log.txt 2>/dev/null || echo "plancia-app: nothing in the log"
else
    echo "--- no Home Assistant: Plancia's shots left out ---"
fi

# Xonos inside myCar, on the run's own Music Assistant (.github/music-assistant.sh) at 10.0.2.2:8095,
# with an album of three tones: from the grid, where the Xonos app was; then Browse, Albums and the
# album's play button, then Now playing; and beside the map, chosen as the Xonos app was. Its own
# log lines are tagged Xonos.
if [ -n "${MA_TOKEN:-}" ]; then
    MA="--es maUrl http://10.0.2.2:8095 --es maToken $MA_TOKEN"
    LOGTAGS=Xonos EXTRA="$MA" shoot xonos-inside 1920 720 240 "$WAIT;$GRID_BUTTON;$(at xonos);$WAIT;1230,48;$WAIT;1300,565;$WAIT;1808,232;$WAIT;$WAIT;993,48;$WAIT;$WAIT;$WAIT"
    # The music stops with the shot: the emulator went away a minute into the album once, with
    # it still playing, and the rest of the run waited for it for good.
    adb shell am force-stop "$PKG"
    LOGTAGS=Xonos EXTRA="$MA --es sidePane $XONOS" shoot xonos-side 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT"
    echo "--- Xonos inside myCar ---"
    grep -h "xonos\|Xonos\|media:" previews/xonos-inside/log.txt 2>/dev/null | head -60 || echo "xonos-inside: nothing in the log"
else
    echo "--- no Music Assistant: Xonos's shots left out ---"
fi

# Cinema: from the grid, myCar's own tiles ("On this phone", then any TV list) and the video sites
# as tiles (each with the icon it gives itself), then the first site, YouTube; and the same with
# the car moving, where "Video only when parked" covers the picture and the sound carries on.
CINEMA_PHONE="302,216"    # the first tile, "On this phone"
CINEMA_SECOND="594,216"   # YouTube; with a TV list, the list
shoot cinema 1920 720 240 "$GRID_BUTTON;$(at cinema);$WAIT;$WAIT;$CINEMA_SECOND;$WAIT;$WAIT;$WAIT"
EXTRA="--ez moving true" shoot cinema-moving 1920 720 240 "$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_SECOND;$WAIT;$WAIT"
# "Continue" (car/CinemaContinue.kt): YouTube opened, a look at the map, Cinema again - the first
# tile is now "Continue: YouTube", and a tap on it opens the site again at the page it was on.
shoot cinema-continue 1920 720 240 "$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_SECOND;$WAIT;$WAIT;$DOCK_MAP;$WAIT;$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_PHONE;$WAIT;$WAIT"

# Cinema's shelves on Android's own player: a minute of test picture and tone on the phone (in
# Movies), and a TV list from the runner (10.0.2.2:8001) with a stream (HLS) and a film file, a
# logo and two groups, all made with ffmpeg. "On this phone" plays its video; the TV tile shows
# the groups, then all the channels, and plays the first; the phone's video again with the car
# moving, covered and still sounding.
CINEMA_MEDIA=/tmp/cinema-media
if [ -z "${ONLY:-}" ] || [[ " $ONLY " == *" cinema-"* ]]; then
mkdir -p "$CINEMA_MEDIA/tv"
FFMPEG="docker run --rm -v $CINEMA_MEDIA:/out --entrypoint /ffmpeg mwader/static-ffmpeg:7.1 -loglevel error -y"
$FFMPEG -f lavfi -i "testsrc2=size=1280x720:rate=30:duration=60" -f lavfi -i "sine=f=440:d=60" \
    -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest /out/test-video.mp4 || echo "cinema: no test video"
$FFMPEG -f lavfi -i "testsrc=size=1280x720:rate=25:duration=120" -f lavfi -i "sine=f=660:d=120" \
    -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -f hls -hls_time 4 -hls_list_size 0 /out/tv/live.m3u8 || echo "cinema: no test stream"
$FFMPEG -f lavfi -i "color=c=0x3D8BFF:s=256x144" -frames:v 1 /out/tv/logo.png || echo "cinema: no test logo"
cat > "$CINEMA_MEDIA/tv.m3u" <<'M3U'
#EXTM3U
#EXTINF:-1 tvg-logo="http://10.0.2.2:8001/tv/logo.png" group-title="Test",Test channel
http://10.0.2.2:8001/tv/live.m3u8
#EXTINF:-1 group-title="Films",Test film
http://10.0.2.2:8001/test-video.mp4
M3U
python3 -m http.server 8001 --bind 127.0.0.1 --directory "$CINEMA_MEDIA" > /tmp/cinema-media.txt 2>&1 &
adb push "$CINEMA_MEDIA/test-video.mp4" /sdcard/Movies/test-video.mp4
adb shell am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d file:///sdcard/Movies/test-video.mp4 > /dev/null
fi
VIDEO_FIRST="377,240"     # the first video on "On this phone"
GROUP_ALL="377,180"       # "All channels", the first of a list's groups
CHANNEL_FIRST="304,207"   # the first channel
TV="--es tvList http://10.0.2.2:8001/tv.m3u"
shoot cinema-videos 1920 720 240 "$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_PHONE;$WAIT;$WAIT;$VIDEO_FIRST;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="$TV" shoot cinema-tv 1920 720 240 "$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_SECOND;$WAIT;$WAIT;$GROUP_ALL;$WAIT;$CHANNEL_FIRST;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez moving true" shoot cinema-videos-moving 1920 720 240 "$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_PHONE;$WAIT;$VIDEO_FIRST;$WAIT;$WAIT"
echo "--- Cinema ---"
grep -h "cinema" previews/cinema*/log.txt 2>/dev/null | head -40 || echo "cinema: nothing in the log"
grep -h "GET" /tmp/cinema-media.txt 2>/dev/null | head -20 || echo "cinema: nothing asked of the runner"
# The sites Cinema suggests, as the runner reaches them (the cloud session can't).
for site in https://www.youtube.com https://www.raiplay.it https://www.twitch.tv https://www.dailymotion.com https://vimeo.com \
    https://www.la7play.it https://www.ted.com https://www.arte.tv https://plus.nasa.gov https://archive.org/details/feature_films; do
    echo "cinema site $site: $(curl -s -o /dev/null -L --max-time 20 -w '%{http_code} %{url_effective}' "$site")"
done

# Plancia and Xonos inside myCar set up on addresses that don't answer (as Tailscale ones do when
# myCar is left out of it), the Plancia and Xonos apps on the phone: a few seconds in, the apps take
# their places for the drive (car/InsideOrApp). The grid, shot as it changes, then the page right
# of the map, whose ready-made Plancia part shows the app.
if installed $PLANCIA && installed $XONOS; then
    DOWN="--es haUrl http://10.0.2.2:1 --es haToken x --es maUrl http://10.0.2.2:2 --es maToken x"
    LOGTAGS=Xonos EXTRA="$DOWN" shoot standin 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$DOCK_MAP;1098,675;$WAIT;$WAIT"
    echo "--- the apps in place of the ones inside myCar ---"
    grep -h "takes .* place\|InsideOrApp" previews/standin/log.txt 2>/dev/null || echo "standin: no app took a place"
fi

# The bridge (bridge/): Plancia and Xonos inside myCar reaching their servers through the Plancia
# and Xonos apps, as with myCar left out of Tailscale (--ez bridge true: the emulator has none).
# Wants the apps built from a branch of theirs with the bridge (the workflow's plancia_ref and
# xonos_ref), set up as a driver's would be: their own settings, written as root. Plancia from the
# grid, then its menu; its page right of the map; Xonos from the grid, an album played through it.
# The apps log "Ponte per <myCar> verso <server>" for each connection they open for myCar.
case " ${ONLY:-bridge} " in *bridge*) BRIDGE_SHOTS=yes ;; *) BRIDGE_SHOTS= ;; esac
if [ -n "${PLANCIA_REF:-}${XONOS_REF:-}" ] && [ -n "$BRIDGE_SHOTS" ]; then
    adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
    # Each app started once, so its data folder exists, then stopped.
    for p in $PLANCIA $XONOS; do adb shell monkey -p $p -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; done
    sleep 8
    for p in $PLANCIA $XONOS; do adb shell am force-stop $p; done
    # app_prefs <package> <file> <entries>: the app's SharedPreferences, owned by it as if it wrote them.
    app_prefs() {
        local pkg=$1 file=$2 body=$3 owner
        owner=$(adb shell stat -c %u:%g /data/data/$pkg | tr -d '\r')
        printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n%s\n</map>\n" "$body" > "/tmp/$file"
        adb shell mkdir -p /data/data/$pkg/shared_prefs
        adb push "/tmp/$file" "/data/data/$pkg/shared_prefs/$file" >/dev/null
        adb shell chown "$owner" /data/data/$pkg/shared_prefs "/data/data/$pkg/shared_prefs/$file"
        adb shell chmod 660 "/data/data/$pkg/shared_prefs/$file"
        adb shell restorecon -R /data/data/$pkg/shared_prefs >/dev/null 2>&1
    }
    [ -n "${HA_TOKEN:-}" ] && app_prefs $PLANCIA plancia.xml "    <string name=\"url_home\">http://10.0.2.2:8123</string>
    <string name=\"token\">$HA_TOKEN</string>"
    [ -n "${MA_TOKEN:-}" ] && app_prefs $XONOS xonos.xml "    <string name=\"url_home\">http://10.0.2.2:8095</string>
    <string name=\"token\">$MA_TOKEN</string>
    <string name=\"username\">mycar</string>"
    adb unroot >/dev/null 2>&1; sleep 3; adb wait-for-device
    if [ -n "${PLANCIA_REF:-}" ] && [ -n "${HA_TOKEN:-}" ]; then
        LOGTAGS=Plancia EXTRA="$HA --ez bridge true" shoot bridge-plancia 1920 720 240 "$WAIT;$WAIT;$GRID_BUTTON;$(at plancia);$WAIT;$WAIT;$WAIT;$PLANCIA_MENU;$WAIT"
        LOGTAGS=Plancia EXTRA="$HA --ez bridge true" shoot bridge-plancia-page 1920 720 240 "$WAIT;$WAIT;$DOCK_MAP;1098,675;$WAIT;$WAIT;$WAIT"
    fi
    if [ -n "${XONOS_REF:-}" ] && [ -n "${MA_TOKEN:-}" ]; then
        LOGTAGS=Xonos EXTRA="$MA --ez bridge true" shoot bridge-xonos 1920 720 240 "$WAIT;$GRID_BUTTON;$(at xonos);$WAIT;1230,48;$WAIT;1300,565;$WAIT;1808,232;$WAIT;$WAIT;993,48;$WAIT;$WAIT;$WAIT"
        adb shell am force-stop "$PKG"
    fi
    echo "--- the bridge: Plancia and Xonos inside myCar through the apps ---"
    for d in bridge-plancia bridge-plancia-page bridge-xonos; do
        [ -d "previews/$d" ] || continue
        echo "[$d]"
        grep -h "Ponte per\|bridge\|proxy\|plancia:\|xonos:\|Xonos" "previews/$d/log.txt" 2>/dev/null | head -50
    done
fi

# Music Assistant on the phone, in the Xonos app (the workflow's xonos_server, with xonos_ref): the
# package Xonos downloads when asked comes from the runner (third-party/xonos-server, made by
# build-apps.sh), as it would from Xonos's release. Xonos's own debug check downloads it and sets the
# server up (ServerCheckActivity: the account, an album of tones from its folder, a first track
# played), then Xonos inside myCar runs on it, from the grid as in xonos-inside. 127.0.0.1 always
# goes through the Xonos app (the bridge): the shot starts with Xonos stopped, and myCar's binding
# wakes it, and its server with it.
case " ${ONLY:-phone-xonos} " in *" phone-xonos "*) PHONE_XONOS=yes ;; *) PHONE_XONOS= ;; esac
if [ -n "${XONOS_SERVER:-}" ] && [ -n "$PHONE_XONOS" ] && installed $XONOS && [ -f third-party/xonos-server/xonos-server-x86_64.zip ]; then
    python3 -m http.server 8000 --bind 127.0.0.1 --directory third-party/xonos-server > /tmp/xonos-package.txt 2>&1 &
    PACKAGE_SERVER=$!
    python3 apps-src/xonos/tools/server/tones.py /tmp/tones >/dev/null
    adb shell rm -rf /data/local/tmp/music
    adb push /tmp/tones /data/local/tmp/music >/dev/null
    adb shell run-as $XONOS mkdir -p files
    adb shell run-as $XONOS cp -r /data/local/tmp/music files/
    adb shell "echo http://10.0.2.2:8000/ | run-as $XONOS sh -c 'cat > files/server-package-url'"
    adb logcat -c
    adb shell am start -n $XONOS/.ServerCheckActivity --ez xonos true
    checked=""
    for _ in $(seq 1 120); do
        checked=$(adb logcat -d -s XonosServer | grep -o "Xonos: \(suona=.*\|prova interrotta.*\|collegato al server sul telefono = false.*\)" | tail -n 1)
        [ -n "$checked" ] && break
        sleep 5
    done
    echo "--- Xonos with its own Music Assistant: ${checked:-no answer in 10 minutes} ---"
    adb logcat -d -s XonosServer | grep -m 5 "Pacchetto" || true
    kill "$PACKAGE_SERVER" 2>/dev/null || true
    phone_token=$(adb shell run-as $XONOS cat shared_prefs/xonos.xml | sed -n 's/.*<string name="token">\([^<]*\)<.*/\1/p')
    if [ -n "$phone_token" ]; then
        LOGTAGS="Xonos XonosServer" EXTRA="--es maUrl http://127.0.0.1:8095 --es maToken $phone_token" \
            shoot phone-xonos 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$GRID_BUTTON;$(at xonos);$WAIT;$WAIT;1230,48;$WAIT;1300,565;$WAIT;1808,232;$WAIT;$WAIT;993,48;$WAIT;$WAIT;$WAIT"
        echo "--- Xonos inside myCar on the phone's Music Assistant ---"
        grep -h "Ponte per\|bridge\|xonos:\|Xonos" previews/phone-xonos/log.txt 2>/dev/null | head -60
    fi
fi

# Android 17 (the workflow's "api" input at 37) keeps an app made for it, as myCar is, off the
# home network until it's given the local network permission: its connections there time out, and
# 10.0.2.2 is the home network here. Plancia and Xonos inside myCar with the permission taken away,
# where the car should say what's missing, then with it given back, where they should connect.
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
LOCAL_NET=android.permission.ACCESS_LOCAL_NETWORK
if [ "${SDK:-0}" -ge 37 ]; then
    if [ -n "${HA_TOKEN:-}" ]; then
        BEFORE="adb shell pm revoke $PKG $LOCAL_NET" EXTRA="$HA" \
            shoot lnp-plancia-off 1920 720 240 "$WAIT;$WAIT;$GRID_BUTTON;$(at plancia);$WAIT;$WAIT;$WAIT;$WAIT"
        BEFORE="adb shell pm grant $PKG $LOCAL_NET" EXTRA="$HA" \
            shoot lnp-plancia-on 1920 720 240 "$WAIT;$WAIT;$GRID_BUTTON;$(at plancia);$WAIT;$WAIT;$WAIT;$WAIT"
    fi
    if [ -n "${MA_TOKEN:-}" ]; then
        BEFORE="adb shell pm revoke $PKG $LOCAL_NET" LOGTAGS=Xonos EXTRA="$MA" \
            shoot lnp-xonos-off 1920 720 240 "$WAIT;$GRID_BUTTON;$(at xonos);$WAIT;$WAIT;$WAIT;$WAIT"
        BEFORE="adb shell pm grant $PKG $LOCAL_NET" LOGTAGS=Xonos EXTRA="$MA" \
            shoot lnp-xonos-on 1920 720 240 "$WAIT;$GRID_BUTTON;$(at xonos);$WAIT;1230,48;$WAIT;$WAIT"
        adb shell am force-stop "$PKG"
    fi
    adb shell pm grant $PKG $LOCAL_NET || true
    echo "--- Android $SDK, local network: without the permission, then with it ---"
    for d in lnp-plancia-off lnp-plancia-on lnp-xonos-off lnp-xonos-on; do
        [ -d "previews/$d" ] || continue
        echo "[$d]"
        grep -h "plancia:\|local network\|Xonos" "previews/$d/log.txt" 2>/dev/null | head -25
    done
else
    echo "--- Android $SDK: the local network permission comes with 37, nothing to try ---"
fi

# myCar's own screens: the phone and the car settings.
shoot phone 1920 720 240 "$GRID_BUTTON;$(at phone);$WAIT"
shoot settings 1920 720 240 "$GRID_BUTTON;$(at settings);$WAIT"

# The car's browser on a page that is one big dropdown (<select>): a tap opens myCar's own list over
# the car's screen (car/WebSelect.kt; Android's dialog can't show there, and before 0.5.19 it closed
# myCar), a tap on "Two" picks it, and the page says so in its log ("change Two", tag chromium).
# The page comes from the runner (10.0.2.2:8002), as the browser's start page (--es startUrl).
mkdir -p /tmp/select-page
cat > /tmp/select-page/select.html <<'HTML'
<html><body style="margin:0;background:#202830"><select onchange="console.log('change ' + this.value)" style="position:fixed;left:0;top:0;width:100%;height:100%;font-size:48px"><option>One</option><option>Two</option><optgroup label="More"><option>Three</option><option disabled>Four</option></optgroup></select></body></html>
HTML
python3 -m http.server 8002 --bind 127.0.0.1 --directory /tmp/select-page > /tmp/select-page.txt 2>&1 &
# The list's rows are 64 dp from 108 dp down, centred on the screen: "Two" at 204 dp, 306 px here.
EXTRA="--es startUrl http://10.0.2.2:8002/select.html" LOGTAGS="chromium cr_SelectPopup" \
    shoot browser-select 1920 720 240 "$GRID_BUTTON;$(at browser);$WAIT;$WAIT;1000,400;$WAIT;960,306;$WAIT"
echo "--- a dropdown in the car's browser ---"
if [ -s previews/browser-select/crash.txt ]; then head -20 previews/browser-select/crash.txt; else echo "browser-select: no crash"; fi
grep -h "change " previews/browser-select/log.txt previews/browser-select/stream.txt 2>/dev/null | head -3 || true

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

# The Phone's Recents (car/PhoneRecents.kt): the call above, missed, over a few more written into
# the call log as root (the shell may not): two in a row with Giulia, a garage yesterday, an
# unknown number missed, a declined one. Root only for that: the shots after it run as before.
case " ${ONLY:-phone-recents} " in *" phone-recents "*)
    if adb root >/dev/null 2>&1; then
        sleep 3; adb wait-for-device
        now=$(( $(adb shell date +%s | tr -d '\r') * 1000 ))
        calllog() {  # <number> <type: 1 in, 2 out, 3 missed, 5 declined> <minutes ago> <seconds> [name]
            local name=
            [ -n "${5:-}" ] && name="--bind name:s:'$5'"
            adb shell "content insert --uri content://call_log/calls --bind number:s:$1 --bind type:i:$2 --bind date:l:$((now - $3 * 60000)) --bind duration:i:$4 $name"
        }
        calllog 3381234567 5 14400 0 "Marco"
        calllog 3209998877 3 4320 0
        calllog 0298765432 2 1560 140 "Officina Rossi"
        calllog 3471112233 2 15 30 "Giulia Bianchi"
        calllog 3471112233 1 12 65 "Giulia Bianchi"
        adb unroot >/dev/null 2>&1; sleep 3; adb wait-for-device
    fi
    shoot phone-recents 1920 720 240 "$GRID_BUTTON;$(at phone);$WAIT"
    ;;
esac

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

# The setup list on the phone, as a new user finds it: myCar's data cleared and no permissions
# (the install above granted them all). Last, since it wipes myCar's data. 01 the list; 02 after
# "Get certificates", a real download from GitHub checked against the fingerprints; 03-* Android's
# dialogs after "Allow"; 04 the list after them; 05 Home after "Done"; 06 "How to connect" with the
# way back; 07 the list again; 08 the list with the contacts taken away.
tap_match() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    local b
    b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -m1 -F "$1" |
        sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p')
    if [ -z "$b" ]; then echo "setup-phone: nothing like $1 on screen"; return 1; fi
    set -- $b
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
tap_text() { tap_match "text=\"$1\""; }
has_text() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml | grep -qF "text=\"$1\""
}
# Scrolls the list a little at a time until $1 is on screen: a card's buttons can sit under the
# bar. Steps shorter than the list's height, so nothing goes by between two looks.
scroll_to() {
    local i
    for i in 1 2 3 4 5 6 7 8; do
        has_text "$1" && return 0
        adb shell input swipe 160 380 160 260 400; sleep 1
    done
    echo "setup-phone: $1 never came into sight"; return 1
}
setup_phone() {
    case " ${ONLY:-setup-phone} " in *" setup-phone "*) ;; *) return ;; esac
    local d=previews/setup-phone i
    mkdir -p $d
    adb shell pm clear "$PKG" >/dev/null
    adb logcat -c
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 6; adb exec-out screencap -p > $d/01-first.png
    tap_text "Get certificates"; sleep 12; adb exec-out screencap -p > $d/02-certificates.png
    # Android Auto comes with the emulator's Google image, as with many phones: kept, not turned off.
    if has_text "Open Android Auto"; then
        # The card can sit under the bar: brought into sight first, as a thumb would.
        scroll_to "No Disable button?" && tap_text "No Disable button?"; sleep 1
        scroll_to "Keep Android Auto"
        adb exec-out screencap -p > $d/02b-android-auto.png
        tap_text "Keep Android Auto"; sleep 2
        adb exec-out screencap -p > $d/02c-kept.png
    fi
    # The big button at the foot, always in sight: the step's own may be scrolled away.
    tap_text "Allow calls, map and music"
    for i in 1 2 3 4 5 6 7; do
        sleep 3
        adb exec-out screencap -p > $d/03-dialog-$i.png
        tap_match "permission_allow_foreground_only_button" || tap_match "permission_allow_button" || break
    done
    sleep 3; adb exec-out screencap -p > $d/04-after-allow.png
    tap_text "Done"; sleep 3; adb exec-out screencap -p > $d/05-home.png
    tap_text "How to connect"; sleep 3; adb exec-out screencap -p > $d/06-help.png
    tap_text "Take me back to the initial setup"; sleep 3; adb exec-out screencap -p > $d/07-return.png
    # Taking a permission away stops the app: open it again, and go back to the list.
    adb shell pm revoke "$PKG" android.permission.READ_CONTACTS
    sleep 2
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 5; tap_text "How to connect"; sleep 3
    tap_text "Take me back to the initial setup"; sleep 3; adb exec-out screencap -p > $d/08-partial.png
    # The intro, as after an update: its first page, the pirate flag hoisted and then in the wind.
    # The emulator runs with animations off (the runner's default), which stops the wind: on for
    # these shots only.
    adb shell am force-stop "$PKG"
    adb shell run-as "$PKG" rm -f shared_prefs/intro.xml
    adb shell settings put global animator_duration_scale 1
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 2.5; adb exec-out screencap -p > $d/09-intro-1.png
    sleep 0.6; adb exec-out screencap -p > $d/09-intro-2.png
    sleep 0.6; adb exec-out screencap -p > $d/09-intro-3.png
    adb shell settings put global animator_duration_scale 0
    adb logcat -d | grep -E "myCar|CERT|DOWNL" > $d/log.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- setup on the phone ---"
    grep -hE "CERT|DOWNL" $d/log.txt | tail -20 || echo "setup-phone: nothing in the log"
}
setup_phone

# The light theme isn't reachable from here; the dark default is what ships.
ls -lR previews
