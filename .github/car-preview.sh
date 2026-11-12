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
# LOGTAGS, more log tags to keep besides myCar's; KEEP_FILES keeps the shot before's files (its log).
# ONLY (the workflow's "shots" input) names the shots to take, all of them when empty.
shoot() {
    case " ${ONLY:-$1} " in *" $1 "*) ;; *) return ;; esac
    if ! adb get-state >/dev/null 2>&1; then
        echo "no emulator any more: $1 left out"
        return
    fi
    adb shell am force-stop "$PKG"
    for p in $VELA $PLANCIA $XONOS; do adb shell am force-stop "$p"; done
    [ -n "${KEEP_FILES:-}" ] || adb shell rm -rf "$FILES" 2>/dev/null
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

# alive <shot>: whether myCar still runs at the end of the shot (the shot leaves it running), and if
# not how it ended: the crash buffer (a native crash's tombstone too), what the web engine, the
# graphics and Android said about it, and Android's record of the process's exits. In alive.txt.
alive() {
    case " ${ONLY:-$1} " in *" $1 "*) ;; *) return ;; esac
    [ -d "previews/$1" ] || return
    adb get-state >/dev/null 2>&1 || return
    local pid
    pid=$(adb shell pidof "$PKG" | tr -d '\r')
    {
        echo "myCar's process at the end of the shot: ${pid:-none}"
        echo "--- crash buffer ---"
        adb logcat -d -b crash
        echo "--- native crashes, the web engine, graphics, kills ---"
        adb logcat -d -v time -b main,system | grep -E "DEBUG|Fatal signal|F libc|libc  |chromium|cr_|WebView|hwui|OpenGLRenderer|RenderThread|lowmemorykiller|Killing|has died|ActivityManager: Process" | tail -300
        echo "--- Android's record of myCar's exits ---"
        adb shell dumpsys activity exit-info "$PKG" | head -120
        echo "--- myCar's memory at the end ---"
        adb shell dumpsys meminfo "$PKG" | head -45
        echo "--- Android's settings on apps' memory ---"
        adb shell device_config list activity_manager | grep -iE "mem|limit|kill" | head -40
    } > "previews/$1/alive.txt" 2>&1
    echo "--- $1: myCar's process at the end: ${pid:-none (gone)} ---"
    grep -m 30 -E "Fatal signal|\*\*\* \*\*\*|signal [0-9]+ \(|Abort message|#[0-9]{2} pc" "previews/$1/alive.txt" || true
}

# How myCar's last process ended, at the top of the next log (LastExit): a native crash on purpose
# (--ei crashAfterMs, debug only: a SIGSEGV), then a shot that only starts and says how the one before
# ended - the signal, the crashing thread's frames - as a drive's log would after a crash.
EXTRA="--ei crashAfterMs 5000" shoot last-exit-crash 800 480 160 "$WAIT;$WAIT;$WAIT"
alive last-exit-crash
# The crashed shot's log stays (its pictures go): the new run also writes how it ended at its foot.
KEEP_FILES=1 BEFORE="adb shell 'rm -f $FILES/preview-*.png'" shoot last-exit 800 480 160 "$WAIT"
case " ${ONLY:-last-exit} " in *" last-exit "*)
    echo "--- last-exit: how the shot before ended, in myCar's log ---"
    grep -h "last exit" previews/last-exit/log.txt 2>/dev/null | cut -c1-250 | head -50 || echo "last-exit: nothing"
    echo "--- last-exit: the same at the foot of the crashed run's own log ---"
    grep -h "Android ended this run" previews/last-exit/mycar-*.log 2>/dev/null | cut -c1-250 | head -5 || true ;;
esac

# The pictures myCar draws for a cluster that wants turns as images (issue #23: a 2024 MG ZS asks
# for 1920x720 at 32 bits), as cluster-*.png from the debug preview (ClusterPreview): only when named.
case " ${ONLY:-} " in *" cluster-turns "*)
    EXTRA="--ez clusterTurns true" shoot cluster-turns 1920 720 240 "$WAIT" ;;
esac

# The hunt for "LayoutNode should be attached to an owner": scripted shots, only when named (crash-*).
source .github/car-preview-crash.sh

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
# Its Lyrics tab (the fourth, 237px after Browse): the made-up song isn't on LRCLIB, so "Looking for
# lyrics…", then one line, where the lyrics come from and "Now playing" back to the player.
EXTRA="--ez demoSong true" shoot media-lyrics 1920 720 240 "$WAIT;$WAIT;1635,200;$WAIT;1704,48;$WAIT;$WAIT;$WAIT"
# The player beside the map at Medium and Large (Settings > Size of the player beside the map).
EXTRA="--ez demoSong true --es playerSize 1.1" shoot split-medium 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es playerSize 1.2" shoot split-large 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es playerSize 1.2" shoot split-large-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es playerSize 1.4" shoot split-extra 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es playerSize 1.4" shoot split-extra-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT"
# The same with the car's screen at Extra large: the player as big as leaves the map room for an app's
# card (MAP_PANE_MIN_EDGE) and itself room for its X over the glass.
EXTRA="--ez demoSong true --es playerSize 1.4 --es carScale 1.5" shoot split-extra-xl-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT"
# The map and the player as panels raised off the wallpaper (car/design/Panels.kt): at 800x480, also at
# Extra small; at 1920x1080; in the light theme; over a moving wallpaper, which the map's corners
# painted over must follow; and with none.
EXTRA="--ez demoSong true" shoot split-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es carScale 0.83" shoot split-xs-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true" shoot split-1920x1080 1920 1080 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es carTheme LIGHT" shoot split-light 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es wallpaper waves" shoot split-waves 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es wallpaper none" shoot split-none 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
# The lyrics beside the map at Extra large, from the player's Lyrics button (the fourth of its bottom
# row): the made-up song has none, so the cover above one line where there's room (1920x720), the line
# alone at 800x480; 4 s later the player has the column back.
EXTRA="--ez demoSong true --es playerSize 1.4" shoot lyrics-side 1920 720 240 "$WAIT;$WAIT;1654,627;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es playerSize 1.4" shoot lyrics-side-extra-800x480 800 480 160 "$WAIT;$WAIT;693,423;$WAIT;$WAIT;$WAIT;$WAIT"
# The mini player over the whole map (car/MapMiniPlayer.kt). The player's X closes the column and the
# mini player floats in the map's bottom-right corner, over the page dots; held on its cover and dragged
# to the top middle (the log says where it was let go); its X puts it away (the card says how); the dock's
# song brings the column back, the column's X the mini player, where it was let go; a slow tap (0.9 s, not
# moved) on its cover still opens the full player. Then at 800x480, light, where an earlier drive left it,
# off; and at 800x480 with the car's screen at Large and Extra large (--es carScale), where the map is
# small: it starts clear of Vela's card of places as far as it goes, under its buttons, over the trip's time.
EXTRA="--ez demoSong true" shoot mini 1920 720 240 "$WAIT;1866,54;$WAIT;1536,582,drag,936,174;1254,174;72,279;1866,54;$WAIT;936,174,long"
EXTRA="--ez demoSong true" shoot mini-800x480 800 480 160 "$WAIT;764,36;$WAIT;544,388,drag,460,60"
EXTRA="--ez demoSong true --es carScale 1.25" shoot mini-large-800x480 800 480 160 "$WAIT;755,45;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es carScale 1.5" shoot mini-xl-800x480 800 480 160 "$WAIT;746,54;$WAIT;$WAIT"
EXTRA="--ez demoSong true --es carTheme LIGHT" shoot mini-light 1920 720 240 "$WAIT;1866,54;$WAIT"
EXTRA="--ez demoSong true --es miniAt 0.5,0" shoot mini-kept 1920 720 240 "$WAIT;1866,54;$WAIT"
EXTRA="--ez demoSong true --ez miniPlayer false" shoot mini-off 1920 720 240 "$WAIT;1866,54;$WAIT"
# The video cover (car/VideoCover.kt): the made-up song with a fake Canvas from the runner (10.0.2.2:8003,
# a 4 s looping 9:16 video made with ffmpeg), as if the car's music had video covers (--es videoCover,
# debug only), so the button on the cover shows (top left). Off, then tapped on: the video fades in over
# the cover and loops (shot every 2.5 s); then the player's X: the mini player over the map keeps the still
# cover. The same at 800x480. On from the start (--ez videoCoverOn true) with the car moving (--ez moving
# true): it plays all the same; the button then turns it off, back to the still cover. And Spotify Web as the
# drive's music, its column kept while it starts: the button there too, for the real service.
VIDEO_COVER=/tmp/video-cover
if [ -z "${ONLY:-}" ] || [[ " $ONLY " == *" video-cover"* ]]; then
    mkdir -p "$VIDEO_COVER"
    docker run --rm -v "$VIDEO_COVER":/out --entrypoint /ffmpeg mwader/static-ffmpeg:7.1 -loglevel error -y \
        -f lavfi -i "testsrc2=size=720x1280:rate=30:duration=4" -an -c:v libx264 -pix_fmt yuv420p -movflags +faststart \
        /out/canvas.mp4 || echo "video cover: no test video"
    python3 -m http.server 8003 --bind 127.0.0.1 --directory "$VIDEO_COVER" > /tmp/video-cover.txt 2>&1 &
    # Whether Spotify's web player still does what the page's script relies on for its Canvas (the runner
    # reaches open.spotify.com, the cloud session can't): facts in the job's log, for when Spotify changes.
    echo "--- Spotify's Canvas in its web player today ---"
    timeout 180 python3 .github/spotify-canvas.py 2>&1 | head -20 || true
