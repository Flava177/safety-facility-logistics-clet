<#
.SYNOPSIS
    Starts the SFL Fleet & Logistics service with the operations dashboard built in.

.DESCRIPTION
    One command for local work. It loads the SFL environment, brings the service databases up,
    builds the SFL Operations UI if needed, then runs the Spring Boot service. The service serves
    the API, Swagger and the dashboard from the same origin on port 8093, and opens Swagger and the
    dashboard in your browser once it is ready.

        http://localhost:8093/ui/             operations dashboard
        http://localhost:8093/swagger-ui.html Swagger UI
        http://localhost:8093/v3/api-docs     OpenAPI JSON

    The dashboard opens on its sign-in page. Sign in with a seeded account - for example
    fleetmanager@clet.gh or driver@clet.gh, password Password@Clet1 - and the portal that opens is
    the one that account's roles entitle it to. The full list is on the sign-in page itself and in
    docs\frontend\SFL_Sign_In_And_Seeded_Accounts.md.

    Service security stays off locally because this script sets SFL_SECURITY_ENABLED=false
    explicitly; the default is ON since A1, so an environment that forgets the variable demands a
    token. Signing in here decides which X-SFL-* actor headers the dashboard sends, which is what
    the open services read.

.PARAMETER SkipDb
    Do not run docker compose - use when the databases are already up.

.PARAMETER SkipUiBuild
    Do not build the dashboard. Only safe when you have just built it yourself.

.PARAMETER RebuildUi
    Kept for compatibility. The dashboard is now always rebuilt unless -SkipUiBuild is given.

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

# --- 3. Operations dashboard ----------------------------------------------------------------
# Always rebuild unless explicitly skipped. Presence of a dist/ says nothing about whether it
# matches the current sources, and a stale bundle served silently is the worst outcome.
$needsUiBuild = $true

if ($SkipUiBuild) {
    Write-Step "Skipping the dashboard build (-SkipUiBuild)"
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
    Write-Step "Reusing the existing dashboard build (-SkipUiBuild was not given, so this should not happen)"
}

# Say out loud which bundle is about to be served. A dashboard that looks unchanged after a rebuild is
# almost always a stale bundle, and that is invisible unless someone prints the file name.
$indexHtml = Join-Path $uiDist "index.html"
if (Test-Path $indexHtml) {
    $bundle = (Select-String -Path $indexHtml -Pattern 'assets/index-[^"]+\.js' -AllMatches |
        Select-Object -First 1).Matches.Value
    $builtAt = (Get-Item $indexHtml).LastWriteTime
    Write-Host "  dashboard bundle : $bundle"
    Write-Host "  built at         : $builtAt"
} else {
    Write-Host "  dashboard bundle : none - the service will start without the /ui routes" -ForegroundColor Yellow
}

# --- 4. Service -----------------------------------------------------------------------------
# Load-bearing since A1: absent means SECURE. Removing this line makes the service demand a token,
# which is correct everywhere except a developer laptop with no identity provider running.
#
# The dashboard still opens on its sign-in page: that form picks which seeded account's X-SFL-*
# headers to send, which is exactly the identity an open service reads. Signing in is how the
# portal is chosen, not how the service is secured.
$env:SFL_SECURITY_ENABLED = "false"
$env:SFL_FLEET_OPEN_BROWSER = if ($NoBrowser) { "false" } else { "true" }

Write-Step "Starting the Fleet & Logistics service on http://localhost:8093"
Write-Host "  dashboard : http://localhost:8093/ui/  (opens on the sign-in page)"
Write-Host "  sign in   : fleetmanager@clet.gh / Password@Clet1" -ForegroundColor Green
Write-Host "  driver    : driver@clet.gh / Password@Clet1"
Write-Host "  swagger   : http://localhost:8093/swagger-ui.html"
Write-Host "  stop      : Ctrl+C"

Push-Location (Join-Path $repoRoot "services")
try {
    & (Join-Path $repoRoot "mvnw.cmd") -pl sfl-fleet-logistics-service spring-boot:run
}
finally {
    Pop-Location
}
