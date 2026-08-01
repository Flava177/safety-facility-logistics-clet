# Checks which SFL services are up.
#
# Rewritten 1 August 2026. This script used to call /api/health and /api/version on
# http://localhost:8081 — endpoints that existed only on the pre-migration legacy application, which
# has been removed. It could not have succeeded for weeks, and reported its failure as a hard error.
#
# The services expose Spring Boot's actuator instead. The health probe is deliberately reachable
# without a token: a load balancer cannot present one.

$ErrorActionPreference = "Continue"

$services = @(
    @{ Name = "facilities";             Port = 8091; Covers = "S152, S153, S159" },
    @{ Name = "safety-security";        Port = 8092; Covers = "SSEMP - unbuilt scope, starts only" },
    @{ Name = "fleet-logistics";        Port = 8093; Covers = "S166, S168_fuel, S171. Serves /ui" },
    @{ Name = "asset-visibility";       Port = 8094; Covers = "AVAMP-Lite" },
    @{ Name = "emergency-notification"; Port = 8095; Covers = "S174" }
)

$up = 0
foreach ($s in $services) {
    $url = "http://localhost:$($s.Port)/actuator/health"
    try {
        $response = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 5
        $status = $response.status
        if ($status -eq "UP") {
            Write-Host ("  UP    {0,-24} :{1}  {2}" -f $s.Name, $s.Port, $s.Covers) -ForegroundColor Green
            $up++
        }
        else {
            # A service that answers but reports DOWN is running with a failed dependency — almost
            # always its database. That is a different problem from one that is not running, and the
            # two must not look the same here.
            Write-Host ("  {0,-5} {1,-24} :{2}  answered, dependency failed" -f $status, $s.Name, $s.Port) -ForegroundColor Yellow
        }
    }
    catch {
        Write-Host ("  DOWN  {0,-24} :{1}  not answering" -f $s.Name, $s.Port) -ForegroundColor DarkGray
    }
}

Write-Host ""
Write-Host "$up of $($services.Count) services up."
if ($up -lt $services.Count) {
    Write-Host "Start one with:  cd services; ..\mvnw.cmd -pl sfl-<name>-service spring-boot:run"
    Write-Host "Set SFL_SECURITY_ENABLED=false first if no Keycloak is running, or every call returns 401."
}