fi
CANVAS="--ez demoSong true --es videoCover http://10.0.2.2:8003/canvas.mp4"
EXTRA="$CANVAS" shoot video-cover 1920 720 240 "$WAIT;$WAIT;1405,54;$WAIT;$WAIT;1866,54;$WAIT"
EXTRA="$CANVAS" shoot video-cover-800x480 800 480 160 "$WAIT;$WAIT;612,36;$WAIT;$WAIT;764,36;$WAIT"
EXTRA="$CANVAS --ez videoCoverOn true --ez moving true" shoot video-cover-moving 1920 720 240 "$WAIT;$WAIT;$WAIT;1405,54;$WAIT"
EXTRA="--es webMusic spotify --ez webMusicKey false" shoot video-cover-spotify 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT"
# The music not reaching the car (--ez musicNotReaching true, debug only: through myCar, Android's question
# turned down) with video covers possible, the car's screen at Extra large: the warning beside the X, where
# its "Ask again" can be read and touched, and no video cover button meanwhile (it was pushed under the glass).
EXTRA="$CANVAS --es carScale 1.5 --ez musicNotReaching true" shoot video-cover-not-reaching 1920 720 240 "$WAIT;$WAIT;$WAIT"
case " ${ONLY:-video-cover} " in *" video-cover"*)
    echo "--- video cover ---"
    grep -h "video cover\|preview: demo" previews/video-cover*/log.txt previews/video-cover*/stream.txt 2>/dev/null | cut -c1-300 | head -30 || true
    grep -h "GET" /tmp/video-cover.txt 2>/dev/null | head -10 || echo "video cover: nothing asked of the runner" ;;
esac
# Hiding an app from the grid: a long press on the browser for its menu, then "Hide from the app
# grid" (the fourth button of the card in the middle of the screen), the grid without it and the
# car saying where it comes back from. Then the settings' hidden apps, with the browser hidden.
shoot grid-hide 1920 720 240 "$GRID_BUTTON;$(at browser),long;960,447;$WAIT"
EXTRA="--es hideApps mycar:web --ez hiddenApps true" shoot hidden-apps 1920 720 240 "$WAIT"
EXTRA="--ez hiddenApps true" shoot hidden-apps-none 800 480 160 "$WAIT"

# Home with an app chosen to sit beside the map instead (car Settings > Beside the map).
installed $PLANCIA && EXTRA="--es sidePane $PLANCIA" shoot split-plancia 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
installed $XONOS && EXTRA="--es sidePane $XONOS" shoot split-xonos 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"

# Home's pages either side of the map, the ready-made ones: the dock's map first, as its touch
# brings back the dots that fade over the map, then a dot turns to its page - the weather and
# the agenda left of the map, Plancia and the browser right of it.
DOCK_MAP="72,174"
shoot pages 1920 720 240 "$DOCK_MAP;966,675;$WAIT;$DOCK_MAP;1098,675;$WAIT;$WAIT;$WAIT"
shoot pages-800x480 800 480 160 "48,116;404,450;$WAIT"
# The same pages in the light theme, as panels on its light ground.
EXTRA="--es carTheme LIGHT" shoot pages-light 1920 720 240 "$DOCK_MAP;966,675;$WAIT;$DOCK_MAP;1098,675;$WAIT"
# Changing a page: the pencil beside the dots, then a part, for the list of what can go there.
shoot pages-edit 1920 720 240 "$WAIT;$DOCK_MAP;966,675;1138,675;585,400;$WAIT"
# The layouts a page can take: the pencil, the Layout button at the start of its bar, the list of
# layouts; then "Half left" from it (the fourth tile of the first row).
shoot pages-layouts 1920 720 240 "$WAIT;$DOCK_MAP;966,675;1138,675;690,72;$WAIT;1430,252;$WAIT"
# Each layout beside the two halves and the four parts, on the page left of the map (--es pageLayout):
# the weather, the agenda, then empty parts.
for layout in full half-quarters quarters-half strip-quarters quarters-strip; do
    EXTRA="--es pageLayout $layout" shoot "layout-$layout" 1920 720 240 "$WAIT;$DOCK_MAP;966,675;$WAIT;$WAIT"
done
EXTRA="--es pageLayout half-quarters" shoot layout-800x480 800 480 160 "48,116;404,450;$WAIT"

# An upright car screen, as some cars have: Home, then the app grid and the dashboard; Home with the
# made-up song beside the real map, over 15s; Home's pages, left of the map and then right of it.
# At 720x1280 (160 dpi) and 1080x1920 (320 dpi); "portrait" in the workflow's shots takes them all.
# The taps are worked out in dp, as myCar lays the screen out, and give the 800x480 and 1920x720 taps
# above too: the dock 96 wide on the left, its clock 44 from the top, its map 116, the grid button 44
# from the bottom; the page dots 30 from the bottom, 44 apart around the middle of what's right of the
# dock, the map's dot in the middle. Each shot starts with a wait, as the first frames are slower here.
dp_at() { echo "$(($2 * $1 / 160)),$(($3 * $1 / 160))"; }   # dp_at <dpi> <x> <y>: "x,y" in pixels
portrait() {   # portrait <width> <height> <dpi>
    local w=$1 h=$2 dpi=$3
    local size=${w}x$h high=$((h * 160 / dpi)) mid=$((96 + (w * 160 / dpi - 96) / 2))
    local clock map grid left right
    clock=$(dp_at "$dpi" 48 44)
    map=$(dp_at "$dpi" 48 116)
    grid=$(dp_at "$dpi" 48 $((high - 44)))
    left=$(dp_at "$dpi" $((mid - 44)) $((high - 30)))
    right=$(dp_at "$dpi" $((mid + 44)) $((high - 30)))
    case " ${ONLY:-} " in *" portrait "*) ONLY="$ONLY portrait-$size portrait-split-$size portrait-pages-$size" ;; esac
    shoot "portrait-$size" "$w" "$h" "$dpi" "$WAIT;$WAIT;$WAIT;$grid;$clock"
    EXTRA="--ez demoSong true" shoot "portrait-split-$size" "$w" "$h" "$dpi" "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
    shoot "portrait-pages-$size" "$w" "$h" "$dpi" "$WAIT;$map;$left;$WAIT;$map;$right;$WAIT;$WAIT"
}
portrait 720 1280 160
portrait 1080 1920 320

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
    # A page left of the map in three parts, as a driver had it (--es pageParts): Plancia in the half,
    # the agenda and the weather in the quarters; the music beside the map. Its dot turns to it (the
    # dock's map first, which brings the dots back over the map), then a swipe on the weather goes back
    # to the map ("x,y,swipe,x2,y2"), eight times - the last four as soon as the page is there. The
    # driver's report (0.6.9): myCar went away as the page went back to the map. alive.txt says whether
    # myCar still runs at the end, and how it went if not.
    swipes() {
        local taps="$WAIT" i
        for i in 1 2 3 4; do taps="$taps;$1;$2;$WAIT;$3;$WAIT"; done
        for i in 1 2 3 4; do taps="$taps;$1;$2;$3"; done
        echo "$taps;$WAIT;$WAIT"
    }
    SWIPE_PAGE="--es pageLayout half-quarters --es pageParts plancia,agenda,weather --ez demoSong true"
    EXTRA="$HA $SWIPE_PAGE" shoot plancia-swipe 1920 720 240 "$(swipes "$DOCK_MAP" 966,675 1700,560,swipe,500,560)"
    alive plancia-swipe
    EXTRA="$HA $SWIPE_PAGE" shoot plancia-swipe-800x480 800 480 160 "$(swipes 48,116 404,450 720,380,swipe,220,380)"
    alive plancia-swipe-800x480
    # The same with Spotify's web player as the car's music, a second web page in myCar as in that drive
    # (no account here: it loads, signed out, and nothing plays).
    EXTRA="$HA --es pageLayout half-quarters --es pageParts plancia,agenda,weather --es webMusic spotify" \
        shoot plancia-swipe-spotify 800 480 160 "$(swipes 48,116 404,450 720,380,swipe,220,380)"
    alive plancia-swipe-spotify
    echo "--- Plancia inside myCar ---"
    grep -h "plancia:\|web:" previews/plancia-app/log.txt 2>/dev/null || echo "plancia-app: nothing in the log"
    # What isn't one of myCar's commands goes to Home Assistant's Assist (car/AssistantBrain), as if
    # said 8s in: the date; then in Italian, which the run's English pipeline doesn't speak, so its
    # agent is asked in Italian.
    EXTRA="$HA --es voiceHeard 'what is the date today'" shoot brain-ha 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
    EXTRA="$HA --es voiceLang it --es voiceHeard 'che giorno è oggi'" shoot brain-ha-it 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
    echo "--- Home Assistant's Assist, from myCar's assistant ---"
    grep -h "assistant:\|preview: heard" previews/brain-ha/log.txt previews/brain-ha-it/log.txt 2>/dev/null || echo "brain: nothing in the log"
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
    # The album played as in xonos-inside, then the Lyrics tab (the fourth): Tone 1 has words in its
    # LYRICS tag, which come from Music Assistant first ("lyrics: found (Music Assistant)" in the log).
    # Best on its own (shots: xonos-lyrics): Xonos playing has taken the emulator down now and then.
    LOGTAGS=Xonos EXTRA="$MA" shoot xonos-lyrics 1920 720 240 "$WAIT;$GRID_BUTTON;$(at xonos);$WAIT;1230,48;$WAIT;1300,565;$WAIT;1808,232;$WAIT;$WAIT;1704,48;$WAIT;$WAIT;$WAIT"
    adb shell am force-stop "$PKG"
    case " ${ONLY:-xonos-lyrics} " in *" xonos-lyrics "*)
        echo "--- lyrics with Xonos inside myCar ---"
        grep -h "lyrics:" previews/xonos-lyrics/log.txt previews/xonos-lyrics/stream.txt 2>/dev/null || echo "xonos-lyrics: no lyrics line" ;;
    esac
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
if [ -z "${ONLY:-}" ] || [[ " $ONLY " == *" cinema-"* ]] || [[ " $ONLY " == *" open-with"* ]]; then
mkdir -p "$CINEMA_MEDIA/tv"
FFMPEG="docker run --rm -v $CINEMA_MEDIA:/out --entrypoint /ffmpeg mwader/static-ffmpeg:7.1 -loglevel error -y"
# Its index first (faststart): from the runner's server, which answers no Range request, Android's player
# reads a film from the start only, and with the index at the end it stopped at once (error 1, 0x80000000).
$FFMPEG -f lavfi -i "testsrc2=size=1280x720:rate=30:duration=60" -f lavfi -i "sine=f=440:d=60" \
    -c:v libx264 -pix_fmt yuv420p -c:a aac -shortest -movflags +faststart /out/test-video.mp4 || echo "cinema: no test video"
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
# Cinema for other apps (car/OpenInCinemaActivity.kt), sent with adb 5 s into the shot, as another app sends it.
# NewPipe's "play with an external player" (the stream's address, its type, its title in EXTRA_TITLE) with a video
# on the internet (NET_VIDEO): the car's screen turns to Cinema's player at once, playing; the same
# with the car moving, covered and still sounding. What could reach the home network waits for the driver's yes on
# the car's screen (CinemaAskCard: the question, where it leads, "Not now" and "Open", here tapped): the runner's
# video (10.0.2.2, a private address), its stream (HLS, NewPipe's type for a live one), YouTube's page shared (then
# on Cinema's YouTube at that video), a page off Cinema's list (the runner's, in Cinema's own browser; then the map,
# the grid and Cinema: "Continue" brings it back). Two pages shared one after the other: the card shows the second,
# "Not now" drops it and nothing opens. Last, the video sent before the car's screen is up, as with no car: not
# kept, Home stays as it was.
OPEN_WITH="$PKG/io.github.sirionero.mycar.car.OpenInCinemaActivity"
open_with() { adb shell "am start -n $OPEN_WITH -a android.intent.action.VIEW -t $1 -d $2 --es android.intent.extra.TITLE 'Test video'"; }
share_with() { adb shell "am start -n $OPEN_WITH -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT '$1'"; }
# The video on the internet: the first of these the runner reaches (Google's sample videos, tried first, answered
# 403 in November 2026: the player waited on them for good).
NET_VIDEO=https://www.w3schools.com/html/mov_bbb.mp4
case " ${ONLY:-open-with} " in *" open-with"*)
    for url in https://mdn.github.io/shared-assets/videos/flower.mp4 https://interactive-examples.mdn.mozilla.net/media/cc0-videos/flower.mp4 \
        https://www.w3schools.com/html/mov_bbb.mp4; do
        if curl -sfIL --max-time 15 "$url" > /dev/null; then NET_VIDEO=$url; break; fi
    done
    echo "open-with: the video on the internet: $NET_VIDEO" ;;
