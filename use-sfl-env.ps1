# SFL local development environment
# This affects only the current PowerShell session.
#
# `use-sfl-env.sh` is the bash twin, for Git Bash and for scripts/sfl-all-services.sh, which cannot
# read PowerShell. Change the two together.

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:SFL_DB_USERNAME = "sfl"
$env:SFL_DB_PASSWORD = "sfl"

$env:SFL_TEST_DB_USERNAME = "sfl"
$env:SFL_TEST_DB_PASSWORD = "sfl"

Remove-Item Env:SFL_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:SFL_TEST_DB_URL -ErrorAction SilentlyContinue

$env:SFL_FACILITIES_DB_URL = "jdbc:postgresql://localhost:5441/sfl_facilities_service"
$env:SFL_FACILITIES_TEST_DB_URL = "jdbc:postgresql://localhost:55441/sfl_facilities_service_e2e"

$env:SFL_SAFETY_SECURITY_DB_URL = "jdbc:postgresql://localhost:5442/sfl_safety_security_service"
$env:SFL_SAFETY_SECURITY_TEST_DB_URL = "jdbc:postgresql://localhost:55442/sfl_safety_security_service_e2e"

$env:SFL_FLEET_LOGISTICS_DB_URL = "jdbc:postgresql://localhost:5443/sfl__fleet_vehicle_service"
$env:SFL_FLEET_LOGISTICS_TEST_DB_URL = "jdbc:postgresql://localhost:55443/sfl__fleet_vehicle_service_e2e"

# Three platforms, three databases. AVAMP's `asset_visibility` schema now lives in the fleet
# database and S174's `emergency_notification` schema in the safety-security one, so
# SFL_ASSET_VISIBILITY_* and SFL_EMERGENCY_NOTIFICATION_* no longer exist. They are actively
# removed rather than merely omitted: a shell that sourced an older copy of this script would
# otherwise keep them set, and a stale URL pointing at a container nobody starts fails at a
# confusing distance from its cause.
Remove-Item Env:SFL_ASSET_VISIBILITY_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:SFL_ASSET_VISIBILITY_TEST_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:SFL_EMERGENCY_NOTIFICATION_DB_URL -ErrorAction SilentlyContinue
Remove-Item Env:SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL -ErrorAction SilentlyContinue

Write-Host "SFL environment loaded."
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Service DB URLs loaded for facilities (IFIMP), safety-security (SSEMP, incl. S174) and fleet-logistics (FTLMP, incl. AVAMP)."
java -version
