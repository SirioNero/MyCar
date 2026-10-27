#!/usr/bin/env bash
# A Music Assistant of the workflow's own, for the shots of Xonos inside myCar: started in Docker on
# the runner - the emulator reaches it at 10.0.2.2:8095 - with an album of its own in a local folder
# (three one-minute tones, made with ffmpeg), its first user and a long-lived token for myCar,
# written to $GITHUB_ENV as MA_TOKEN. It lives and dies with the run: user, password and token are
# throwaway. Music Assistant 2.10.8, the version Xonos's commands were checked against.
set -euo pipefail
MA=http://localhost:8095
W=${RUNNER_TEMP:-/tmp}/music-assistant
ALBUM="$W/music/Preview Artist/Preview Album"
mkdir -p "$ALBUM" "$W/data"

for t in 1 2 3; do
    docker run --rm -v "$W/music:/music" --entrypoint /ffmpeg mwader/static-ffmpeg:7.1 \
        -loglevel error -y -f lavfi -i "sine=f=$((300 + t * 80)):d=60" -ac 2 -ar 48000 \
        -metadata artist="Preview Artist" -metadata album_artist="Preview Artist" \
        -metadata album="Preview Album" -metadata title="Tone $t" -metadata track="$t" \
        "/music/Preview Artist/Preview Album/0$t Tone $t.flac"
done
ls "$ALBUM"

docker run -d --name music-assistant --network host -v "$W/data:/data" -v "$W/music:/music:ro" \
    ghcr.io/music-assistant/server:2.10.8 >/dev/null
echo "waiting for Music Assistant to start..."
for _ in $(seq 1 120); do
    curl -sf "$MA/info" >/dev/null && break
    sleep 2
done
if ! curl -sf "$MA/info" >/dev/null; then
    echo "Music Assistant didn't start"
    docker logs music-assistant 2>&1 | tail -40
    exit 1
fi

# The first user, as a new install makes it (POST /setup answers with a session token).
session=$(curl -sf -X POST "$MA/setup" -H 'Content-Type: application/json' \
    -d "{\"username\":\"mycar\",\"password\":\"preview-only-$RANDOM$RANDOM\",\"device_name\":\"myCar preview\"}" | jq -r .token)
echo "::add-mask::$session"

# Then over the WebSocket API: a long-lived token, onboarding done, the folder as a music
# provider, and a sync, waited for until the album's tracks are in the library.
pip install -q websocket-client
token=$(python3 - "$session" <<'PY'
import json, sys, time
import websocket

ws = websocket.create_connection("ws://localhost:8095/ws")
ws.recv()  # server_info
count = 0

def cmd(command, **args):
    global count
    count += 1
    mid = str(count)
    ws.send(json.dumps({"message_id": mid, "command": command, "args": args}))
    parts = []
    while True:
        msg = json.loads(ws.recv())
        if "event" in msg or msg.get("message_id") != mid:
            continue
        if msg.get("partial"):
            parts += msg.get("result") or []
            continue
        if "error_code" in msg:
            raise RuntimeError(f"{command}: {msg['error_code']} {msg.get('details')}")
        result = msg.get("result")
        return parts + (result or []) if parts else result

cmd("auth", token=sys.argv[1])
token = cmd("auth/token/create", name="myCar preview")
try:
    cmd("config/onboard_complete")
except RuntimeError:
    pass
step = cmd("config/providers/setup", provider_domain="filesystem_local")
if isinstance(step, dict) and step.get("type") == "form":
    cmd("config/flows/submit", flow_id=step["flow_id"], values={"content_type": "music", "path": "/music"})
cmd("music/sync")
for _ in range(60):
    tracks = cmd("music/tracks/library_items", limit=10) or []
    if len(tracks) >= 3:
        break
    time.sleep(2)
print(f"{len(tracks)} tracks in the library", file=sys.stderr)
print(token)
PY
)
echo "::add-mask::$token"
echo "MA_TOKEN=$token" >> "$GITHUB_ENV"
echo "Music Assistant $(curl -sf "$MA/info" | jq -r .server_version) is ready for Xonos's shots"