esac
SENT_VIDEO="open_with video/mp4 $NET_VIDEO"
OPEN_ASKED="1239,435"     # the card's "Open", at 1920x720
NOT_NOW_ASKED="825,435"   # its "Not now"
shoot open-with 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT" "$SENT_VIDEO"
EXTRA="--ez moving true" shoot open-with-moving 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT" "$SENT_VIDEO"
shoot open-with-home 1920 720 240 "$WAIT;$WAIT;$OPEN_ASKED;$WAIT;$WAIT;$WAIT" "open_with video/mp4 http://10.0.2.2:8001/test-video.mp4"
shoot open-with-stream 1920 720 240 "$WAIT;$WAIT;$OPEN_ASKED;$WAIT;$WAIT;$WAIT" "open_with application/x-mpegURL http://10.0.2.2:8001/tv/live.m3u8"
shoot open-with-share 1920 720 240 "$WAIT;$WAIT;$OPEN_ASKED;$WAIT;$WAIT;$WAIT;$WAIT" "share_with 'Big Buck Bunny https://www.youtube.com/watch?v=aqz-KE-bpKQ'"
shoot open-with-page 1920 720 240 "$WAIT;$WAIT;$OPEN_ASKED;$WAIT;$DOCK_MAP;$WAIT;$GRID_BUTTON;$(at cinema);$WAIT;$CINEMA_PHONE;$WAIT;$WAIT" "share_with 'On the runner: http://10.0.2.2:8001/'"
shoot open-with-twice 1920 720 240 "$WAIT;$WAIT;$WAIT;$NOT_NOW_ASKED;$WAIT" "share_with 'https://www.example.org/first'; sleep 2; share_with 'http://10.0.2.2:8001/tv/'"
BEFORE="$SENT_VIDEO; sleep 3" shoot open-with-no-car 1920 720 240 "$WAIT;$WAIT"
case " ${ONLY:-open-with} " in *" open-with"*)
    echo "--- Cinema for other apps: who takes them on the emulator ---"
    adb shell cmd package query-activities --components -a android.intent.action.VIEW -t video/mp4 -d http://10.0.2.2:8001/test-video.mp4
    adb shell cmd package query-activities --components -a android.intent.action.VIEW -t application/x-mpegURL -d http://10.0.2.2:8001/tv/live.m3u8
    adb shell cmd package query-activities --components -a android.intent.action.SEND -t text/plain | grep -i mycar || echo "share: myCar not among them"
    echo "--- Cinema for other apps: myCar's log ---"
    grep -h "cinema:" previews/open-with*/log.txt previews/open-with*/stream.txt 2>/dev/null | cut -c1-300 | head -60 || echo "open-with: nothing in the log" ;;
esac
echo "--- Cinema ---"
grep -h "cinema" previews/cinema*/log.txt 2>/dev/null | head -40 || echo "cinema: nothing in the log"
grep -h "GET" /tmp/cinema-media.txt 2>/dev/null | head -20 || echo "cinema: nothing asked of the runner"
# The sites Cinema suggests, as the runner reaches them (the cloud session can't).
for site in https://www.youtube.com https://www.raiplay.it https://www.twitch.tv https://www.dailymotion.com https://vimeo.com \
    https://www.la7play.it https://www.ted.com https://www.arte.tv https://plus.nasa.gov https://archive.org/details/feature_films; do
    echo "cinema site $site: $(curl -s -o /dev/null -L --max-time 20 -w '%{http_code} %{url_effective}' "$site")"
done

# The music services' web players inside myCar (webmusic/, car/WebMusicPage.kt), each on its own in
# the car (--es webMusic, debug only) and opened a few seconds in as the car's music, with the car's
# media key: the service's own page, as on a computer. No account in CI: the pages are signed out, so
# nothing plays. myCar's log says how each loaded: its address, the user agent the page sees, whether
# the web engine takes protected audio (Widevine) and whether the page said what plays (its Media
# Session); the debug build adds the page's parts a few seconds later, to fit it better.
MUSIC_SERVICES="spotify youtube-music apple-music amazon-music deezer tidal soundcloud qobuz"
for service in $MUSIC_SERVICES; do
    EXTRA="--es webMusic $service" shoot "music-$service" 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
done
EXTRA="--es webMusic spotify" shoot music-spotify-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
# Playing, signed out, where a service lets you: its page's first "Play" pressed a few seconds in
# (--ez webMusicPlay, debug only), then the map, with myCar's player beside it showing what the page
# plays, and its song in the dock - the page's Media Session through myCar's stand-in, as in a car.
for service in soundcloud youtube-music; do
    EXTRA="--es webMusic $service --ez webMusicPlay true" shoot "music-$service-play" 1920 720 240 \
        "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$DOCK_MAP;$WAIT;$WAIT"
done
# "Play lofi" for the voice assistant (--es webMusicSearch, debug only: asked of the car's music app
# 14 s in, as the assistant does): the service's own search, then the Play of what it finds first.
for service in soundcloud youtube-music spotify; do
    EXTRA="--es webMusic $service --es webMusicSearch lofi" shoot "music-$service-search" 1920 720 240 \
        "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$DOCK_MAP;$WAIT;$WAIT"
done
# Spotify's tile on the app grid, beside the other music apps ("Spotify", "Web" under it).
EXTRA="--es webMusic spotify" shoot music-grid 1920 720 240 "$WAIT;$WAIT;$GRID_BUTTON;$WAIT"
# The map's surfaces as a drive with music starts (CarServices.musicStarting): Vela, and Spotify's web
# player as the drive's music (Autoplay on, as by default). The player's column is kept from the start,
# so the map's first surface should already be the narrow one, and "Starting Spotify…" beside it; with
# no account here no song comes, and 20 s in the map gets its whole width. No media key (--ez
# webMusicKey false): pressed first, it opened Spotify as the driver's pick, not the drive's.
installed $VELA && EXTRA="--es webMusic spotify --ez webMusicKey false" shoot map-start 1920 720 240 \
    "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
if [ -d previews/map-start ]; then
    echo "--- the map's surfaces as a drive with music starts: the first should be the narrow one ---"
    grep -h "stage: surface\|host: lent\|media: \|preview: the car's player" \
        previews/map-start/log.txt previews/map-start/stream.txt 2>/dev/null | cut -c1-300 | head -40
