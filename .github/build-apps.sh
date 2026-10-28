#!/usr/bin/env bash
# The Plancia and Xonos apps built from a branch of theirs (PLANCIA_REF, XONOS_REF, either may be
# empty) in place of the releases the workflow downloaded: for what myCar does with them that no
# release has yet - the bridge, for one. Their debug builds, which take myCar's debug build too,
# into third-party/. Neither app has a Gradle wrapper: Gradle 8.14.3, which builds both, is
# fetched here. With XONOS_SERVER, also Music Assistant for the phone: the package Xonos downloads
# when asked ("Make Xonos your Music Assistant server"), for the emulator's x86_64 only (its FFmpeg
# and librespot are compiled with the runner's NDK and Rust), into third-party/xonos-server/. Xonos
# uses it only if it's signed like Xonos: a throwaway key signs both, as the release key does.
set -eu
GRADLE_VERSION=8.14.3
mkdir -p third-party apps-src
if [ -n "${PLANCIA_REF:-}${XONOS_REF:-}" ]; then
    curl -fsSL --retry 3 -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    unzip -q -o /tmp/gradle.zip -d /tmp
fi
GRADLE=/tmp/gradle-$GRADLE_VERSION/bin/gradle

fetch() {
    local name=$1 repo=$2 ref=$3
    rm -rf "apps-src/$name"
    git clone -q --depth 1 --branch "$ref" "https://github.com/$repo.git" "apps-src/$name"
}

assemble() {
    local name=$1
    shift
    (cd "apps-src/$name" && "$GRADLE" :app:assembleDebug --no-daemon -q "$@")
    cp "apps-src/$name/app/build/outputs/apk/debug/app-debug.apk" "third-party/$name.apk"
    echo "$name: $(git -C "apps-src/$name" remote get-url origin) at $(git -C "apps-src/$name" rev-parse --short HEAD), debug build"
}

if [ -n "${PLANCIA_REF:-}" ]; then
    fetch plancia SirioNero/Home-Assistant-on-Gearslip "$PLANCIA_REF"
    assemble plancia
fi

if [ -n "${XONOS_REF:-}" ]; then
    fetch xonos SirioNero/Music-Assistant-on-Gearslip "$XONOS_REF"
    if [ -n "${XONOS_SERVER:-}" ]; then
        export ANDROID_NDK="${ANDROID_NDK_LATEST_HOME:-${ANDROID_NDK_HOME:-}}"
        export XONOS_KEYSTORE_PASSWORD=xonos-android XONOS_KEY_ALIAS=xonos
        keytool -genkeypair -noprompt -keystore apps-src/xonos/app/xonos.keystore -storepass "$XONOS_KEYSTORE_PASSWORD" \
            -keypass "$XONOS_KEYSTORE_PASSWORD" -alias "$XONOS_KEY_ALIAS" -keyalg RSA -keysize 2048 -validity 30 \
            -dname "CN=Xonos (myCar preview)"
        assemble xonos :server:assembleRelease -PxonosServerAbis=x86_64
        # The package, made and signed the way Xonos's release workflow does it.
        mkdir -p third-party/xonos-server
        zip=third-party/xonos-server/xonos-server-x86_64.zip
        python3 apps-src/xonos/server/package/make_payload.py \
            --aar apps-src/xonos/server/build/outputs/aar/server-release.aar --abi x86_64 --ndk "$ANDROID_NDK" \
            --version "$(sed -n 's/^xonosServerPackage=//p' apps-src/xonos/gradle.properties)" --out "$zip"
        keytool -importkeystore -noprompt -srckeystore apps-src/xonos/app/xonos.keystore \
            -srcstorepass "$XONOS_KEYSTORE_PASSWORD" -srcalias "$XONOS_KEY_ALIAS" -destkeystore /tmp/xonos.p12 \
            -deststoretype PKCS12 -deststorepass "$XONOS_KEYSTORE_PASSWORD" -destkeypass "$XONOS_KEYSTORE_PASSWORD"
        openssl pkcs12 -in /tmp/xonos.p12 -passin env:XONOS_KEYSTORE_PASSWORD -nocerts -nodes -out /tmp/xonos.pem
        openssl dgst -sha256 -sign /tmp/xonos.pem -out "$zip.sig" "$zip"
        rm -f /tmp/xonos.p12 /tmp/xonos.pem
        ls -l third-party/xonos-server
    else
        assemble xonos
    fi
fi
# The builds' daemons (Kotlin's stays on after --no-daemon) hold gigabytes the emulator, Home
# Assistant and Music Assistant need next: twice the emulator went away with Xonos playing.
"$GRADLE" --stop >/dev/null 2>&1 || true
pkill -f KotlinCompileDaemon || true
pkill -f GradleDaemon || true
free -m || true
ls -l third-party
