param(
    [string] $BaseUrl = "http://localhost:8081"
)

$ErrorActionPreference = "Stop"

Write-Host "Checking $BaseUrl/api/health..."
$health = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get
$health | ConvertTo-Json -Depth 5

Write-Host "Checking $BaseUrl/api/version..."
$version = Invoke-RestMethod -Uri "$BaseUrl/api/version" -Method Get
$version | ConvertTo-Json -Depth 5