fi
echo "--- the music services' web players ---"
for d in previews/music-*; do
    [ -d "$d" ] || continue
    echo "[$(basename "$d")]"
    grep -h "music:\|media:\|cinema: no\|clipboard:\|the car's player" "$d/log.txt" "$d/stream.txt" 2>/dev/null | cut -c1-1500 | head -60
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
EXTRA="--es wallpaper mycar" shoot motion-mycar 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
EXTRA="--es wallpaper mycar" shoot motion-mycar-800x480 800 480 160 "48,436;$WAIT;$WAIT"
EXTRA="--es wallpaper waves" shoot motion-waves 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
EXTRA="--es wallpaper fireflies" shoot motion-fireflies 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
EXTRA="--es wallpaper lava" shoot motion-lava 1920 720 240 "$GRID_BUTTON;$WAIT;$WAIT"
# The wallpaper picker, still and moving swatches.
EXTRA="--ez wallpapers true" shoot wallpapers 1920 720 240 "$WAIT;$WAIT"

# The player docked beside the map, playing a made-up song (the emulator has none of its own).
EXTRA="--ez player true" shoot player 1920 720 240 "$WAIT"
EXTRA="--ez player true" shoot player-800x480 800 480 160 "$WAIT"

# An app's alert across the top of the car's screen (CarAlerts; --ez alert true puts up one like
# Waze's question about a hazard, 12 s long, with a blue button and a neutral one). At 1920x720
# "Still there" is tapped, at 800x480 its time runs out, and once with the light theme. The log
# says what the app was told.
EXTRA="--ez alert true" shoot alert 1920 720 240 "$WAIT;800,200;$WAIT"
EXTRA="--ez alert true" shoot alert-800x480 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez alert true --es carTheme LIGHT" shoot alert-light 1920 720 240 "$WAIT"
case " ${ONLY:-alert} " in *" alert "*|*" alert-800x480 "*)
    echo "--- alert: the button tapped and the time run out, as the app was told ---"
    grep -h "alert" previews/alert/log.txt previews/alert-800x480/log.txt 2>/dev/null | cut -c1-200 | head -20 || echo "alert: nothing" ;;
esac

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

# The Jolly key (car/Jolly*.kt), in the dock just above the microphone: 296 dp down (184 dp from the
# bottom), the microphone 70 dp under it. "jolly" in the workflow's shots takes them all.
# - jolly-800x480: Home with the key and two slots above it (the user's Golf), then a tap: its command
#   "what time is it" as if said - the assistant shows it and answers; then held: the chooser, its kinds
#   in two rows.
# - jolly-1920x720: nothing chosen yet, so the tap opens the chooser; a second tap on the lit key closes it.
# - jolly-choose: the chooser from a long press ("Assistant command" lit, what the key does now), that
#   kind's list (the key's own command, then the ready-made ones), "Read my messages" chosen: back
#   Home with the car saying what the key does now.
# - jolly-say-it: a command written in Italian, myCar in English: understood and answered in Italian.
# - jolly-light: Home and the chooser in the light theme.
# - jolly-large-800x480 / jolly-xl-800x480: Large keeps the key and one slot (the map); at Extra large
#   there's no room for both, and the map keeps its place.
# - jolly-ai, jolly-apps, jolly-contacts: the lists of assistants, apps and contacts (two contacts written
#   as root, one with two numbers: tapped, its numbers).
# - jolly-navigate: the key set to a place (the Castello Sforzesco): the map on Home (Vela) takes it, as
#   it takes the assistant's "navigate to".
# - jolly-it-800x480, jolly-de, jolly-en-XP: the chooser in Italian at 800x480, in German, in pirate speak;
#   jolly-ru-800x480, jolly-el-800x480, jolly-pt-XP-800x480: where the names of the kinds are longest.
# - jolly-settings: the car settings' rows on their own (--ez jollySettings true).
# - jolly-phone: on the phone, Settings > Car screen: the section, the kinds, a ready-made command picked
#   and saved, a place written with the phone's keyboard and saved, the contacts (after jolly-contacts)
#   and a number chosen.
JOLLY_KEY="72,444"         # at 1920x720
JOLLY_KEY_800="48,296"
JOLLY_TILE_CALL="314,222"  # the kinds, one row at 1920x720
JOLLY_TILE_AI="601,222"
JOLLY_TILE_HOME="888,222"
JOLLY_TILE_SAY="1462,222"
JOLLY_TILE_APP="1749,222"
JOLLY_ROW_1="1032,186"     # the lines of a kind's list, 80 dp apart
JOLLY_ROW_2="1032,306"
case " ${ONLY:-} " in *" jolly "*)
    ONLY="$ONLY jolly-800x480 jolly-1920x720 jolly-choose jolly-say-it jolly-light jolly-large-800x480 jolly-xl-800x480"
    ONLY="$ONLY jolly-ai jolly-navigate jolly-apps jolly-contacts jolly-home jolly-unlock jolly-lock jolly-lock-stuck"
    ONLY="$ONLY jolly-it-800x480 jolly-de jolly-en-XP"
    ONLY="$ONLY jolly-settings jolly-phone jolly-ru-800x480 jolly-el-800x480 jolly-pt-XP-800x480" ;;
esac
EXTRA="--es jolly say" shoot jolly-800x480 800 480 160 "$WAIT;$JOLLY_KEY_800;$WAIT;$WAIT;$JOLLY_KEY_800,long;$WAIT"
shoot jolly-1920x720 1920 720 240 "$WAIT;$JOLLY_KEY;$WAIT;$JOLLY_KEY;$WAIT"
EXTRA="--es jolly say" shoot jolly-choose 1920 720 240 "$WAIT;$JOLLY_KEY,long;$JOLLY_TILE_SAY;$JOLLY_ROW_2;$WAIT"
EXTRA="--es jolly say --es jollySay 'che ore sono' --es jollyLang it" shoot jolly-say-it 1920 720 240 "$WAIT;$JOLLY_KEY;$WAIT;$WAIT"
EXTRA="--es jolly say --es carTheme LIGHT" shoot jolly-light 1920 720 240 "$WAIT;$JOLLY_KEY,long;$WAIT"
EXTRA="--es jolly say --es carScale 1.25" shoot jolly-large-800x480 800 480 160 "$WAIT"
EXTRA="--es jolly say --es carScale 1.5" shoot jolly-xl-800x480 800 480 160 "$WAIT"
shoot jolly-ai 1920 720 240 "$WAIT;$JOLLY_KEY;$JOLLY_TILE_AI;$WAIT"
installed $VELA && EXTRA="--es jolly navigate" shoot jolly-navigate 1920 720 240 "$WAIT;$WAIT;$JOLLY_KEY;$WAIT;$WAIT;$WAIT;$WAIT"
EXTRA="--ez jollySettings true --es jolly call" shoot jolly-settings 1920 720 240 "$WAIT"
jolly_lang() {  # jolly_lang <shot> <locale> <width> <height> <dpi> <the key>: the chooser in that language
    case " ${ONLY:-$1} " in *" $1 "*) ;; *) return ;; esac
    BEFORE="adb shell cmd locale set-app-locales $PKG --locales $2" EXTRA="--es jolly say" \
        shoot "$1" "$3" "$4" "$5" "$WAIT;$6,long;$WAIT"
    adb shell cmd locale set-app-locales "$PKG" --locales en-US
}
jolly_lang jolly-it-800x480 it 800 480 160 "$JOLLY_KEY_800"
jolly_lang jolly-de de 1920 720 240 "$JOLLY_KEY"
jolly_lang jolly-en-XP en-XP 1920 720 240 "$JOLLY_KEY"
jolly_lang jolly-ru-800x480 ru 800 480 160 "$JOLLY_KEY_800"
jolly_lang jolly-el-800x480 el 800 480 160 "$JOLLY_KEY_800"
jolly_lang jolly-pt-XP-800x480 pt-XP 800 480 160 "$JOLLY_KEY_800"
shoot jolly-apps 1920 720 240 "$WAIT;$JOLLY_KEY;$JOLLY_TILE_APP;$WAIT"
case " ${ONLY:-jolly-contacts} " in *" jolly-contacts "*)
    if adb root >/dev/null 2>&1; then
        sleep 3; adb wait-for-device
        contact() {  # <name> <number> [number]: the first a mobile number, the second a work one
            adb shell "content insert --uri content://com.android.contacts/raw_contacts --bind account_type:n: --bind account_name:n:"
            local id type=2 number name=$1
            id=$(adb shell "content query --uri content://com.android.contacts/raw_contacts --projection _id" | tr -d '\r' | sed -n 's/.*_id=\([0-9]*\).*/\1/p' | sort -n | tail -1)
            adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/name --bind data1:s:'$name'"
            shift
            for number in "$@"; do
                adb shell "content insert --uri content://com.android.contacts/data --bind raw_contact_id:i:$id --bind mimetype:s:vnd.android.cursor.item/phone_v2 --bind data1:s:$number --bind data2:i:$type"
                type=3
            done
        }
        contact "Anna Verdi" 3335550101 0255501010
        contact "Bruno Neri" 3475550202
        adb unroot >/dev/null 2>&1; sleep 3; adb wait-for-device
    fi
    shoot jolly-contacts 1920 720 240 "$WAIT;$JOLLY_KEY;$JOLLY_TILE_CALL;$JOLLY_ROW_1;$WAIT"
    ;;
