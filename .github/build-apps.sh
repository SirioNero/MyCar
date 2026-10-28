#!/usr/bin/env bash
# The Plancia and Xonos apps built from a branch of theirs (PLANCIA_REF, XONOS_REF, either may be
# empty) in place of the releases the workflow downloaded: for what myCar does with them that no
# release has yet - the bridge, for one. Their debug builds, which take myCar's debug build too,
# into third-party/. Neither app has a Gradle wrapper: Gradle 8.14.3, which builds both, is
# fetched here.
set -eu
GRADLE_VERSION=8.14.3
mkdir -p third-party apps-src
if [ -n "${PLANCIA_REF:-}${XONOS_REF:-}" ]; then
    curl -fsSL --retry 3 -o /tmp/gradle.zip "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
    unzip -q -o /tmp/gradle.zip -d /tmp
fi
GRADLE=/tmp/gradle-$GRADLE_VERSION/bin/gradle

build() {
    local name=$1 repo=$2 ref=$3
    [ -n "$ref" ] || return 0
    rm -rf "apps-src/$name"
    git clone -q --depth 1 --branch "$ref" "https://github.com/$repo.git" "apps-src/$name"
    (cd "apps-src/$name" && "$GRADLE" :app:assembleDebug --no-daemon -q)
    cp "apps-src/$name/app/build/outputs/apk/debug/app-debug.apk" "third-party/$name.apk"
    echo "$name: $repo at $ref ($(git -C "apps-src/$name" rev-parse --short HEAD)), debug build"
}
build plancia SirioNero/Home-Assistant-on-Gearslip "${PLANCIA_REF:-}"
build xonos SirioNero/Music-Assistant-on-Gearslip "${XONOS_REF:-}"
# The builds' daemons (Kotlin's stays on after --no-daemon) hold gigabytes the emulator, Home
# Assistant and Music Assistant need next: twice the emulator went away with Xonos playing.
"$GRADLE" --stop >/dev/null 2>&1 || true
pkill -f KotlinCompileDaemon || true
pkill -f GradleDaemon || true
free -m || true
ls -l third-party
