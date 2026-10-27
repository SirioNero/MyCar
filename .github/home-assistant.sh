#!/usr/bin/env bash
# A Home Assistant of the workflow's own, for Plancia's shots: started in Docker on the runner -
# the emulator reaches it at 10.0.2.2:8123 - and set up as a new install is (a user, then the rest
# of onboarding), with a long-lived token for myCar written to $GITHUB_ENV as HA_TOKEN. It lives
# and dies with the run: the user, password and token are throwaway.
set -euo pipefail
HA=http://localhost:8123
CLIENT="$HA/"

docker run -d --name homeassistant -p 8123:8123 -e TZ=Europe/Rome ghcr.io/home-assistant/home-assistant:stable >/dev/null
echo "waiting for Home Assistant to start..."
for _ in $(seq 1 150); do
    curl -sf "$HA/api/onboarding" >/dev/null && break
    sleep 2
done
if ! curl -sf "$HA/api/onboarding" >/dev/null; then
    echo "Home Assistant didn't start"
    docker logs homeassistant 2>&1 | tail -40
    exit 1
fi

code=$(curl -sf -X POST "$HA/api/onboarding/users" -H 'Content-Type: application/json' \
    -d "{\"client_id\":\"$CLIENT\",\"name\":\"myCar\",\"username\":\"mycar\",\"password\":\"preview-only-$RANDOM$RANDOM\",\"language\":\"en\"}" |
    jq -r .auth_code)
access=$(curl -sf -X POST "$HA/auth/token" -d "grant_type=authorization_code&code=$code&client_id=$CLIENT" | jq -r .access_token)
echo "::add-mask::$access"
auth=(-H "Authorization: Bearer $access" -H 'Content-Type: application/json')
# The rest of onboarding, as the first visit in a browser would finish it.
curl -sf -X POST "$HA/api/onboarding/core_config" "${auth[@]}" -d '{}' >/dev/null || echo "core_config: already done or refused"
curl -sf -X POST "$HA/api/onboarding/analytics" "${auth[@]}" -d '{}' >/dev/null || echo "analytics: already done or refused"
curl -sf -X POST "$HA/api/onboarding/integration" "${auth[@]}" \
    -d "{\"client_id\":\"$CLIENT\",\"redirect_uri\":\"${CLIENT}?auth_callback=1\"}" >/dev/null || echo "integration: already done or refused"

# A long-lived token, as the driver would make one in their profile: only the WebSocket API makes them.
pip install -q websocket-client
token=$(python3 - "$access" <<'PY'
import json, sys
import websocket

ws = websocket.create_connection("ws://localhost:8123/api/websocket")
ws.recv()
ws.send(json.dumps({"type": "auth", "access_token": sys.argv[1]}))
ws.recv()
ws.send(json.dumps({"id": 1, "type": "auth/long_lived_access_token", "client_name": "myCar preview", "lifespan": 1}))
print(json.loads(ws.recv())["result"])
PY
)
echo "::add-mask::$token"
echo "HA_TOKEN=$token" >> "$GITHUB_ENV"
echo "Home Assistant $(curl -sf "$HA/api/config" -H "Authorization: Bearer $token" | jq -r .version) is ready for Plancia's shots"