esac
# With the run's own Home Assistant: a script, a button and a switch of its own (made here, as the
# install has none), then the chooser's Home Assistant list from the drive's connection, the script
# chosen, and the key running it ("Jolly test: done"). Then a lock that isn't there: the first tap
# asks for a second, the second one goes, and the car says Home Assistant doesn't have it (nothing is
# sent). Then a lock that's a state alone (written with the REST API, no device behind it): in
# jolly-lock a second tap 300 ms after the first does nothing, a third at 1.6 s goes, and when the
# "lock" says unlocked a couple of seconds later (the REST API again) the car says it's done; in
# jolly-lock-stuck it never does, and 10 s after the second tap the car says it's still locked.
if [ -n "${HA_TOKEN:-}" ]; then
    case " ${ONLY:-jolly-home} " in *" jolly-home "*)
        curl -sf -X POST "http://localhost:8123/api/config/script/config/jolly_test" -H "Authorization: Bearer $HA_TOKEN" \
            -H 'Content-Type: application/json' -d '{"alias":"Jolly test","sequence":[{"delay":{"seconds":1}}]}' >/dev/null ||
            echo "jolly: no script made"
        python3 - "$HA_TOKEN" <<'HELPERS' || echo "jolly: no helpers made"
import json, sys
import websocket

ws = websocket.create_connection("ws://localhost:8123/api/websocket")
ws.recv()
ws.send(json.dumps({"type": "auth", "access_token": sys.argv[1]}))
ws.recv()
for i, (kind, name) in enumerate([("input_button", "Garage"), ("input_boolean", "Porch light")], start=1):
    ws.send(json.dumps({"id": i, "type": f"{kind}/create", "name": name}))
    print("jolly:", kind, json.loads(ws.recv()).get("success"))
HELPERS
        ;;
    esac
    lock_state() {  # lock_state <state>: lock.jolly_door as a lock would report it, a state alone
        curl -sf -X POST "http://localhost:8123/api/states/lock.jolly_door" -H "Authorization: Bearer $HA_TOKEN" \
            -H 'Content-Type: application/json' -d "{\"state\":\"$1\",\"attributes\":{\"friendly_name\":\"Jolly door\"}}" >/dev/null ||
            echo "jolly: lock state $1 not written"
    }
    HA="--es haUrl http://10.0.2.2:8123 --es haToken $HA_TOKEN"
    EXTRA="$HA" shoot jolly-home 1920 720 240 "$WAIT;$WAIT;$JOLLY_KEY;$JOLLY_TILE_HOME;$JOLLY_ROW_1;$JOLLY_KEY;$WAIT"
    EXTRA="$HA --es jolly unlock" shoot jolly-unlock 1920 720 240 "$WAIT;$WAIT;$JOLLY_KEY;$JOLLY_KEY;$WAIT"
    case " ${ONLY:-jolly-lock} " in *" jolly-lock "*)
        lock_state locked
        # The key (72,444) tapped by a script, for the timing: at 12 s, 300 ms later, 1.6 s after the first.
        EXTRA="$HA --es jolly unlock --es jollyHome lock.jolly_door --es script 'wait:12000;tap:72,444;wait:300;tap:72,444;wait:1200;tap:72,444;wait:1500;snap:waiting;wait:2500;snap:after'" \
            shoot jolly-lock 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT" "sleep 11; lock_state unlocked"
        ;;
    esac
    case " ${ONLY:-jolly-lock-stuck} " in *" jolly-lock-stuck "*)
        lock_state locked
        EXTRA="$HA --es jolly unlock --es jollyHome lock.jolly_door" shoot jolly-lock-stuck 1920 720 240 \
            "$WAIT;$WAIT;$WAIT;$WAIT;$JOLLY_KEY;$JOLLY_KEY;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
        ;;
    esac
    case " ${ONLY:-jolly-home} " in *" jolly-home "*|*" jolly-unlock "*|*" jolly-lock "*|*" jolly-lock-stuck "*)
        echo "--- the Jolly key and Home Assistant ---"
        grep -h "jolly\|plancia: connected\|preview: step" previews/jolly-home/log.txt previews/jolly-unlock/log.txt \
            previews/jolly-lock/log.txt previews/jolly-lock-stuck/log.txt 2>/dev/null || echo "jolly-home: nothing in the log" ;;
    esac
else
    echo "--- no Home Assistant: the Jolly key's Home Assistant shots left out ---"
fi
case " ${ONLY:-jolly-800x480} " in *" jolly-800x480 "*)
    echo "--- the Jolly key ---"
    grep -h "jolly\|assistant: a written" previews/jolly-*/log.txt 2>/dev/null | head -60 || echo "jolly: nothing in the log" ;;
esac

# A call under way (car/CallPill.kt): its pill over every screen until it ends, a call picked up 1:23 ago
# with no modem behind it (--ez fakeCall true, debug only). Over the map, the app grid and the car's
# Settings, each given a wait (the emulator draws a new screen slowly); held and dragged to the bottom
# left, where it stays; a tap on it opens the Phone. At 800x480, Normal and Extra large (drawn smaller);
# light. Over the mini player: the column's X brings it out, the pill dragged onto its corner moves it up,
# and the pill dragged away lets it back. Then the emulator's modem rings, myCar's Answer takes the call
# (the pill), and the pill's red button ends it. "call-bubble" in the workflow's shots takes them all.
# At 1920x720 (240 dpi) the pill starts 810-1254 px across, 18-114 down.
case " ${ONLY:-} " in *" call-bubble "*)
    ONLY="$ONLY call-bubble-800x480 call-bubble-xl-800x480 call-bubble-light call-bubble-mini call-bubble-knob call-answer" ;;
esac
EXTRA="--ez fakeCall true" shoot call-bubble 1920 720 240 \
    "$WAIT;$GRID_BUTTON;$WAIT;$(at settings);$WAIT;900,66,drag,400,640;$WAIT;450,640;$WAIT"
EXTRA="--ez fakeCall true" shoot call-bubble-800x480 800 480 160 "$WAIT;48,436;$WAIT"
EXTRA="--ez fakeCall true --es carScale 1.5" shoot call-bubble-xl-800x480 800 480 160 "$WAIT;72,414;$WAIT"
EXTRA="--ez fakeCall true --es carTheme LIGHT" shoot call-bubble-light 1920 720 240 "$WAIT;$GRID_BUTTON;$WAIT;$(at settings);$WAIT"
EXTRA="--ez fakeCall true --ez demoSong true" shoot call-bubble-mini 1920 720 240 \
    "$WAIT;1866,54;$WAIT;900,66,drag,1700,700;$WAIT;1550,654,drag,900,300;$WAIT"
# The car's knob (a script, see PreviewScript: knob:n turns it, knob:ok presses it). Its first turn puts the
# focus on Home (the weather of the page left of the map), the next one back on the dock's foot; back
# along the dock (mic, the Jolly key, map, clock) and round past its top, the last thing on the screen:
# the pill's red button, at the seventh turn (a picture after each). Pressed, it ends the call ("calls:
# there was no call to end", for the made-up one). Seen (0.7.4): the ring on the red button, then on the
# rest of the pill.
EXTRA="--ez fakeCall true --es script 'wait:7000;knob:-1;wait:600;knob:-1;wait:600;rep:5;knob:-1;wait:700;snap:;end;knob:ok;wait:1500;snap:pressed'" \
    shoot call-bubble-knob 1920 720 240 "$WAIT;$WAIT;$WAIT;$WAIT;$WAIT;$WAIT"
# The ringing card's Answer is 1068-1176 px across, 444-579 down (with or without its Bluetooth line);
# the pill's red button 1158-1254 across.
shoot call-answer 1920 720 240 "$WAIT;$WAIT;1122,510;$WAIT;$WAIT;1206,66;$WAIT;$WAIT" "adb emu gsm call 3339876543"
adb emu gsm cancel 3339876543 >/dev/null 2>&1

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

# The same with Home Assistant set up in myCar (car/AssistantBrain): the phone stays locked, myCar
# listens itself and says so on the car's screen, and nothing on the phone asks to be unlocked.
if [ -n "${HA_TOKEN:-}" ]; then
    EXTRA="$HA --es assistant phone" shoot brain-locked 1920 720 240 \
        "$WAIT;$WAIT;$WAIT;$GRID_BUTTON;$MIC_BUTTON;$WAIT;$WAIT" \
        "lock_phone; sleep 10; mkdir -p previews/brain-locked; \
         adb exec-out screencap -p > previews/brain-locked/phone.png"
    adb shell locksettings clear --old $PIN || true
    adb shell input keyevent KEYCODE_WAKEUP
    adb shell wm dismiss-keyguard || true
    adb shell cmd role remove-role-holder --user 0 android.app.role.ASSISTANT "$PKG" || true
    echo "--- the microphone with the phone locked, Home Assistant set up ---"
    grep -h "assistant:\|test assistant: shown\|plancia: connected" previews/brain-locked/log.txt 2>/dev/null ||
        echo "brain-locked: nothing in the log"
fi

