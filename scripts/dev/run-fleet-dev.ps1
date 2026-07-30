<#
.SYNOPSIS
    Runs the Fleet service and the SFL Operations UI dev server side by side, with hot reload.

.DESCRIPTION
    Use this while working on the front end. The Spring Boot service runs in its own window on 8093
    and Vite serves the dashboard on 5005 with hot module replacement, so a save is reflected in the
    browser immediately - no rebuild, no restart.

        http://localhost:5005                 dashboard (hot reload)
        http://localhost:8093/swagger-ui.html Swagger UI

    The service already allows http://localhost:5005 as a CORS origin. For the bundled
    single-process experience, use .\start-fleet.ps1 at the repository root instead.

.PARAMETER SkipDb
    Do not run docker compose - use when the databases are already up.

.PARAMETER NoBrowser
    Do not open browser tabs.
#>
[CmdletBinding()]
param(
    [switch] $SkipDb,
    [switch] $NoBrowser
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
$uiDir    = Join-Path $repoRoot "frontend\sfl-operations-ui"

Write-Host "==> Loading the SFL environment" -ForegroundColor Cyan
. (Join-Path $repoRoot "use-sfl-env.ps1")

if (-not $SkipDb) {
    Write-Host "==> Starting the service databases" -ForegroundColor Cyan
    docker compose -f (Join-Path $repoRoot "compose.service-dbs.yml") up -d
    if ($LASTEXITCODE -ne 0) { throw "docker compose failed." }
}

if (-not (Test-Path (Join-Path $uiDir "node_modules"))) {
    Write-Host "==> Installing front-end dependencies (first run only)" -ForegroundColor Cyan
    Push-Location $uiDir
    try {
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw "npm install failed." }
    }
    finally { Pop-Location }
}

# The backend goes to its own window so this one can stay attached to Vite's output.
Write-Host "==> Starting the Fleet service on http://localhost:8093" -ForegroundColor Cyan
$backend = @"
. '$repoRoot\use-sfl-env.ps1'
`$env:SFL_SECURITY_ENABLED = 'false'
Set-Location '$repoRoot\services'
& '$repoRoot\mvnw.cmd' -pl sfl-fleet-logistics-service spring-boot:run
"@
Start-Process powershell -ArgumentList "-NoExit", "-Command", $backend | Out-Null

if (-not $NoBrowser) {
    Start-Process "http://localhost:8093/swagger-ui.html"
    Start-Process "http://localhost:5005"
}

Write-Host "==> Starting the dashboard dev server on http://localhost:5005 (Ctrl+C to stop)" -ForegroundColor Cyan
Push-Location $uiDir
try {
    npm run dev
}
finally { Pop-Location }
