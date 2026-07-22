# SFL local development environment
# This affects only the current PowerShell session.

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:Path = "$env:JAVA_HOME\bin;$env:Path"

$env:SFL_DB_URL = "jdbc:postgresql://localhost:5434/sfl_fleet_db"
$env:SFL_DB_USERNAME = "sfl"
$env:SFL_DB_PASSWORD = "sfl"

$env:SFL_TEST_DB_URL = "jdbc:postgresql://localhost:55432/sfl_fleet_e2e_db"
$env:SFL_TEST_DB_USERNAME = "sfl"
$env:SFL_TEST_DB_PASSWORD = "sfl"

Write-Host "SFL environment loaded."
Write-Host "JAVA_HOME=$env:JAVA_HOME"
java -version