# The car map's position with the phone locked (host/LocationKeepAlive.kt): Android lets a location
# foreground service started from the background lend its location only within 10 s of one of the
# app's screens being created (the trip of 2 Nov 2026, on Android 17: refused 14 s in). myCar's screen
# opened with the phone locked by a PIN and its screen off, as the car's Bluetooth opens it in a
# pocket; the drive's hold taken 1 s in (--ei locationProbeMs, debug only) should run, the same taken
# 15 s in should be refused. The wallpaper picker alone, so that no car map binds and takes the service
# first (a car map has to be installed, though: Vela). Meant for api 37.0; the two runs in at-1000/ and
# at-15000/, each with Android's account of myCar's services (dumpsys.txt).
case " ${ONLY:-location-locked} " in *" location-locked "*)
    adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
    adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
    rm -rf previews/location-locked previews/location-locked-runs
    mkdir -p previews/location-locked-runs
    for at in 1000 15000; do
        LOGTAGS=ActivityManager \
            BEFORE="adb shell locksettings set-pin $PIN; adb shell input keyevent KEYCODE_SLEEP; sleep 3" \
            EXTRA="--ez wallpapers true --ei locationProbeMs $at" shoot location-locked 800 480 160 "$WAIT;$WAIT;$WAIT;$WAIT" \
            "sleep $(( at / 1000 + 3 > 5 ? at / 1000 - 2 : 0 )); adb shell dumpsys activity services $PKG > previews/location-locked/dumpsys.txt 2>&1"
        [ -d previews/location-locked ] && mv previews/location-locked "previews/location-locked-runs/at-$at"
        adb shell locksettings clear --old $PIN || true
        adb shell input keyevent KEYCODE_WAKEUP
        adb shell wm dismiss-keyguard || true
        sleep 2
    done
    mv previews/location-locked-runs previews/location-locked
    echo "--- the car map's position with the phone locked: the drive's hold 1 s in, then 15 s in ---"
    for at in 1000 15000; do
        echo "[at $at ms]"
        grep -h "preview: location\|location keep-alive\|LocationKeepAlive\|preview: displayId" \
            "previews/location-locked/at-$at/log.txt" "previews/location-locked/at-$at/stream.txt" 2>/dev/null | cut -c1-400 | head -20
        grep -iE "LocationKeepAlive|isForeground|foregroundServiceType|WhileInUse|Wiu" \
            "previews/location-locked/at-$at/dumpsys.txt" 2>/dev/null | head -20
    done
    ;;
esac

# A car arriving over Bluetooth with myCar's screen open from before and the phone locked
# (LocationKeepAlive.screenForArrival, 0.7.1): the car allowed through an association made by the
# shell, as Android's "Allow" makes it, and its arrival simulated by Android's companion service.
# myCar's screen was created well over 10 s before, so only the screen made for the arrival can let
# the position service start. In the log: the refusal, then "trying again" and "running".
# Android's view of the phone and of myCar at a moment of location-arrival, in <file>.
arrival_state() {
    {
        echo "uid state: $(adb shell cmd activity get-uid-state "$ARRIVAL_UID" 2>&1 | tr -d '\r')"
        adb shell dumpsys power | grep -E "mWakefulness=|mStayOn=|mIsPowered=" | tr -d '\r'
        adb shell dumpsys window | grep -iE "KeyguardShowing|KeyguardOccluded|mCurrentFocus|mFocusedApp" | tr -d '\r' | head -8
        adb shell dumpsys activity activities | grep -iE "ResumedActivity|isSleeping|mSleeping" | tr -d '\r' | head -8
        adb shell dumpsys activity processes "$PKG" | grep -iE "procState|curAdj|ProcessRecord" | tr -d '\r' | head -8
    } > "$1" 2>&1
}

# arrival <where myCar was left: home|top> <screen at the arrival: off|keyguard> <without|with>
arrival() {
    local d=previews/location-arrival/$1-$2-$3 id out logpid
    mkdir -p $d
    adb shell cmd companiondevice associate 0 $PKG $CAR > $d/companion.txt 2>&1
    adb shell cmd companiondevice list 0 >> $d/companion.txt 2>&1
    # "pkg mac id" before Android 15 or so, "Association{mId=1, ... mDeviceMacAddress=...}" on 17.
    id=$(tr -d '\r' < $d/companion.txt | grep -i "$CAR" | grep -o 'mId=[0-9]*' | head -n 1 | cut -d= -f2)
    [ -n "$id" ] || id=$(tr -d '\r' < $d/companion.txt | grep -i " $CAR " | awk '{print $NF}' | tail -n 1)
    adb shell am force-stop "$PKG"
    # files/ doesn't exist yet on a fresh install: made first, or the control had no file.
    adb shell run-as $PKG mkdir -p files
    if [ $3 = without ]; then
        adb shell run-as $PKG touch files/no-arrival-screen
    else
        adb shell run-as $PKG rm -f files/no-arrival-screen
    fi
    echo "control file: $(adb shell run-as $PKG ls files/no-arrival-screen 2>&1 | tr -d '\r')" >> $d/companion.txt
    adb logcat -c
    timeout 300 "$ADB_BIN" logcat -v time -s myCar ActivityManager ActivityTaskManager > $d/log.txt 2>&1 &
    logpid=$!
    # myCar opened as on any day (it watches the cars it may connect to), then left: behind another
    # screen (home) or as the last screen when the phone was locked (top).
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 8
    if [ $1 = home ]; then adb shell input keyevent KEYCODE_HOME; sleep 2; fi
    adb shell locksettings set-pin $PIN
    adb shell input keyevent KEYCODE_SLEEP
    sleep 15
    # The lock screen up, as when the car's Bluetooth or a notification lights the phone.
    if [ $2 = keyguard ]; then adb shell input keyevent KEYCODE_WAKEUP; sleep 2; fi
    arrival_state $d/before.txt
    adb shell cmd companiondevice list 0 >> $d/companion.txt 2>&1
    if [ -n "$id" ]; then
        out=$(adb shell cmd companiondevice simulate-device-appeared "$id" 2>&1)
        echo "simulate-device-appeared: $out" >> $d/companion.txt
        if grep -qiE "unknown|error|exception|usage" <<< "$out"; then
            out=$(adb shell cmd companiondevice simulate-device-event "$id" 0 2>&1)
            echo "simulate-device-event 0: $out" >> $d/companion.txt
        fi
    fi
    sleep 3
    arrival_state $d/after.txt
    sleep 9
    adb shell dumpsys activity services "$PKG" > $d/dumpsys.txt 2>&1
    if [ -n "$id" ]; then adb shell cmd companiondevice simulate-device-disappeared "$id" || true; fi
    kill $logpid 2>/dev/null
    adb logcat -d -b crash > $d/crash.txt 2>&1
    adb shell am force-stop "$PKG"
    adb shell run-as $PKG rm -f files/no-arrival-screen
    adb shell cmd companiondevice disassociate 0 $PKG $CAR || true
    adb shell locksettings clear --old $PIN || true
    adb shell input keyevent KEYCODE_WAKEUP
    adb shell wm dismiss-keyguard || true
    sleep 2
    echo "--- location-arrival $1-$2-$3: a car arriving with myCar open behind the lock screen ---"
    grep -h "uid state\|mWakefulness\|KeyguardShowing" $d/before.txt | head -4 | sed 's/^/  before: /'
    grep -h "location keep-alive\|Background started FGS\|startForeground\|while-in-use\|eligible state" $d/log.txt | cut -c1-260 | head -12
    grep -h "uid state" $d/after.txt | sed 's/^/  after: /'
    grep -B2 -A40 "host.LocationKeepAlive c:" $d/dumpsys.txt | grep -iE "isForeground|getFgsAllowWiu_forCapabilities|infoAllowStartForeground" | cut -c1-200 | head -4
}

# A car arriving over Bluetooth with myCar's screen open from before and the phone locked
# (LocationKeepAlive.screenForArrival, 0.7.1): the car allowed through an association made by the
# shell, as Android's "Allow" makes it, and its arrival simulated by Android's companion service.
# myCar's screen was created well over 10 s before. Each case twice: "without" is the control
# (debug builds skip the arrival's screen when told to, as 0.7.0 did), "with" the fix.
case " ${ONLY:-location-arrival} " in *" location-arrival "*)
    rm -rf previews/location-arrival
    CAR=02:11:22:33:44:55
    ARRIVAL_UID=$(adb shell cmd package list packages -U $PKG | tr -d '\r' | grep -o 'uid:[0-9]*' | head -n 1 | cut -d: -f2)
    adb shell pm grant $PKG android.permission.ACCESS_FINE_LOCATION || true
    adb shell pm grant $PKG android.permission.ACCESS_COARSE_LOCATION || true
    adb shell pm grant $PKG android.permission.BLUETOOTH_CONNECT || true
    for left in top home; do
        for screen in off keyguard; do
            for run in without with; do
                arrival $left $screen $run
            done
        done
    done
    ;;
esac

# The assistant in another language on the car's screen (--es voiceLang, as with its model on the
# phone): its help in Italian, in German (the longest words), at 800x480, and an answer in German.
EXTRA="--es voiceLang it --ez voiceHelp true" shoot voice-it 1920 720 240 "$WAIT"
EXTRA="--es voiceLang de --ez voiceHelp true" shoot voice-de 1920 720 240 "$WAIT"
EXTRA="--es voiceLang it --ez voiceHelp true" shoot voice-it-800x480 800 480 160 "$WAIT"
EXTRA="--es voiceLang de --es voiceReply 'Around 14:30, in 1 hour 5 minutes.'" shoot voice-reply 1920 720 240 "$WAIT"

# The speech models' files where myCar downloads them, by size (the emulator can't run the speech
# library, which is arm64 only), an answer in each language and the phone's voices for them.
voice_check() {
    case " ${ONLY:-voice-check} " in *" voice-check "*) ;; *) return ;; esac
    local d=previews/voice-check i
    mkdir -p $d
    adb shell am force-stop "$PKG"
    adb logcat -c
    adb shell am start -n "$PKG/io.github.sirionero.mycar.VoiceCheckActivity"
    for i in $(seq 1 30); do
        sleep 4
        adb logcat -d -s myCar | grep -q "voice check: done" && break
    done
    adb logcat -d -s myCar > $d/log.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- voice check ---"
    grep -h "voice check" $d/log.txt || echo "voice-check: nothing in the log"
}
voice_check

