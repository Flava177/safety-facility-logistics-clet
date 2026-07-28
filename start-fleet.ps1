<#
.SYNOPSIS
    Starts the SFL Fleet & Logistics service with the operations console built in.

.DESCRIPTION
    One command for local work. It loads the SFL environment, brings the service databases up,
    builds the SFL Operations UI if needed, then runs the Spring Boot service. The service serves
    the API, Swagger and the console from the same origin on port 8093, and opens Swagger and the
    console in your browser once it is ready.

        http://localhost:8093/ui/             operations console
        http://localhost:8093/swagger-ui.html Swagger UI
        http://localhost:8093/v3/api-docs     OpenAPI JSON

    Security is off in local development (sfl.security.enabled=false), so there is no sign-in step:
    the front end sends the X-SFL-* actor headers instead.

.PARAMETER SkipDb
    Do not run docker compose - use when the databases are already up.

.PARAMETER SkipUiBuild
    Do not build the console. Only safe when you have just built it yourself.

.PARAMETER RebuildUi
    Kept for compatibility. The console is now always rebuilt unless -SkipUiBuild is given.

.PARAMETER NoBrowser
    Start the service without opening browser tabs.

.EXAMPLE
    .\start-fleet.ps1

.EXAMPLE
    .\start-fleet.ps1 -RebuildUi
#>
[CmdletBinding()]
param(
    [switch] $SkipDb,
    [switch] $SkipUiBuild,
    [switch] $RebuildUi,
    [switch] $NoBrowser
)

$ErrorActionPreference = "Stop"

$repoRoot = $PSScriptRoot
$uiDir    = Join-Path $repoRoot "frontend\sfl-operations-ui"
$uiDist   = Join-Path $uiDir "dist"

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

# --- 1. Environment -------------------------------------------------------------------------
Write-Step "Loading the SFL environment"
. (Join-Path $repoRoot "use-sfl-env.ps1")

# --- 2. Databases ---------------------------------------------------------------------------
if (-not $SkipDb) {
    Write-Step "Starting the service databases"
    docker compose -f (Join-Path $repoRoot "compose.service-dbs.yml") up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed. Start Docker Desktop, or re-run with -SkipDb if the databases are already up."
    }
} else {
    Write-Step "Skipping the databases (-SkipDb)"
}

# --- 3. Operations console ------------------------------------------------------------------
# Always rebuild unless explicitly skipped. Presence of a dist/ says nothing about whether it
# matches the current sources, and a stale bundle served silently is the worst outcome.
$needsUiBuild = $true

if ($SkipUiBuild) {
    Write-Step "Skipping the console build (-SkipUiBuild)"
} elseif ($needsUiBuild) {
    Write-Step "Building the SFL Operations UI"
    if (-not (Get-Command npm -ErrorAction SilentlyContinue)) {
        throw "npm was not found on PATH. Install Node.js 20+ or re-run with -SkipUiBuild."
    }
    Push-Location $uiDir
    try {
        # Always run install: it is close to a no-op when the tree is current, and it is what picks
        # up a dependency added since the last run.
        Write-Host "Resolving front-end dependencies..."
        npm install --no-audit --no-fund
        if ($LASTEXITCODE -ne 0) { throw "npm install failed." }

        npm run build
        if ($LASTEXITCODE -ne 0) {
            throw "npm run build failed. Fix the errors above, then re-run .\start-fleet.ps1."
        }
    }
    finally {
        Pop-Location
    }
} else {
    Write-Step "Reusing the existing console build (-SkipUiBuild was not given, so this should not happen)"
}

# Say out loud which bundle is about to be served. A console that looks unchanged after a rebuild is
# almost always a stale bundle, and that is invisible unless someone prints the file name.
$indexHtml = Join-Path $uiDist "index.html"
if (Test-Path $indexHtml) {
    $bundle = (Select-String -Path $indexHtml -Pattern 'assets/index-[^"]+\.js' -AllMatches |
        Select-Object -First 1).Matches.Value
    $builtAt = (Get-Item $indexHtml).LastWriteTime
    Write-Host "  console bundle : $bundle"
    Write-Host "  built at       : $builtAt"
} else {
    Write-Host "  console bundle : none - the service will start without the /ui routes" -ForegroundColor Yellow
}

# --- 4. Service -----------------------------------------------------------------------------
$env:SFL_SECURITY_ENABLED   = "false"
$env:SFL_FLEET_OPEN_BROWSER = if ($NoBrowser) { "false" } else { "true" }

Write-Step "Starting the Fleet & Logistics service on http://localhost:8093"
Write-Host "  console : http://localhost:8093/ui/"
Write-Host "  swagger : http://localhost:8093/swagger-ui.html"
Write-Host "  stop    : Ctrl+C"

Push-Location (Join-Path $repoRoot "services")
try {
    & (Join-Path $repoRoot "mvnw.cmd") -pl sfl-fleet-logistics-service spring-boot:run
}
finally {
    Pop-Location
}
