#!/usr/bin/env bash
# Run by .github/workflows/release-check.yml on the emulator: a published myCar release, set up as
# a driver's phone would be - the guide done, the Plancia and Xonos apps signed in to the run's own
# Home Assistant and Music Assistant - then the Plancia app's own "Save and test" (the address works
# outside myCar) and myCar's settings, where Plancia's "Save and test" and Xonos's "Use the Xonos
# app's account" try to connect, as on the phone. NET=lan: the servers at 10.0.2.2, as on the home
# Wi-Fi. NET=tailscale: the Tailscale app on the phone, signed in to the run's headscale, and the
# servers at http://umbrel:<port> through MagicDNS (.github/tailscale.sh). NET=tailscale-out: the
# same, with myCar left out of Tailscale - what drivers do for the wireless link.
# Everything it sees goes to checks/: screens, UI dumps, the texts on screen, logs, what myCar kept.
set -u
PKG=io.github.sirionero.mycar
PLANCIA=it.plancia.ha
XONOS=it.xonos.ma
TAILSCALE=com.tailscale.ipn
NET=${NET:-lan}
OUT=checks
mkdir -p "$OUT"
SDK=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
echo "Android SDK $SDK ($(adb shell getprop ro.build.version.release | tr -d '\r')), home through $NET" | tee "$OUT/android.txt"
if [ "$NET" = lan ]; then HOST=10.0.2.2; else HOST=umbrel; fi
HA_URL=http://$HOST:8123
MA_URL=http://$HOST:8095

# Root, to write the apps' settings as if they had been typed in.
adb root >/dev/null 2>&1; sleep 3; adb wait-for-device
echo "adb runs as $(adb shell whoami | tr -d '\r')"