# Lyrics: some 50 songs as the music apps write them through 0.6.8's lookup and the new one, against the
# real lrclib.net (the cloud session can't reach it). Only when named in "shots": ~250 requests to a free service.
lyrics_check() {
    case " ${ONLY:-} " in *" lyrics-check "*) ;; *) return ;; esac
    local d=previews/lyrics-check i logpid
    mkdir -p $d
    adb shell am force-stop "$PKG"
    adb logcat -c
    # Streamed: some 250 requests take minutes, and the log's buffer would lose the first songs. Not
    # through adb() above, which gives up after three minutes.
    timeout 420 "$ADB_BIN" logcat -s myCar > $d/log.txt 2>&1 &
    logpid=$!
    adb shell am start -n "$PKG/io.github.sirionero.mycar.LyricsCheckActivity"
    for i in $(seq 1 90); do
        sleep 4
        grep -q "lyrics check: done" $d/log.txt && break
    done
    kill $logpid 2>/dev/null
    adb shell am force-stop "$PKG"
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- lyrics check ---"
    grep -h "lyrics check" $d/log.txt || echo "lyrics-check: nothing in the log"
}
lyrics_check

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
# The same, back up the list.
scroll_back_to() {
    local i
    for i in 1 2 3 4 5 6 7 8; do
        has_text "$1" && return 0
        adb shell input swipe 160 260 160 380 400; sleep 1
    done
    echo "setup-phone: $1 never came back into sight"; return 1
}
# The phone's Settings: the car preview first at the top (opened, and back), then "Car screen" - the
# car's settings on the phone - with a choice's dialog, a page of Home opened out with its layouts
# and what a part can show, the rest of the group scrolled through, and the voice assistant in
# "Voice". Before setup-phone, which wipes myCar's data.
phone_settings() {
    case " ${ONLY:-phone-settings} " in *" phone-settings "*) ;; *) return ;; esac
    local d=previews/phone-settings i
    mkdir -p $d
    adb shell am force-stop "$PKG"
    adb logcat -c
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity" --es screen settings >/dev/null
    sleep 4; adb exec-out screencap -p > $d/01-top.png
    tap_text "Car preview"; sleep 5; adb exec-out screencap -p > $d/02-preview.png
    adb shell input keyevent 4; sleep 2; adb exec-out screencap -p > $d/03-back.png
    tap_text "Car screen"; sleep 3; adb exec-out screencap -p > $d/04-car.png
    tap_text "Open on connect"; sleep 1.5; adb exec-out screencap -p > $d/05-open-on-connect.png
    tap_text "Close"; sleep 1
    # The player beside the map: Small, Medium, Large.
    scroll_to "Size of the player beside the map" && tap_text "Size of the player beside the map"
    sleep 1.5; adb exec-out screencap -p > $d/05-player-size.png
    tap_text "Close"; sleep 1
    scroll_to "Page 1" && tap_text "Page 1"; sleep 1.5
    # The page opened out below the fold: brought up, so its parts can be tapped.
    adb shell input swipe 160 520 160 200 400; sleep 1.5; adb exec-out screencap -p > $d/06-page.png
    tap_text "Layout"; sleep 1.5; adb exec-out screencap -p > $d/07-layouts.png
    tap_text "Close"; sleep 1
    tap_text "Weather"; sleep 1.5; adb exec-out screencap -p > $d/08-part.png
    tap_text "Close"; sleep 1
    # The apps on the car's grid, each with a tick (none hidden), under the pages.
    for i in 1 2 3 4 5 6 7 8; do
        tap_match 'text="Apps on the car' && break
        adb shell input swipe 160 380 160 260 400; sleep 1
    done
    sleep 1.5; adb exec-out screencap -p > $d/08-grid-apps.png
    adb shell input swipe 160 520 160 220 400; sleep 1.5; adb exec-out screencap -p > $d/08-grid-apps-2.png
    tap_text "Done"; sleep 1
    for i in 1 2 3 4 5 6 7; do
        adb shell input swipe 160 520 160 220 400; sleep 1.5; adb exec-out screencap -p > $d/09-car-$i.png
    done
    adb shell input keyevent 4; sleep 2
    tap_text "Voice"; sleep 3; adb exec-out screencap -p > $d/10-voice.png
    adb shell input swipe 160 520 160 220 400; sleep 1.5; adb exec-out screencap -p > $d/11-voice.png
    adb logcat -d -s myCar > $d/log.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- phone settings ---"
    grep -h "FATAL\|AndroidRuntime" $d/crash.txt || echo "phone-settings: no crash"
}
phone_settings

# The Jolly key on the phone (ui/JollySettings.kt), in Settings > Car screen: its section, the kinds, a
# ready-made command picked and saved, a place written with the phone's keyboard and saved, a contact's
# number. Here, after the helpers above it and before setup-phone, which wipes myCar's data.
phone_jolly() {
    case " ${ONLY:-jolly-phone} " in *" jolly-phone "*) ;; *) return ;; esac
    local d=previews/jolly-phone
    mkdir -p $d
    adb shell am force-stop "$PKG"
    adb logcat -c
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity" --es screen settings >/dev/null
    sleep 4
    tap_text "Car screen"; sleep 3
    scroll_to "What it does"; sleep 1; adb exec-out screencap -p > $d/01-section.png
    tap_text "What it does"; sleep 1.5; adb exec-out screencap -p > $d/02-kinds.png
    tap_text "Assistant command"; sleep 1.5; adb exec-out screencap -p > $d/03-command.png
    tap_text "Read my messages"; sleep 1; adb exec-out screencap -p > $d/04-command-picked.png
    tap_text "Save"; sleep 1.5; adb exec-out screencap -p > $d/05-saved.png
    tap_text "What it does"; sleep 1.5
    tap_text "Go to a place"; sleep 1.5; adb exec-out screencap -p > $d/06-place.png
    tap_match 'class="android.widget.EditText"'; sleep 1
    adb shell input text "Piazza%sdel%sDuomo%s1"; sleep 1; adb exec-out screencap -p > $d/07-place-written.png
    tap_text "Save"; sleep 1.5; adb exec-out screencap -p > $d/08-place-saved.png
    # The contacts (those jolly-contacts wrote, when it ran before), one with two numbers.
    tap_text "What it does"; sleep 1.5
    tap_text "Call a contact"; sleep 2; adb exec-out screencap -p > $d/09-contacts.png
    tap_text "Anna Verdi"; sleep 1.5; adb exec-out screencap -p > $d/10-numbers.png
    tap_text "Mobile"; sleep 1.5; adb exec-out screencap -p > $d/11-call-saved.png
    adb logcat -d -s myCar > $d/log.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- the Jolly key on the phone ---"
    grep -h "FATAL\|AndroidRuntime" $d/crash.txt || echo "jolly-phone: no crash"
}
phone_jolly

# Music on the phone (ui/MusicSettings.kt): Xonos's switch and settings, Spotify turned on (its
# sign-in on the phone, the page in myCar's own web engine), "Add a service" with Deezer ticked.
tap_last_text() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    local b
    b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -F "text=\"$1\"" | tail -1 |
        sed -nE 's/.*bounds="\[([0-9]+),([0-9]+)\]\[([0-9]+),([0-9]+)\]".*/\1 \2 \3 \4/p')
    if [ -z "$b" ]; then echo "phone-music: nothing like $1 on screen"; return 1; fi
    set -- $b
    adb shell input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
# A sign-in page in myCar's web engine, checked from inside (a debug build lets adb into its pages):
# its first field laid out and the thing a touch at its centre reaches. signin_probe <out> <site>
signin_probe() {
    local pid
    pid=$(adb shell pidof "$PKG" | tr -d '\r' | awk '{print $1}')
    [ -n "$pid" ] || { echo "no myCar process"; return; }
    adb forward tcp:9333 "localabstract:webview_devtools_remote_$pid" >/dev/null
    python3 -m pip install --quiet --user websocket-client >/dev/null 2>&1
    python3 .github/signin-probe.py http://127.0.0.1:9333 "$1" "$2" 2>&1
    adb forward --remove tcp:9333 >/dev/null 2>&1
}
phone_music() {
    case " ${ONLY:-phone-music} " in *" phone-music "*) ;; *) return ;; esac
    local d=previews/phone-music
    mkdir -p $d
    adb shell am force-stop "$PKG"
    adb logcat -c
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity" --es screen settings >/dev/null
    sleep 4
    tap_text "Music"; sleep 3; adb exec-out screencap -p > $d/01-music.png
    # Spotify's switch, under Xonos's settings: its line says it's off (an apostrophe in it, which the
    # screen's dump may write otherwise, so it's found by its start).
    local i
    for i in 1 2 3 4 5 6 7 8; do
        adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
        adb shell cat /sdcard/ui.xml | grep -qF "Off: not on the car" && break
        adb shell input swipe 160 380 160 260 400; sleep 1
    done
    tap_match "Off: not on the car"; sleep 2
    adb exec-out screencap -p > $d/02-spotify-on.png
    scroll_to "Sign in on the phone" && tap_text "Sign in on the phone"; sleep 12
    adb exec-out screencap -p > $d/03-sign-in.png
    # The service's sign-in page can be slow on the emulator: again later, with what the page shows
    # (a web page's text is in the screen's tree too).
    sleep 20; adb exec-out screencap -p > $d/03b-sign-in-later.png
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; adb shell cat /sdcard/ui.xml > $d/03-sign-in-ui.xml
    signin_probe "$d/03-cdp" spotify > $d/03-sign-in-probe.txt
    adb shell input keyevent 4; sleep 2
    adb exec-out screencap -p > $d/04-back.png
    scroll_to "Add a service" && tap_text "Add a service"; sleep 2; adb exec-out screencap -p > $d/05-add.png
    tap_text "Deezer"; sleep 1; adb exec-out screencap -p > $d/06-deezer.png
    tap_text "Done"; sleep 2
    for i in 1 2 3 4; do
        adb shell input swipe 160 520 160 220 400; sleep 1.5; adb exec-out screencap -p > $d/07-music-$i.png
    done
    # Deezer's sign-in too, the last "Sign in on the phone" of the list, checked the same way.
    tap_last_text "Sign in on the phone"; sleep 15
    adb exec-out screencap -p > $d/08-deezer-sign-in.png
    signin_probe "$d/08-cdp" deezer > $d/08-sign-in-probe.txt
    adb shell input keyevent 4; sleep 2
    adb logcat -d -s myCar > $d/log.txt
    # The pages' own messages (errors and warnings in their scripts) go to the log as "chromium".
    adb logcat -d -s chromium > $d/chromium.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- music on the phone ---"
    grep -h "music:" $d/log.txt | head -20
    grep -h "sign-in field" $d/03-sign-in-probe.txt $d/08-sign-in-probe.txt 2>/dev/null
    grep -h "FATAL\|AndroidRuntime" $d/crash.txt || echo "phone-music: no crash"
}
phone_music

