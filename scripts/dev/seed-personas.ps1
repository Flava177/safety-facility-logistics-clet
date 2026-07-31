<#
.SYNOPSIS
    Seeds data owned by each persona, so the role portals have something to show.

.DESCRIPTION
    A1 (authentication) is deferred, so the dashboard still identifies its actor with X-SFL-* headers
    and the Actor Switcher can become anybody. That makes the *roles* easy; it does not make the
    *data* appear. Every personal portal is narrowed per record by the service:

        driver      FuelApplicationService     logbooks WHERE created_by = actor
        requester   FacilityFaultService       faults   WHERE reported_by = actor
        technician  WorkOrderApplicationService work orders WHERE assigned_to = actor

    So switching to "Driver" against an empty database shows an empty portal, and there is no way to
    tell that from a broken one. This script creates records owned by the same actor ids the switcher
    presets use, so each portal opens on real rows.

    It writes through the **real APIs with the persona's own headers**, never straight into the
    database. That matters: a row inserted by SQL would carry whatever created_by the script felt
    like, whereas a row created through the API carries whatever the service decided — which is the
    thing being demonstrated. If the narrowing is broken, seeding this way surfaces it instead of
    hiding it.

.PARAMETER FacilitiesUrl
    Base URL of sfl-facilities-service. Default http://localhost:8091.

.PARAMETER FleetUrl
    Base URL of sfl-fleet-logistics-service. Default http://localhost:8093.

.PARAMETER Site
    Site code to seed against. Must already exist — this script does not create estate.

.EXAMPLE
    ./scripts/dev/seed-personas.ps1 -Site CLET-HQ

.NOTES
    Development only. It sends actor headers the services trust because security is off; with A1
    landed these calls need a token and this script needs a client credential instead.
#>
param(
    [string] $FacilitiesUrl = "http://localhost:8091",
    [string] $FleetUrl = "http://localhost:8093",
    [string] $Site = "CLET-HQ"
)

$ErrorActionPreference = "Stop"

# The actor ids are the switcher presets, verbatim. If they drift apart the portals go empty and the
# reason is invisible, so they are listed here together rather than derived.
$Personas = @{
    Requester  = @{ User = "akosua.requester"; Name = "Akosua Requester";   Roles = "IFIMP_REQUESTER" }
    Technician = @{ User = "yaw.technician";   Name = "Yaw Technician";     Roles = "IFIMP_TECHNICIAN" }
    Supervisor = @{ User = "supervisor";       Name = "Maintenance Lead";   Roles = "IFIMP_MAINTENANCE_SUPERVISOR" }
    Driver     = @{ User = "kwame.driver";     Name = "Kwame Driver";       Roles = "FLEET_DRIVER" }
    Manager    = @{ User = "fleet.manager";    Name = "Fleet Manager";      Roles = "FLEET_MANAGER" }
}

function Invoke-AsPersona {
    param(
        [hashtable] $Persona,
        [string] $Method,
        [string] $Uri,
        $Body
    )

    $headers = @{
        "X-SFL-User"         = $Persona.User
        "X-SFL-Display-Name" = $Persona.Name
        "X-SFL-Roles"        = $Persona.Roles
        "X-SFL-Sites"        = $Site
        "X-Correlation-ID"   = "seed-personas-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
        "Content-Type"       = "application/json"
    }

    if ($null -ne $Body) {
        $json = $Body | ConvertTo-Json -Depth 8
        return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $headers -Body $json
    }
    return Invoke-RestMethod -Uri $Uri -Method $Method -Headers $headers
}

function Test-ServiceUp {
    param([string] $Name, [string] $Url)
    try {
        Invoke-RestMethod -Uri "$Url/actuator/health" -Method Get -TimeoutSec 5 | Out-Null
        Write-Host "  $Name is up at $Url" -ForegroundColor Green
        return $true
    } catch {
        Write-Warning "  $Name is not answering at $Url - skipping the records it owns."
        return $false
    }
}

Write-Host "Seeding persona-owned data at site $Site" -ForegroundColor Cyan
$facilitiesUp = Test-ServiceUp -Name "facilities" -Url $FacilitiesUrl
$fleetUp = Test-ServiceUp -Name "fleet-logistics" -Url $FleetUrl

