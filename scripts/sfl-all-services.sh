#!/usr/bin/env bash
#
# Start the whole of SFL: infrastructure, a tested build, and all three services.
#
# ## Why this is a script and not a compound run configuration
#
# IntelliJ's compound configurations launch several things at once and can do nothing before or
# between them - no build step, no test step, no waiting for a database. That left the project with
# four configurations to run in the right order and a way to get it wrong: start the services before
# the containers and all three die on Flyway, which reads as a broken build rather than a missed step.
#
# One command instead. It brings up the containers, builds and tests, then launches the three services
# and holds them in one console. Ctrl+C stops all three.
#
# ## The tests are not skipped
#
# The database-backed suites disable themselves when they cannot find PostgreSQL, so a build that
# "passed" can have proved very little. The e2e containers are started and their URLs exported, which
# is what turns 24 skipped SSEMP scenarios and every facilities and fleet end-to-end suite back on.
# See CLAUDE.md for the names - two use a single underscore and one a double, which is not guessable.
#
# Usage:  scripts/sfl-all-services.sh [--skip-tests] [--skip-build]
set -uo pipefail

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1
ROOT="$PWD"

SKIP_TESTS=0
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-tests) SKIP_TESTS=1 ;;
    --skip-build) SKIP_BUILD=1; SKIP_TESTS=1 ;;
    *) echo "Unknown option: $arg" >&2; exit 2 ;;
  esac
done

