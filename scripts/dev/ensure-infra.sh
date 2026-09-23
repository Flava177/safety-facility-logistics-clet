#!/usr/bin/env bash
#
# Brings up the local Postgres + RabbitMQ containers (if not already running) and waits until each
# is actually accepting connections. Used as an IntelliJ "before launch" step on the three solo
# Spring Boot run configurations (IFIMP/SSEMP/FTLMP), so starting any one service by itself still
# guarantees its database - and the broker, for facilities/fleet - is there before Spring Boot's
# first Flyway migration runs.
#
# `scripts/sfl-all-services.sh` has its own copy of this same logic; this file exists so the solo
# configs don't have to invoke the whole all-services script (build, tests, all three services)
# just to satisfy one dependency.
#
# Works the same under Docker Desktop or OrbStack - both speak the same `docker` CLI/socket.
set -uo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/../.." || exit 1

ok()  { printf '\033[0;32m    %s\033[0m\n' "$*"; }
die() { printf '\n\033[0;31m!!  %s\033[0m\n' "$*" >&2; exit 1; }

# A compound launch invokes this same before-launch task for all three services at once. Serialise
# those invocations so three Docker Compose clients do not race while creating the same containers
# and networks. Every waiter eventually acquires the lock and rechecks the already-running stack,
# so a failed first invocation cannot make the other two continue on a false assumption.
lock_dir="${TMPDIR:-/tmp}/sfl-infrastructure-${UID}.lock"
waited_for_lock=0
while ! mkdir "$lock_dir" 2>/dev/null; do
  if [ -r "$lock_dir/pid" ]; then
    read -r lock_pid < "$lock_dir/pid" || lock_pid=""
    case "$lock_pid" in
      ''|*[!0-9]*) ;;
      *)
        if ! kill -0 "$lock_pid" 2>/dev/null; then
          rm -f "$lock_dir/pid"
          rmdir "$lock_dir" 2>/dev/null || true
          continue
        fi
        ;;
    esac
  fi
  sleep 1
  waited_for_lock=$((waited_for_lock + 1))
  [ "$waited_for_lock" -ge 120 ] \
    && die "Another infrastructure start has held the launch lock for two minutes."
done
printf '%s\n' "$$" > "$lock_dir/pid"

release_lock() {
  rm -f "$lock_dir/pid"
  rmdir "$lock_dir" 2>/dev/null || true
}
trap release_lock EXIT
trap 'exit 130' INT TERM HUP

command -v docker >/dev/null 2>&1 || die "Docker is not on PATH. Start Docker Desktop or OrbStack and try again."
docker info       >/dev/null 2>&1 || die "Docker is not responding. Start Docker Desktop or OrbStack and try again."

docker compose -f compose.service-dbs.yml up -d --wait --wait-timeout 90 >/dev/null 2>&1 \
  || die "Could not start the database containers."
docker compose -f compose.broker.yml up -d --wait --wait-timeout 90 >/dev/null 2>&1 \
  || die "Could not start the broker."

wait_for_port() {
  local port="$1" name="$2" waited=0
  while ! (exec 3<>"/dev/tcp/127.0.0.1/$port") 2>/dev/null; do
    sleep 1
    waited=$((waited + 1))
    [ "$waited" -ge 90 ] && die "$name on $port did not come up within 90 seconds."
  done
  exec 3<&- 2>/dev/null || true
  ok "$name ready on $port"
}

for entry in "5441:facilities db" "5442:safety-security db" "5443:fleet db" "5672:broker"; do
  wait_for_port "${entry%%:*}" "${entry#*:}"
done

ok "infrastructure ready"