# ---------------------------------------------------------------------------------------------
# IFIMP - a requester's faults, and a technician's queue
# ---------------------------------------------------------------------------------------------
if ($facilitiesUp) {
    Write-Host "`nIFIMP" -ForegroundColor Cyan

    # Reported BY the requester, so `reported_by` is theirs and `requesterFilter` returns it.
    $faults = @(
        @{ title = "Lecture hall projector will not power on"; description = "Hall A, front projector dead."; priority = "HIGH" },
        @{ title = "Air conditioning noisy in seminar room"; description = "Rattling since Monday."; priority = "MEDIUM" },
        @{ title = "Door closer loose on the east corridor"; description = "Slams. Trip hazard."; priority = "LOW" }
    )

    foreach ($fault in $faults) {
        $body = @{
            siteCode    = $Site
            title       = $fault.title
            description = $fault.description
            priority    = $fault.priority
        }
        try {
            $created = Invoke-AsPersona -Persona $Personas.Requester -Method Post `
                -Uri "$FacilitiesUrl/api/v1/facilities/faults" -Body $body
            Write-Host ("  fault {0} reported by {1}" -f $created.data.faultNumber, $Personas.Requester.User)
        } catch {
            Write-Warning ("  could not report '{0}': {1}" -f $fault.title, $_.Exception.Message)
        }
    }

    # Raised and assigned BY the supervisor TO the technician, because assignment is the boundary
    # S153 narrows on and a technician cannot assign work to themselves.
    try {
        $open = Invoke-AsPersona -Persona $Personas.Supervisor -Method Get `
            -Uri "$FacilitiesUrl/api/v1/facilities/faults?siteCode=$Site"
        $triaged = @($open.data | Where-Object { $_.status -eq "TRIAGED" -or $_.status -eq "REPORTED" }) | Select-Object -First 2

        foreach ($fault in $triaged) {
            try {
                $order = Invoke-AsPersona -Persona $Personas.Supervisor -Method Post `
                    -Uri "$FacilitiesUrl/api/v1/facilities/work-orders/from-fault" `
                    -Body @{ faultId = $fault.id; assignTo = $Personas.Technician.User }
                Write-Host ("  work order {0} assigned to {1}" -f $order.data.workOrderNumber, $Personas.Technician.User)
            } catch {
                Write-Warning ("  could not raise work for {0}: {1}" -f $fault.faultNumber, $_.Exception.Message)
            }
        }
    } catch {
        Write-Warning "  could not read faults as the supervisor: $($_.Exception.Message)"
    }
}

# ---------------------------------------------------------------------------------------------
# FTLMP - a driver's logbooks
# ---------------------------------------------------------------------------------------------
if ($fleetUp) {
    Write-Host "`nFTLMP" -ForegroundColor Cyan

    try {
        $vehicles = Invoke-AsPersona -Persona $Personas.Manager -Method Get `
            -Uri "$FleetUrl/api/v1/fleet/vehicles?siteCode=$Site&size=1"
        $drivers = Invoke-AsPersona -Persona $Personas.Manager -Method Get `
            -Uri "$FleetUrl/api/v1/fleet/drivers?siteCode=$Site&size=1"

        $vehicle = $vehicles.data.content | Select-Object -First 1
        $driver = $drivers.data.content | Select-Object -First 1

        if ($null -eq $vehicle -or $null -eq $driver) {
            Write-Warning "  no vehicle or driver at $Site - register one first, then re-run."
        } else {
            # Created BY the driver, so `created_by` is theirs. A logbook created by the manager
            # would not appear in the driver's portal, and that is correct rather than a bug.
            $journeys = @(
                @{ origin = "Accra HQ"; destination = "Kumasi Centre"; purpose = "Examination materials"; start = 12000; end = 12240 },
                @{ origin = "Accra HQ"; destination = "Tema Centre";   purpose = "Staff transport";       start = 12240; end = 12305 }
            )

            foreach ($journey in $journeys) {
                $body = @{
                    siteCode            = $Site
                    driverId            = $driver.id
                    vehicleId           = $vehicle.id
                    journeyDate         = (Get-Date).ToString("yyyy-MM-dd")
                    startTime           = (Get-Date).AddHours(-4).ToUniversalTime().ToString("o")
                    endTime             = (Get-Date).AddHours(-1).ToUniversalTime().ToString("o")
                    origin              = $journey.origin
                    destination         = $journey.destination
                    useClassification   = "OFFICIAL"
                    purpose             = $journey.purpose
                    startOdometer       = $journey.start
                    endOdometer         = $journey.end
                    declarationAccepted = $true
                }
                try {
                    $logbook = Invoke-AsPersona -Persona $Personas.Driver -Method Post `
                        -Uri "$FleetUrl/api/v1/fuel/logbooks" -Body $body
                    Write-Host ("  logbook {0} created by {1}" -f $logbook.data.logbookNumber, $Personas.Driver.User)
                } catch {
                    Write-Warning ("  could not create logbook {0}->{1}: {2}" -f $journey.origin, $journey.destination, $_.Exception.Message)
                }
            }
        }
    } catch {
        Write-Warning "  could not read the fleet register: $($_.Exception.Message)"
    }
}

Write-Host "`nDone. Open the dashboard, use the Actor Switcher, and pick a persona." -ForegroundColor Cyan
Write-Host "A driver should now see their own logbooks and nobody else's - that is the check." -ForegroundColor Cyan