# Home's card when the phone charges from a USB port and no car comes (ui/ChargingNoCarCard.kt):
# the emulator's battery set to charge from USB, no card at first, the card after the 20 s wait
# (scrolled into sight), gone once "unplugged". --ez graphene true (debug only) adds GrapheneOS's
# USB-C lines, here and in How to connect, whose step is shot too. Before setup-phone, which wipes
# myCar's data.
charging_no_car() {
    case " ${ONLY:-charging-no-car} " in *" charging-no-car "*) ;; *) return ;; esac
    local d=previews/charging-no-car
    mkdir -p $d
    adb shell am force-stop "$PKG"
    adb logcat -c
    adb shell dumpsys battery unplug
    adb shell dumpsys battery set usb 1
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity" --es screen home --ez graphene true >/dev/null
    sleep 5; adb exec-out screencap -p > $d/01-plugged.png
    sleep 20; adb exec-out screencap -p > $d/02-after-20s.png
    # Home scrolls only above its bottom card (What's new, How to connect): the swipe starts on the
    # status card, inside the part that scrolls.
    adb shell input swipe 160 420 160 120 400; sleep 1.5; adb exec-out screencap -p > $d/03-card.png
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml | grep -qF "The phone is charging" && echo "charging-no-car: card shown" || echo "charging-no-car: no card"
    adb shell dumpsys battery set usb 0
    sleep 3; adb exec-out screencap -p > $d/04-unplugged.png
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb shell cat /sdcard/ui.xml | grep -qF "The phone is charging" && echo "charging-no-car: card still there unplugged" || echo "charging-no-car: gone unplugged"
    adb shell dumpsys battery reset
    adb shell am force-stop "$PKG"
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity" --es screen help --ez graphene true >/dev/null
    sleep 4
    scroll_to "On GrapheneOS, let the USB-C port carry data." && adb exec-out screencap -p > $d/05-help.png
    adb shell input swipe 160 520 160 220 400; sleep 1.5; adb exec-out screencap -p > $d/06-help.png
    adb logcat -d -s myCar > $d/log.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- charging, no car ---"
    grep -h "home: charging" $d/log.txt || echo "charging-no-car: nothing in the log"
    grep -h "FATAL\|AndroidRuntime" $d/crash.txt || echo "charging-no-car: no crash"
}
charging_no_car

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
    # Car audio, with the drive's steps: each way with what it means at every drive, and through
    # myCar the command that stops Android's question. Then Automatic again, the default.
    if scroll_to "Car audio"; then
        adb exec-out screencap -p > $d/04b-audio.png
        scroll_to "Through myCar" && tap_text "Through myCar"; sleep 1.5
        adb exec-out screencap -p > $d/04c-audio-mycar.png
        scroll_to "Tap to copy" && adb exec-out screencap -p > $d/04d-audio-command.png
        scroll_back_to "Automatic" && tap_text "Automatic"; sleep 1
    fi
    tap_text "Done"; sleep 3; adb exec-out screencap -p > $d/05-home.png
    tap_text "How to connect"; sleep 3; adb exec-out screencap -p > $d/06-help.png
    tap_text "Take me back to the initial setup"; sleep 3; adb exec-out screencap -p > $d/07-return.png
    # Taking a permission away stops the app: open it again, and go back to the list.
    adb shell pm revoke "$PKG" android.permission.READ_CONTACTS
    sleep 2
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 5; tap_text "How to connect"; sleep 3
    tap_text "Take me back to the initial setup"; sleep 3; adb exec-out screencap -p > $d/08-partial.png
    # The intro, as after a big release: its first page, the pirate ship sailing in, then running
    # its flag up the main mast. The emulator runs with animations off (the runner's default),
    # which stops the sea and the wind: on for these shots only.
    adb shell am force-stop "$PKG"
    adb shell run-as "$PKG" rm -f shared_prefs/intro.xml
    adb shell settings put global animator_duration_scale 1
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 1.2; adb exec-out screencap -p > $d/09-intro-1.png
    sleep 1.2; adb exec-out screencap -p > $d/09-intro-2.png
    sleep 1.2; adb exec-out screencap -p > $d/09-intro-3.png
    sleep 2; adb exec-out screencap -p > $d/09-intro-4.png
    # Then the intro's other pages, Next by Next (its button, or the same spot when the screen can't
    # be read): 09-intro-page-2 is the second page, up to the voice page (7) before Start.
    for i in 2 3 4 5 6 7; do
        tap_text "Next" || adb shell input tap $((W * 72 / 100)) $((H - 80))
        sleep 2.5; adb exec-out screencap -p > $d/09-intro-page-$i.png
    done
    # After a smaller update: the intro of this release seen, an older version noted, so the flag
    # runs up on its own page (UpdateSplash) before Home.
    adb shell am force-stop "$PKG"
    release=$(sed -nE 's/.*versionName = .*"([0-9]+\.[0-9]+)\.[0-9]+[^"]*".*/\1/p' app/build.gradle.kts | head -1)
    adb shell "run-as $PKG sh -c 'printf \"<map><string name=\\\"seen_release\\\">$release</string></map>\" > shared_prefs/intro.xml'"
    adb shell "run-as $PKG sh -c 'printf \"<map><int name=\\\"version_code\\\" value=\\\"1\\\" /></map>\" > shared_prefs/update_flag.xml'"
    adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity"
    sleep 1; adb exec-out screencap -p > $d/10-update-1.png
    sleep 1.5; adb exec-out screencap -p > $d/10-update-2.png
    sleep 2; adb exec-out screencap -p > $d/10-update-3.png
    # A tap goes on to Home.
    adb shell input tap 160 320; sleep 2; adb exec-out screencap -p > $d/10-update-4.png
    adb shell settings put global animator_duration_scale 0
    adb logcat -d | grep -E "myCar|CERT|DOWNL" > $d/log.txt
    adb logcat -d -b crash > $d/crash.txt 2>&1
    echo "--- setup on the phone ---"
    grep -hE "CERT|DOWNL" $d/log.txt | tail -20 || echo "setup-phone: nothing in the log"
}
setup_phone

# myCar in its other languages (Settings > Language; from Android 13 the same as Android's own
# language setting for the app, which `cmd locale` sets here): Home with music, the app grid and
# the car's settings at 1920x720; Home, grid and dashboard at 800x480, where long words have the
# least room. After the shots that tap English words, and English again at the end.
# On the phone too: the setup list, Home, Settings, Connection help and the intro, each scrolled
# twice (the debug build opens the screen named by --es screen).
phone_lang() {
    local d=previews/lang-$1/phone s
    mkdir -p "$d"
    for s in setup home settings help intro; do
        adb shell am force-stop "$PKG"
        # The intro from its first page: the language before left it on a later one.
        [ "$s" = intro ] && adb shell run-as "$PKG" rm -f shared_prefs/intro.xml
        adb shell am start -n "$PKG/io.github.sirionero.mycar.MyCarActivity" --es screen "$s" >/dev/null
        sleep 4; adb exec-out screencap -p > "$d/$s-1.png"
        # From above the fixed bar at the foot (setup's big button): started on it, the list didn't move.
        adb shell input swipe 160 430 160 110 400; sleep 1.5; adb exec-out screencap -p > "$d/$s-2.png"
        adb shell input swipe 160 430 160 110 400; sleep 1.5; adb exec-out screencap -p > "$d/$s-3.png"
    done
    # Each page of the intro in this language, where long words have the least room: Next is at the
    # same spot in every language (its text isn't English here).
    for i in 2 3 4 5 6 7; do
        # On the button (570-621 at 640 high): at H-80 the tap fell between the dots and it.
        adb shell input tap $((W * 72 / 100)) $((H - 45))
        sleep 1.5; adb exec-out screencap -p > "$d/intro-page-$i.png"
    done
}
# it-XP, en-XP: pirate speak (AppLanguage.PIRATE), Italian and English. en-GB: English that isn't en-US
# must stay plain (values-en, copied at build time): without it Android picked the pirate folder.
LANGS="it es de fr pt el ru zh-CN it-XP en-XP en-GB"
for lang in $LANGS; do
    BEFORE="adb shell cmd locale set-app-locales $PKG --locales $lang" EXTRA="--ez demoSong true" \
        shoot "lang-$lang" 1920 720 240 "$WAIT;$GRID_BUTTON;$(at settings);$WAIT"
    BEFORE="adb shell cmd locale set-app-locales $PKG --locales $lang" \
        shoot "lang-$lang-800x480" 800 480 160 "48,436;48,44"
    case " ${ONLY:-lang-$lang} " in *" lang-$lang "*) phone_lang "$lang" ;; esac
done
adb shell cmd locale set-app-locales "$PKG" --locales en-US

# The light theme isn't reachable from here; the dark default is what ships.
ls -lR previews