# A phone-sized screen, so results aren't pushed off the bottom.
adb shell wm size 1080x2400; adb shell wm density 420; sleep 2
size=$(adb shell wm size | tr -d '\r' | sed -n 's/.*: \([0-9]*x[0-9]*\)$/\1/p' | tail -1)
W=${size%x*}; H=${size#*x}

ui() {
    adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
    adb pull /sdcard/ui.xml "$OUT/ui.xml" >/dev/null 2>&1
}
# The centre of the first node whose text or description is $1, in capitals or not (the Plancia
# app's buttons are all capitals).
centre() {
    python3 - "$OUT/ui.xml" "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
try:
    nodes = list(ET.parse(sys.argv[1]).iter('node'))
except Exception:
    sys.exit(0)
want = sys.argv[2].lower()
for n in nodes:
    if want in ((n.get('text') or '').lower(), (n.get('content-desc') or '').lower()):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
}
# Every text on screen, one a line.
texts() {
    python3 - "$OUT/ui.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
try:
    nodes = list(ET.parse(sys.argv[1]).iter('node'))
except Exception:
    sys.exit(0)
for n in nodes:
    for k in ('text', 'content-desc'):
        v = n.get(k)
        if v: print(v)
PY
}
shot() { adb exec-out screencap -p > "$OUT/$1.png"; ui; cp "$OUT/ui.xml" "$OUT/$1.xml"; texts > "$OUT/$1.txt"; }
# Taps $1, scrolling down for it if it isn't on screen yet ($2: how many tries, 14 by default).
tap() {
    local c i
    for i in $(seq 1 "${2:-14}"); do
        ui
        c=$(centre "$1")
        if [ -n "$c" ]; then adb shell input tap $c; return 0; fi
        adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500
        sleep 1
    done
    echo "not found on screen: $1"
    return 1
}
# The centre of $1, scrolling down for it ($2: how many tries); empty if it never shows.
find_scrolling() {
    local c i
    for i in $(seq 1 "${2:-14}"); do
        ui
        c=$(centre "$1")
        if [ -n "$c" ]; then echo "$c"; return 0; fi
        adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500
        sleep 1
    done
    return 1
}
# Taps $1 if it is on screen now, without scrolling.
tap_if() { ui; local c; c=$(centre "$1"); [ -n "$c" ] && adb shell input tap $c; }
type_text() { adb shell input text "$1"; sleep 1; }

for apk in mycar plancia xonos; do adb install -r "third-party/$apk.apk" || echo "could not install $apk"; done

# --- Tailscale on the phone, signed in to the run's headscale ---------------------------------
in_tailnet() { adb shell ip addr 2>/dev/null | grep -q "inet 100\."; }
if [ "$NET" != lan ]; then
    adb install -r third-party/tailscale.apk || echo "could not install Tailscale"
    adb shell appops set $TAILSCALE ACTIVATE_VPN allow
    adb shell pm grant $TAILSCALE android.permission.POST_NOTIFICATIONS 2>/dev/null
    adb logcat -c
    adb shell monkey -p $TAILSCALE -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
    sleep 8
    shot ts-1-start
    tap_if "Get Started"; sleep 4
    tap "Open settings" 3; sleep 3
    tap "Accounts" 3; sleep 3
    shot ts-2-accounts
    tap "menu" 2; sleep 2
    tap "Use an alternate server" 2; sleep 3
    ui
    field=$(python3 - "$OUT/ui.xml" <<'PY'
import re, sys, xml.etree.ElementTree as ET
for n in ET.parse(sys.argv[1]).iter('node'):
    if n.get('class') == 'android.widget.EditText':
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', n.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
    [ -n "$field" ] && adb shell input tap $field && sleep 1
    type_text "http://10.0.2.2:8080"
    shot ts-3-server
    tap "Add account" 2; sleep 8
    shot ts-4-browser
    # Headscale's sign-in page opens in the browser: the phone is let in from the runner instead,
    # by the id of its request.
    reg=$(grep -o "auth id: [A-Za-z0-9_-]*" "$HEADSCALE_LOG" | tail -1 | sed 's/auth id: //')
    [ -z "$reg" ] && reg=$(adb logcat -d | grep -oE "/register/[A-Za-z0-9_-]+" | tail -1 | sed 's,/register/,,')
    echo "letting the phone into the tailnet (request $reg)"
    $HEADSCALE auth register --auth-id "$reg" --user driver ||
        $HEADSCALE auth register --auth-id "$reg" --user "$TS_USER" ||
        $HEADSCALE nodes register --user driver --key "$reg"
    sleep 8
    adb shell am start -n $TAILSCALE/.MainActivity >/dev/null 2>&1; sleep 8
    in_tailnet || { tap_if "Connect"; sleep 8; }
    in_tailnet || { tap_if "Connect to VPN"; sleep 8; }
    shot ts-5-connected
    echo "--- the phone in the tailnet ---" | tee "$OUT/tailnet.txt"
    adb shell ip addr 2>/dev/null | grep -E "inet6? (100\.|fd7a)" | tee -a "$OUT/tailnet.txt"
    $HEADSCALE nodes list 2>&1 | sed 's/\x1b\[[0-9;]*m//g' | tee -a "$OUT/tailnet.txt"
    adb shell dumpsys connectivity 2>/dev/null | grep -E "VPN CONNECTED|InterfaceName: tun" | head -10 > "$OUT/connectivity.txt"
    echo "--- umbrel from the phone's shell ---" | tee -a "$OUT/tailnet.txt"
    adb shell ping -c 2 -W 3 umbrel 2>&1 | tee -a "$OUT/tailnet.txt"
    adb logcat -d | grep -iE "tailscale|libtailscale|ipn|wgengine|magicsock" | tail -300 > "$OUT/tailscale-log.txt"
    in_tailnet && echo "the phone is in the tailnet" || echo "THE PHONE IS NOT IN THE TAILNET: the rest says nothing about Tailscale"

    # tailscale-out: myCar left out of Tailscale, as drivers do for the wireless link (Tailscale ->
    # Settings -> App split tunneling, myCar ticked among the excluded apps).
    if [ "$NET" = tailscale-out ]; then
        adb shell am start -n $TAILSCALE/.MainActivity >/dev/null 2>&1; sleep 4
        # Signed in, the way to the settings is the account's picture, "Settings".
        tap "Settings" 3 || tap "Open settings" 2; sleep 3
        tap "App split tunneling" 8; sleep 5
        shot ts-7-split
        row=$(find_scrolling "myCar" 30)
        if [ -n "$row" ]; then
            # The tick box is at the end of the row.
            adb shell input tap $((W - 90)) ${row#* }
            sleep 6
            shot ts-8-mycar-out
        else
            echo "myCar not found among Tailscale's apps"
        fi
        adb shell input keyevent KEYCODE_BACK; sleep 2; adb shell input keyevent KEYCODE_BACK; sleep 6
    fi
    # Whether the VPN carries myCar's user: the shell's own user is one Android lets through everything.
    uid=$(adb shell pm list packages -U $PKG | tr -d '\r' | sed -n 's/.*uid://p' | head -1)
    vpn=$(adb shell dumpsys connectivity 2>/dev/null | grep -m1 "NetworkAgentInfo.*Transports: VPN")
    echo "--- myCar (user $uid) and the VPN ---" | tee -a "$OUT/tailnet.txt"
    python3 - "$uid" "$vpn" <<'PY' | tee -a "$OUT/tailnet.txt"
import re, sys
uid, line = int(sys.argv[1] or 0), sys.argv[2]
m = re.search(r"Uids: <\{([^}]*)\}>", line)
if not m:
    print("no VPN carrying apps")
else:
    inside = any(int(a) <= uid <= int(b) for a, b in re.findall(r"(\d+)-(\d+)", m.group(1)))
    print(f"the VPN carries users {m.group(1)}: myCar is {'INSIDE' if inside else 'LEFT OUT'}")
print(" ".join(re.findall(r"bypassable=\w+", line)))
PY
fi

# What a driver allowed through myCar's guide before 0.5.12 - not the home network, which only
# 0.5.12 asks for.
for p in RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_CALENDAR READ_PHONE_STATE \
    READ_CALL_LOG READ_CONTACTS ANSWER_PHONE_CALLS CALL_PHONE BLUETOOTH_CONNECT POST_NOTIFICATIONS; do
    adb shell pm grant $PKG android.permission.$p 2>/dev/null || echo "no $p"
done

# Each app started once, so its data folder exists, then stopped.
for p in $PKG $PLANCIA $XONOS; do adb shell monkey -p $p -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; done
sleep 10
for p in $PKG $PLANCIA $XONOS; do adb shell am force-stop $p; done

# prefs <package> <file> <entries>: a SharedPreferences file, owned by the app as if it wrote it.
prefs() {
    local pkg=$1 file=$2 body=$3 owner
    owner=$(adb shell stat -c %u:%g /data/data/$pkg | tr -d '\r')
    printf "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n<map>\n%s\n</map>\n" "$body" > "/tmp/$file"
    adb shell mkdir -p /data/data/$pkg/shared_prefs
    adb push "/tmp/$file" "/data/data/$pkg/shared_prefs/$file" >/dev/null
    adb shell chown "$owner" /data/data/$pkg/shared_prefs "/data/data/$pkg/shared_prefs/$file"
    adb shell chmod 660 "/data/data/$pkg/shared_prefs/$file"
    adb shell restorecon -R /data/data/$pkg/shared_prefs >/dev/null 2>&1
}
prefs $PLANCIA plancia.xml "    <string name=\"url_home\">$HA_URL</string>
    <string name=\"token\">$HA_TOKEN</string>"
prefs $XONOS xonos.xml "    <string name=\"url_home\">$MA_URL</string>
    <string name=\"token\">$MA_TOKEN</string>
    <string name=\"username\">mycar</string>"
prefs $PKG mycar_settings.xml "    <boolean name=\"skipped_cert_setup\" value=\"true\" />
    <boolean name=\"seen_permissions_setup\" value=\"true\" />"
RELEASE=$(adb shell dumpsys package $PKG | sed -n 's/.*versionName=\([0-9]*\.[0-9]*\).*/\1/p' | head -1 | tr -d '\r')
prefs $PKG intro.xml "    <string name=\"seen_release\">$RELEASE</string>"

# --- The Plancia app's own test: the address works outside myCar ------------------------------
adb logcat -c
adb shell monkey -p $PLANCIA -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1
sleep 6
tap "Save and test the connection"
sleep 20
for i in 1 2 3 4; do
    shot 0-plancia-app
    grep -qE "^(✓|✗)" "$OUT/0-plancia-app.txt" && break
    adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500
    sleep 2
done
echo "--- the Plancia app, Save and test ---"
grep -E "^(✓|✗)" "$OUT/0-plancia-app.txt" || echo "(no result line on screen)"
adb shell am force-stop $PLANCIA

# --- myCar ------------------------------------------------------------------------------------
adb shell am start -n $PKG/$PKG.MyCarActivity
sleep 12
shot 1-home
# A permission question from myCar's start (the home network, from 0.5.12): shot, then allowed.
if grep -qiE "^Allow|nearby|local network" "$OUT/1-home.txt"; then
    shot 1b-question
    tap "Allow" || tap "Allow only while using the app" || true
    sleep 3
fi
echo "--- permissions ---" | tee "$OUT/permissions.txt"
adb shell dumpsys package $PKG | grep -iE "LOCAL_NETWORK|NEARBY_WIFI|BLUETOOTH_CONNECT" | tee -a "$OUT/permissions.txt"

tap "Settings"
sleep 3
shot 2-settings
tap "Save and test"
sleep 20
# The result is written below the button: scrolled to.
for i in 1 2 3 4; do
    shot 3-plancia-test
    grep -qE "^(At home|Away): " "$OUT/3-plancia-test.txt" && break
    adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 2)) 500
    sleep 2
done
echo "--- Plancia in myCar, Save and test ---"
grep -E "^(At home|Away|Trying)" "$OUT/3-plancia-test.txt" || echo "(no result line on screen)"

tap "Use the Xonos app's account"
sleep 20
# The result is written at the bottom of the Xonos section, below the sign-in: scrolled to.
for i in 1 2 3 4 5; do
    shot 4-xonos-test
    grep -E "^(At home|Away): " "$OUT/4-xonos-test.txt" | grep -qv "Home Assistant" && break
    adb shell input swipe $((W / 2)) $((H * 3 / 4)) $((W / 2)) $((H / 3)) 500
    sleep 2
done
echo "--- Xonos in myCar, the app's account ---"
grep -E "^(At home|Away|Trying|Asking|Xonos)" "$OUT/4-xonos-test.txt" | grep -v "Home Assistant" || echo "(no result line on screen)"

echo "--- what myCar kept (tokens hidden) ---"
for f in mycar_settings.xml xonos.xml; do
    adb shell cat /data/data/$PKG/shared_prefs/$f 2>/dev/null |
        sed -E 's/(name="[a-z_]*token">)[^<]*/\1(hidden)/' | tee "$OUT/kept-$f"
done
adb logcat -d -s myCar Xonos Plancia > "$OUT/log.txt" 2>&1
adb logcat -d | grep -iE "mycar|xonos|plancia|okhttp|cleartext|networksecurity|localnetwork|AndroidRuntime|FATAL|UnknownHost|resolv" | tail -400 > "$OUT/logcat.txt"
adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb pull /sdcard/Android/data/$PKG/files/. "$OUT/files/" >/dev/null 2>&1 || true
echo "--- myCar's log ---"
grep -iE "plancia|xonos|local network|home assistant|music assistant" "$OUT/log.txt" | head -60
ls -l "$OUT"