step()  { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
ok()    { printf '\033[0;32m    %s\033[0m\n' "$*"; }
warn()  { printf '\033[0;33m    %s\033[0m\n' "$*"; }
die()   { printf '\n\033[0;31m!!  %s\033[0m\n' "$*" >&2; exit 1; }

# --------------------------------------------------------------------------------- prerequisites

# The environment, from the file that owns it.
#
# `use-sfl-env.sh` is the bash twin of `use-sfl-env.ps1` and holds JAVA_HOME and every database URL.
# Sourcing it rather than repeating those values here means the launcher cannot drift from the file
# a developer sources by hand - and nothing is written to the machine's environment: `export` lives
# in this process and its children, so the system default JDK is untouched.
[ -f "$ROOT/use-sfl-env.sh" ] || die "use-sfl-env.sh is missing; it holds JAVA_HOME and the database URLs."
SFL_ENV_QUIET=1 . "$ROOT/use-sfl-env.sh"

# The version is then verified, not assumed. Spring Boot 4.1 will not run on 11, this machine's
# default is Zulu 11, and an existence check accepts it happily and fails hundreds of classes deep
# with a class-version error. Gap report C-15 records the same trap; the first draft of this script
# walked into it.
java_major() {
  [ -x "$1/bin/java" ] || return 1
  "$1/bin/java" -version 2>&1 | head -1 | sed -nE 's/.*"([0-9]+).*/\1/p'
}
JAVA_VERSION="$(java_major "${JAVA_HOME:-}" 2>/dev/null || true)"
[ "$JAVA_VERSION" = "17" ]   || die "JAVA_HOME is Java ${JAVA_VERSION:-unset}, not 17. Correct it in use-sfl-env.sh."
ok "JAVA_HOME $JAVA_HOME (Java $JAVA_VERSION)"

command -v docker >/dev/null 2>&1 || die "Docker is not on PATH. Start Docker Desktop and try again."
docker info >/dev/null 2>&1 || die "Docker is not responding. Start Docker Desktop and try again."

# ------------------------------------------------------------- a previous run, still running

# Reclaims the four services this script started last time, before anything tries to overwrite them.
#
# ## Why this is not optional
#
# On Windows a running service holds its own jar open, so `spring-boot:repackage` cannot rename the
# jar it has just built and the reactor dies with:
#
#     Unable to rename '...sfl-facilities-service-0.1.0-SNAPSHOT.jar'
#                  to '...sfl-facilities-service-0.1.0-SNAPSHOT.jar.original'
#
# which is a message about a file operation that says nothing whatever about the cause. It reads as a
# corrupt target directory, and the obvious response - delete `target` and build again - fixes it for
# exactly as long as it takes the next run to hold the jar open again. The build is also *green* at
# that point: every test has passed, and the failure lands at packaging, so the first thing anyone
# does is re-read a test report that has nothing wrong with it.
#
# Ports 8090-8093 would clash immediately afterwards in any case, so a leftover run is fatal whether
# or not this invocation builds - which is why the check sits here rather than beside the build.
#
# ## What it will and will not stop
#
# Only processes started the way `launch` starts them: `java -jar <root>/services/sfl-*-service/
# target/*.jar`. An IntelliJ debug session runs the same service from a classpath rather than a jar
# and therefore does not match, deliberately - stopping somebody's breakpoints to save a rebuild
# would be a poor trade, and the three Spring Boot run configurations exist precisely so one service
# can be debugged while the rest are left alone. If a debugged service is holding port 8091, this
# script will still fail on the port, and that is the right outcome: the developer knows why.
previous_run_pids() {
  if command -v powershell.exe >/dev/null 2>&1; then
    # `-replace "\\", "/"` so a command line written with either separator matches one pattern.
    powershell.exe -NoProfile -NonInteractive -Command '
      Get-CimInstance Win32_Process -Filter "Name=''java.exe''" |
        Where-Object { ($_.CommandLine -replace "\\", "/") -match "services/sfl-[a-z-]+-service/target/[^ ]*\.jar" } |
        ForEach-Object { $_.ProcessId }' 2>/dev/null | tr -d '\r'
  else
    pgrep -f 'java .*-jar .*services/sfl-[a-z-]*-service/target/.*\.jar' 2>/dev/null
  fi
}

stop_pid() {
  if command -v powershell.exe >/dev/null 2>&1; then
    powershell.exe -NoProfile -NonInteractive \
      -Command "Stop-Process -Id $1 -Force -ErrorAction SilentlyContinue" >/dev/null 2>&1
  else
    kill "$1" 2>/dev/null
  fi
}

leftovers="$(previous_run_pids | tr -d ' ' | grep -E '^[0-9]+$' || true)"
if [ -n "$leftovers" ]; then
  step "A previous run is still up - stopping it first"
  for pid in $leftovers; do
    stop_pid "$pid"
    ok "stopped $pid"
  done
  # Waited for rather than assumed: Stop-Process returns before the handle on the jar is released,
  # and building into that window reproduces the exact failure this block exists to prevent.
  waited=0
  while [ -n "$(previous_run_pids | tr -d ' ' | grep -E '^[0-9]+$' || true)" ]; do
    sleep 1
    waited=$((waited + 1))
    [ "$waited" -ge 20 ] && die "A previous service would not stop. Close it and try again."
  done
  ok "previous run cleared"
fi

# ------------------------------------------------------------------------------- infrastructure

step "Infrastructure - PostgreSQL and RabbitMQ"
docker compose -f compose.service-dbs.yml up -d >/dev/null 2>&1 \
  || die "Could not start the database containers."
docker compose -f compose.broker.yml up -d >/dev/null 2>&1 \
  || die "Could not start the broker."

# Ports, not container status: a container reports running before PostgreSQL is accepting
# connections, and starting the build in that window fails on the first Flyway migration.
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
if [ "$SKIP_TESTS" -eq 0 ]; then
  for entry in "55441:facilities e2e db" "55442:safety-security e2e db" "55443:fleet e2e db"; do
    wait_for_port "${entry%%:*}" "${entry#*:}"
  done
fi

# The end-to-end databases are recreated before every test run.
#
# They accumulate. Two facilities suites fail against a used one and are right to: the migration suite
# proves V1..V23 apply to an *empty* schema and refuses a database with rows in it, and
# `FacilitiesJwtSecurityTest` expects its POST to be refused 403 but gets 409 because the record it
# posts already exists from a previous run. Both read as product defects and are neither.
#
# Safe by construction: these are the `_e2e` databases on 55441-55443, containers of their own,
# separate from the development databases on 5441-5443 that hold anything worth keeping. A test
# database that survives between runs is a test database that lies.
#
# WITH (FORCE) because a pooled connection from the last run will still be attached, and DROP
# DATABASE waits politely forever rather than failing.
reset_test_databases() {
  reset_one() { # container, database
    docker exec "$1" psql -U sfl -d postgres         -c "DROP DATABASE IF EXISTS $2 WITH (FORCE);"         -c "CREATE DATABASE $2 OWNER sfl;" >/dev/null 2>&1       && return 0 || return 1
  }
  local failed=0
  reset_one sfl-facilities-e2e-postgres      sfl_facilities_service_e2e       || failed=1
  reset_one sfl-facilities-e2e-postgres      sfl_facilities_migration_test    || failed=1
  reset_one sfl-safety-security-e2e-postgres sfl_safety_security_service_e2e  || failed=1
  reset_one sfl-fleet-vehicle-e2e-postgres   sfl__fleet_vehicle_service_e2e   || failed=1
  [ "$failed" -eq 0 ]     && ok "test databases recreated"     || warn "could not recreate every test database; suites that need an empty one may fail"
}

# ------------------------------------------------------------------------------ build and test

# The dashboard is served from inside the portal jar, copied from `dist` when the jar is built - so a
# front-end change is invisible on the portal until this runs. `-Pui` builds it first; without the
# profile the copy silently reuses whatever `dist` happens to hold.
if [ "$SKIP_BUILD" -eq 0 ]; then
  if [ "$SKIP_TESTS" -eq 0 ]; then
    step "Build and test - all modules, dashboard included"
    reset_test_databases
    (cd services && ../mvnw.cmd -Pui install) || die "Build or tests failed. Nothing was started."
  else
    step "Build - tests skipped by request"
    (cd services && ../mvnw.cmd -Pui install -DskipTests) || die "Build failed. Nothing was started."
  fi
  ok "Build green"
else
  warn "Build skipped - launching whatever was built last"
fi

# ------------------------------------------------------------------------------------- services

declare -a PIDS=()
STOPPING=0
stop_all() {
  # Idempotent: a terminal sends INT and the shell can follow with TERM, so without this the handler
  # runs twice and prints two "Stopping" blocks for one Ctrl+C.
  [ "$STOPPING" -eq 1 ] && return
  STOPPING=1
  printf '\n'
  step "Stopping"
  for pid in "${PIDS[@]:-}"; do
    [ -n "$pid" ] && kill "$pid" 2>/dev/null && ok "stopped $pid"
  done
  wait 2>/dev/null
  exit 0
}
trap stop_all INT TERM

launch() { # module, port, label
  local module="$1" port="$2" label="$3"
  local jar="$ROOT/services/$module/target/$module-0.1.0-SNAPSHOT.jar"
  [ -f "$jar" ] || die "$jar is missing. Run without --skip-build."

  # Prefixed so three services in one console stay tellable apart.
  (
    SFL_SECURITY_ENABLED=false "$JAVA_HOME/bin/java" -jar "$jar" --server.port="$port" 2>&1 \
      | sed -u "s/^/[$label] /"
  ) &
  PIDS+=("$!")
  ok "$label starting on $port"
}

step "Services"
# The portal opens the browser once it is ready; the three platform services do not, or one start
# would produce four tabs. It is a static file server, so it is up long before the others.
export SFL_PORTAL_OPEN_BROWSER=true
launch sfl-portal-service          8090 PORTAL
launch sfl-facilities-service      8091 IFIMP
launch sfl-safety-security-service 8092 SSEMP
launch sfl-fleet-logistics-service 8093 FTLMP

# Facilities takes about a minute - long enough to conclude the wrong thing and go looking for a
# fault - so the summary waits for all four rather than printing while some are still booting.
step "Waiting for all four to report ready"
for entry in "8090:SFL portal" "8091:IFIMP facilities" "8092:SSEMP safety & security" "8093:FTLMP fleet & vehicle"; do
  wait_for_port "${entry%%:*}" "${entry#*:}"
done

cat <<BANNER

  ---------------------------------------------------------------------
   SFL is up.  One portal, three services.

     Unified portal          http://localhost:8090/home

     IFIMP facilities        http://localhost:8091/swagger-ui.html
                             http://localhost:8091/home
     SSEMP safety & security http://localhost:8092/swagger-ui.html
                             http://localhost:8092/home
     FTLMP fleet & vehicle   http://localhost:8093/swagger-ui.html
                             http://localhost:8093/home

   Every service serves the dashboard on its own port, so any one of these is a
   complete way in. The account you sign in with decides what you land on.

   Ctrl+C stops all four. The containers keep running.
  ---------------------------------------------------------------------

BANNER

wait
