#!/usr/bin/env bash
# A tailnet of the run's own, for drivers who reach home through Tailscale with a MagicDNS name
# (http://umbrel:8123): headscale - the open-source Tailscale control server - on the runner, and
# the runner itself joined to it as "umbrel", so the run's Home Assistant and Music Assistant answer
# at http://umbrel:8123 and http://umbrel:8095 to a phone in the tailnet. The emulator reaches
# headscale at 10.0.2.2:8080; the runner gets 10.0.2.2 as well, so both use the same address.
# Writes HEADSCALE (the command) and TS_AUTHKEY (a reusable key for the phone) to $GITHUB_ENV.
set -euo pipefail
HS_VERSION=0.29.6
W=$RUNNER_TEMP/headscale
mkdir -p "$W"

curl -fsSL -o "$W/headscale" "https://github.com/juanfont/headscale/releases/download/v$HS_VERSION/headscale_${HS_VERSION}_linux_amd64"
chmod +x "$W/headscale"
curl -fsSL -o "$W/config.yaml" "https://raw.githubusercontent.com/juanfont/headscale/v$HS_VERSION/config-example.yaml"
yq -i "
  .server_url = \"http://10.0.2.2:8080\" |
  .listen_addr = \"0.0.0.0:8080\" |
  .metrics_listen_addr = \"127.0.0.1:9090\" |
  .grpc_listen_addr = \"127.0.0.1:50443\" |
  .noise.private_key_path = \"$W/noise_private.key\" |
  .derp.server.private_key_path = \"$W/derp_server_private.key\" |
  .database.sqlite.path = \"$W/db.sqlite\" |
  .unix_socket = \"$W/headscale.sock\" |
  .dns.magic_dns = true |
  .dns.base_domain = \"tailnet.test\"
" "$W/config.yaml"

sudo ip addr add 10.0.2.2/32 dev lo
nohup "$W/headscale" serve -c "$W/config.yaml" > "$W/headscale.log" 2>&1 &
for _ in $(seq 1 30); do
    curl -sf http://10.0.2.2:8080/health >/dev/null && break
    sleep 1
done
if ! curl -sf http://10.0.2.2:8080/health >/dev/null; then
    echo "headscale didn't start"
    tail -40 "$W/headscale.log"
    exit 1
fi
hs() { "$W/headscale" -c "$W/config.yaml" "$@"; }
hs users create driver >/dev/null
key=$(hs preauthkeys create -u driver --reusable -e 3h -o json | jq -r .key)
echo "::add-mask::$key"

# The runner joins as "umbrel", as the driver's home server would.
curl -fsSL https://tailscale.com/install.sh | sh >/dev/null
sudo tailscale up --login-server=http://10.0.2.2:8080 --authkey="$key" --hostname=umbrel --accept-dns=false
ip4=$(tailscale ip -4)
echo "umbrel is $ip4 and $(tailscale ip -6) in the tailnet"
curl -sf -o /dev/null "http://$ip4:8123/" && echo "Home Assistant answers at $ip4:8123" || echo "Home Assistant doesn't answer at $ip4:8123"
curl -sf -o /dev/null "http://$ip4:8095/" && echo "Music Assistant answers at $ip4:8095" || echo "Music Assistant doesn't answer at $ip4:8095"

echo "HEADSCALE=$W/headscale -c $W/config.yaml" >> "$GITHUB_ENV"
echo "TS_AUTHKEY=$key" >> "$GITHUB_ENV"
echo "HEADSCALE_LOG=$W/headscale.log" >> "$GITHUB_ENV"
