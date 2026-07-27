# SFL local development environment
# This affects only the current PowerShell session.

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

$env:SFL_ASSET_VISIBILITY_DB_URL = "jdbc:postgresql://localhost:5444/sfl_asset_visibility_service"
$env:SFL_ASSET_VISIBILITY_TEST_DB_URL = "jdbc:postgresql://localhost:55444/sfl_asset_visibility_service_e2e"

$env:SFL_EMERGENCY_NOTIFICATION_DB_URL = "jdbc:postgresql://localhost:5445/sfl_emergency_notification_service"
$env:SFL_EMERGENCY_NOTIFICATION_TEST_DB_URL = "jdbc:postgresql://localhost:55445/sfl_emergency_notification_service_e2e"

Write-Host "SFL environment loaded."
Write-Host "JAVA_HOME=$env:JAVA_HOME"
Write-Host "Service DB URLs loaded for facilities, safety-security, fleet-logistics, asset-visibility and emergency-notification."
java -version
