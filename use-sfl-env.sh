# SFL local development environment - the bash twin of use-sfl-env.ps1.
#
# Source it, do not run it:  . ./use-sfl-env.sh
#
# ## Why there are two
#
# `use-sfl-env.ps1` is for a PowerShell session and cannot be sourced by bash; this is for Git Bash,
# for `scripts/sfl-all-services.sh`, and for the IntelliJ shell run configuration, none of which can
# read the PowerShell one. **The two must be changed together.** They are duplication with a reason,
# not by accident, and the reason is that neither shell can read the other's syntax.
#
# ## It affects this shell only
#
# Nothing here is written to the machine's environment. `export` lives in the process that runs it
# and its children, so a JAVA_HOME of 17 for SFL leaves whatever the rest of the system uses alone -
# which matters here, where the machine default is Zulu 11 and other projects rely on it.

# macOS resolves JDK 17 dynamically via java_home (works regardless of installer -
# Homebrew, Temurin .pkg, etc). Windows/Git Bash keeps the hardcoded path below since
# java_home does not exist there - adjust it if your JDK 17 lives somewhere else.
if [ "$(uname -s)" = "Darwin" ] && JAVA_HOME_MAC="$(/usr/libexec/java_home -v 17 2>/dev/null)"; then
  export JAVA_HOME="$JAVA_HOME_MAC"
else
  export JAVA_HOME="/c/Program Files/Eclipse Adoptium/jdk-17.0.19.10-hotspot"
fi
export PATH="$JAVA_HOME/bin:$PATH"

export SFL_DB_USERNAME=sfl
export SFL_DB_PASSWORD=sfl
export SFL_TEST_DB_USERNAME=sfl
export SFL_TEST_DB_PASSWORD=sfl

# Unset rather than left alone: a shell that sourced an older copy would otherwise keep a generic URL
# set, and one pointing at a container nobody starts fails at a confusing distance from its cause.
unset SFL_DB_URL SFL_TEST_DB_URL

export SFL_FACILITIES_DB_URL="jdbc:postgresql://localhost:5441/sfl_facilities_service"
export SFL_FACILITIES_TEST_DB_URL="jdbc:postgresql://localhost:55441/sfl_facilities_service_e2e"

# The migration suite needs a database of its own. FacilitiesMigrationIntegrationTest proves
# V1..V23 apply to an EMPTY schema, and the e2e database above is never empty - one variable
# cannot serve both, and pointing the suite at the shared one failed every otherwise-green build.
# The suite empties this database itself before Flyway runs; the _migration_test suffix is what
# permits that, so renaming it makes the emptying refuse rather than destroy something else.
# Create it once:
#   docker exec sfl-facilities-e2e-postgres psql -U sfl -d postgres -c "CREATE DATABASE sfl_facilities_migration_test OWNER sfl;"
export SFL_FACILITIES_MIGRATION_TEST_DB_URL="jdbc:postgresql://localhost:55441/sfl_facilities_migration_test"

export SFL_SAFETY_SECURITY_DB_URL="jdbc:postgresql://localhost:5442/sfl_safety_security_service"
export SFL_SAFETY_SECURITY_TEST_DB_URL="jdbc:postgresql://localhost:55442/sfl_safety_security_service_e2e"

export SFL_FLEET_LOGISTICS_DB_URL="jdbc:postgresql://localhost:5443/sfl__fleet_vehicle_service"
export SFL_FLEET_LOGISTICS_TEST_DB_URL="jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e"

# Three platforms, three databases. The `asset_visibility` schema lives in the fleet database and
# S174's `emergency_notification` in the safety-security one, so these two pairs no longer exist.
# Actively removed for the same reason as the generic URLs above.
unset SFL_ASSET_VISIBILITY_DB_URL SFL_ASSET_VISIBILITY_TEST_DB_URL
unset SFL_EMERGENCY_NOTIFICATION_DB_URL SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL

# Quiet when something else sources it - the launcher prints its own summary.
if [ "${SFL_ENV_QUIET:-0}" != "1" ]; then
  echo "SFL environment loaded for this shell only."
  echo "JAVA_HOME=$JAVA_HOME"
  "$JAVA_HOME/bin/java" -version
fi
