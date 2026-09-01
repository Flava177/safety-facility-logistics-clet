#!/usr/bin/env bash
#
# Kills whatever is listening on SFL's service ports, regardless of how it was started. Exists
# because IntelliJ's Stop button is unreliable for the "SFL all services" config specifically: it
# runs as a Shell Script config with "Execute in the terminal" on, and the script backgrounds three
# independent java processes (one per service) rather than staying as IntelliJ's own tracked child.
# Once IntelliJ loses track of that process tree - which it reliably does here - the Stop button in
# that Run tab greys out while the three java processes keep running, orphaned, in the background.
# This is the same `lsof` + `kill` you'd run by hand, just for all three ports at once.
set -uo pipefail

ok()   { printf '\033[0;32m    %s\033[0m\n' "$*"; }
warn() { printf '\033[0;33m    %s\033[0m\n' "$*"; }

for port in 8091 8092 8093; do
  pid="$(lsof -nP -iTCP:"$port" -sTCP:LISTEN -t 2>/dev/null || true)"
  if [ -z "$pid" ]; then
    ok "nothing listening on $port"
    continue
  fi
  kill "$pid" 2>/dev/null || true
  for _ in 1 2 3 4 5 6 7 8 9 10; do
    lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1 || break
    sleep 1
  done
  if lsof -nP -iTCP:"$port" -sTCP:LISTEN -t >/dev/null 2>&1; then
    warn "$port ($pid) would not stop gracefully - force-killing"
    kill -9 "$pid" 2>/dev/null || true
  fi
  ok "stopped whatever was on $port (was pid $pid)"
done
