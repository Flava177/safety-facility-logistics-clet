<#
.SYNOPSIS
    Starts the SFL backend: the service databases, then the APIs. No user interface.

.DESCRIPTION
    This is the whole system minus the front end. It starts the per-service PostgreSQL containers,
    waits for them, and then runs each selected Spring Boot service in its own window.

    Nothing here builds or serves a UI. The services expose their APIs and their OpenAPI documents,
    and any front end — the React dashboard in frontend/sfl-operations-ui, or a replacement — runs
    as a separate application against those APIs.

    The one thing a new UI has to be told about is CORS. Because the interface is no longer served
    from a service origin, the browser blocks calls from any origin not named in
    SFL_CORS_ALLOWED_ORIGINS. Pass -UiOrigin to add yours for this run.

.PARAMETER Services
    Which services to start. Defaults to the three that have built systems behind them.
    Valid: facilities, fleet, emergency, asset, safety. Use -Services all for every one.

.PARAMETER SkipDb
    Do not run docker compose. Use when the databases are already up.

.PARAMETER UiOrigin
    Origin of the front end you intend to plug in, e.g. http://localhost:4200. Added to the allowed
    CORS origins for this run, on top of the defaults.

.PARAMETER Secure
    Run with authentication enabled. Off by default for local work, which is what the X-SFL-* actor
    headers exist for.

.EXAMPLE
    .\start-backend.ps1

.EXAMPLE
    .\start-backend.ps1 -Services fleet -UiOrigin http://localhost:4200
#>
param(
    [string[]] $Services = @('facilities', 'fleet', 'emergency'),
    [switch]   $SkipDb,
    [string]   $UiOrigin,
    [switch]   $Secure
)

$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot

function Write-Step($message) {
    Write-Host ""
    Write-Host "==> $message" -ForegroundColor Cyan
}

# module name, port, and what it serves. Kept in one place so the banner and the launcher cannot
# disagree about which port a service is on.
$catalogue = [ordered]@{
    facilities = @{ Module = 'sfl-facilities-service';             Port = 8091; Systems = 'S152 CAFM/IWMS, S153 CMMS, S159 booking' }
    safety     = @{ Module = 'sfl-safety-security-service';        Port = 8092; Systems = 'SSEMP (scaffold only)' }
    fleet      = @{ Module = 'sfl-fleet-logistics-service';        Port = 8093; Systems = 'S166 fleet, S168 fuel, S171 dispatch' }
    asset      = @{ Module = 'sfl-asset-visibility-service';       Port = 8094; Systems = 'AVAMP-Lite' }
    emergency  = @{ Module = 'sfl-emergency-notification-service'; Port = 8095; Systems = 'S174 emergency notification' }
}

if ($Services -contains 'all') {
    $Services = @($catalogue.Keys)
}

foreach ($name in $Services) {
    if (-not $catalogue.Contains($name)) {
        throw "Unknown service '$name'. Valid names: $($catalogue.Keys -join ', '), or 'all'."
    }
}

# --- 1. Java ---------------------------------------------------------------------------------
# The reactor needs 17+, and this machine's PATH has carried a Zulu 11 that Maven picks up silently
# and then fails with errors that never mention the JDK.
$jdk17 = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
if (Test-Path $jdk17) {
    $env:JAVA_HOME = $jdk17
}

# --- 2. Databases ----------------------------------------------------------------------------
if ($SkipDb) {
    Write-Step "Skipping the databases (-SkipDb)"
} else {
    Write-Step "Starting the service databases"
    docker compose -f (Join-Path $repoRoot "compose.service-dbs.yml") up -d
    if ($LASTEXITCODE -ne 0) {
        throw "docker compose failed. Start Docker Desktop, or re-run with -SkipDb if the databases are already up."
    }
    # Flyway runs on startup and a service that reaches a container before Postgres is accepting
    # connections dies with a connection refusal that reads like a configuration error.
    Write-Host "  waiting for PostgreSQL to accept connections..."
    Start-Sleep -Seconds 8
}

# --- 3. Configuration ------------------------------------------------------------------------
# Absent means SECURE since A1, so running open locally has to be explicit. This is what lets the
# X-SFL-* actor headers stand in for a token on a machine with no identity provider running.
if ($Secure) {
    $env:SFL_SECURITY_ENABLED = "true"
} else {
    $env:SFL_SECURITY_ENABLED = "false"
}

$origins = @(
    'http://localhost:5005',
    'http://localhost:5173',
    'http://localhost:3000',
    'http://localhost:4200',
    'http://localhost:8080'
)
if ($UiOrigin) {
    $origins = @($UiOrigin) + $origins
}
$env:SFL_CORS_ALLOWED_ORIGINS = ($origins -join ',')

# The services no longer bundle a dashboard, so there is nothing to open but Swagger, and five
# Swagger tabs is not a convenience.
$env:SFL_FLEET_OPEN_BROWSER = "false"

# --- 4. Services -----------------------------------------------------------------------------
$servicesDir = Join-Path $repoRoot "services"
$mvnw = Join-Path $repoRoot "mvnw.cmd"

Write-Step "Starting $($Services.Count) backend service(s)"

foreach ($name in $Services) {
    $entry = $catalogue[$name]
    Write-Host ("  {0,-10} :{1}  {2}" -f $name, $entry.Port, $entry.Systems)

    $command = "Set-Location '$servicesDir'; " +
               "`$env:JAVA_HOME='$env:JAVA_HOME'; " +
               "`$env:SFL_SECURITY_ENABLED='$env:SFL_SECURITY_ENABLED'; " +
               "`$env:SFL_CORS_ALLOWED_ORIGINS='$env:SFL_CORS_ALLOWED_ORIGINS'; " +
               "& '$mvnw' -pl $($entry.Module) spring-boot:run"

    Start-Process -FilePath "powershell" -ArgumentList @('-NoExit', '-Command', $command) | Out-Null
}

Write-Step "Backend starting — each service is in its own window"
Write-Host ""
Write-Host "  API base      http://localhost:<port>/api/v1"
Write-Host "  API docs      http://localhost:<port>/swagger-ui.html"
Write-Host "  OpenAPI JSON  http://localhost:<port>/v3/api-docs"
Write-Host "  Health        http://localhost:<port>/actuator/health"
Write-Host ""
foreach ($name in $Services) {
    $entry = $catalogue[$name]
    Write-Host ("    {0,-10} http://localhost:{1}/swagger-ui.html" -f $name, $entry.Port)
}
Write-Host ""
Write-Host "  No UI is served by these processes." -ForegroundColor Yellow
Write-Host "  Browser origins allowed to call them:" -ForegroundColor Yellow
Write-Host "    $env:SFL_CORS_ALLOWED_ORIGINS"
Write-Host ""
Write-Host "  To plug a UI in, point it at the ports above and make sure its own origin is in that"
Write-Host "  list — pass -UiOrigin http://localhost:<port> if it is not."
Write-Host ""
Write-Host "  Stop a service with Ctrl+C in its window."
