<#
.SYNOPSIS
    Fills the facilities estate with enough real data to demonstrate it.

.DESCRIPTION
    Five registers film as empty states against a fresh database - device references, readiness
    assessments, work orders, vendors and preventive schedules all sit at zero, and three more have
    a single row. Every screen works; there is simply nothing in them, and an empty state is
    indistinguishable from a broken screen on video.

    This writes through the **real API with real actor headers**, never into the database. That is
    not fastidiousness: a row inserted by SQL carries whatever `created_by` the script felt like and
    skips every derivation the service performs. Seeding through the API means the readiness blockers
    an out-of-service chiller raises are the ones the service decided to raise, the audit chain is
    genuinely hash-linked, the SLA deadlines are computed from the configured thresholds, and the
    dashboard counts what actually exists. If any of that is broken, this surfaces it rather than
    painting over it.

    **Re-runnable.** Every record is looked up by its code first and created only if absent, so a
    second run adds nothing and repairs anything that failed halfway through the first.

.PARAMETER FacilitiesUrl
    Base URL of sfl-facilities-service. Default http://localhost:8091.

.PARAMETER Site
    The site code to seed into. Created if it does not exist.

.PARAMETER SkipWorkflow
    Seed the registers but leave faults and work orders alone - a faster run when only the estate
    screens are being filmed.

.PARAMETER DismissStrayFaults
    Withdraw any open fault this script did not raise. Off by default: a development database
    accumulates hand-tested faults whose blockers then appear on the dashboard as estate problems
    nobody can account for on camera, but dismissing records a script does not recognise is not a
    decision it should take unasked. Use it only on a scratch database.

.EXAMPLE
    ./scripts/dev/seed-facilities-demo.ps1

.EXAMPLE
    ./scripts/dev/seed-facilities-demo.ps1 -Site CLET-HQ -SkipWorkflow

.NOTES
    Development only. It sends actor headers the service trusts because SFL_SECURITY_ENABLED=false;
    with authentication landed these calls need a token and this script needs a client credential.
#>
[CmdletBinding()]
param(
    [string] $FacilitiesUrl = "http://localhost:8091",
    [string] $Site = "CLET-HQ",
    [switch] $SkipWorkflow,
    [switch] $DismissStrayFaults
)

$ErrorActionPreference = "Stop"

# ---------------------------------------------------------------------------------------------
# Who writes what
#
# The director does the estate work, because that is the account the demo is filmed as and a record
# whose audit trail names somebody else invites the question "who is that" mid-take. Faults come
# from a requester and work is assigned to a technician, because that is the actual shape of the
# workflow - a director raising and then triaging their own fault would demonstrate a loop nobody
# runs.
# ---------------------------------------------------------------------------------------------
$Director = @{ User = "facilities.director"; Name = "Facilities Director"; Roles = "FACILITIES_DIRECTOR" }
$Requester = @{ User = "akosua.requester"; Name = "Akosua Requester"; Roles = "IFIMP_REQUESTER" }

# Named `$estateSite` rather than `$site` further down, deliberately: PowerShell variable names are
# case-insensitive, so `$site = ...` and `$Site` are the same variable. Assigning the site *record*
# to `$site` silently replaced the site *code* every later URL is built from, and the failure
# surfaced as a duplicate-identifier error on an unrelated request.
$script:Created = @{}
$script:Existing = @{}

# Windows PowerShell 5.1 has no null-coalescing operator, so these count the long way round.
function Note-Created {
    param([string] $Kind)
    if (-not $script:Created.ContainsKey($Kind)) { $script:Created[$Kind] = 0 }
    $script:Created[$Kind] = $script:Created[$Kind] + 1
}
function Note-Existing {
    param([string] $Kind)
    if (-not $script:Existing.ContainsKey($Kind)) { $script:Existing[$Kind] = 0 }
    $script:Existing[$Kind] = $script:Existing[$Kind] + 1
}

<#
    One call, with the actor headers and the envelope unwrapped.

    The service's own error message is surfaced rather than swallowed: a seed that dies on
    "The remote server returned an error: (400) Bad Request" tells whoever runs it nothing, and the
    envelope underneath names the field.
#>
function Invoke-Facilities {
    param(
        [hashtable] $Actor = $Director,
        [string] $Method = "Get",
        [Parameter(Mandatory)] [string] $Path,
        $Body,
        [string] $IdempotencyKey
    )

    $headers = @{
        "X-SFL-User"           = $Actor.User
        "X-SFL-Display-Name"   = $Actor.Name
        "X-SFL-Roles"          = $Actor.Roles
        "X-SFL-Sites"          = $Site
        "X-SFL-Source-Channel" = "WEB"
        "X-Correlation-ID"     = "seed-facilities-" + [guid]::NewGuid().ToString("N").Substring(0, 12)
        "Content-Type"         = "application/json"
    }
    if ($IdempotencyKey) { $headers["Idempotency-Key"] = $IdempotencyKey }

    $uri = "$FacilitiesUrl/api/v1/facilities$Path"
    try {
        if ($null -ne $Body) {
            $json = $Body | ConvertTo-Json -Depth 10
            $response = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers -Body $json
        } else {
            $response = Invoke-RestMethod -Uri $uri -Method $Method -Headers $headers
        }
        return $response.data
    } catch {
        $detail = $_.ErrorDetails.Message
        if (-not $detail -and $_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $detail = $reader.ReadToEnd()
        }
        throw "$Method $Path failed: $detail"
    }
}

<#
    Create a record only when nothing already carries its code.

    `$Find` returns the existing record or $null. Written as a lookup rather than relying on the
    service's duplicate-identifier refusal because that refusal is a 409 the caller would have to
    catch and interpret, and because a half-finished first run leaves some records present and some
    absent - which is exactly the state a re-run has to cope with.
#>
function New-IfAbsent {
    param(
        [Parameter(Mandatory)] [string] $Kind,
        [Parameter(Mandatory)] [string] $Code,
        [Parameter(Mandatory)] [scriptblock] $Find,
        [Parameter(Mandatory)] [scriptblock] $Create
    )

    $alreadyThere = & $Find
    if ($alreadyThere) {
        Note-Existing $Kind
        Write-Host "    = $Code" -ForegroundColor DarkGray
        return $alreadyThere
    }
    $record = & $Create
    Note-Created $Kind
    Write-Host "    + $Code" -ForegroundColor Green
    return $record
}

function Write-Step { param([string] $Message) Write-Host "`n$Message" -ForegroundColor Cyan }

# A 64-character hex digest, which is what the evidence endpoint's pattern demands. Derived from the
# reference so a re-run produces the same hash for the same file rather than a new one each time.
function Get-Digest { param([string] $Value)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Value))
    return -join ($bytes | ForEach-Object { $_.ToString("x2") })
}

$today = Get-Date
function Days { param([int] $Offset) return $today.AddDays($Offset).ToString("yyyy-MM-dd") }

# ---------------------------------------------------------------------------------------------

Write-Host "Seeding the facilities demo estate at $Site via $FacilitiesUrl" -ForegroundColor Cyan
try {
    Invoke-RestMethod -Uri "$FacilitiesUrl/actuator/health" -TimeoutSec 5 | Out-Null
} catch {
    throw "The facilities service is not answering at $FacilitiesUrl. Start it first."
}

# ---- 1. Site, buildings, floors ----------------------------------------------------------------
Write-Step "Site, buildings and floors"

$estateSite = New-IfAbsent -Kind "site" -Code $Site `
    -Find { (Invoke-Facilities -Path "/sites") | Where-Object { $_.siteCode -eq $Site } | Select-Object -First 1 } `
    -Create {
        Invoke-Facilities -Method Post -Path "/sites" -IdempotencyKey "seed-site-$Site" -Body @{
            siteCode    = $Site
            name        = "CLET Headquarters"
            description = "Main campus, Accra. Examination halls, moot courtrooms and the registry."
        }
    }

$buildings = @(
    @{ Code = "MAIN"; Name = "Main Block"; Description = "Teaching and examinations" },
    @{ Code = "ANNEX"; Name = "Annex"; Description = "Registry, administration and the moot court" },
    @{ Code = "PLANT"; Name = "Plant House"; Description = "Standby power, chillers and the water plant" }
)
foreach ($b in $buildings) {
    New-IfAbsent -Kind "building" -Code $b.Code `
        -Find { (Invoke-Facilities -Path "/buildings?siteCode=$Site") | Where-Object { $_.buildingCode -eq $b.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/buildings" -IdempotencyKey "seed-building-$($b.Code)" -Body @{
                siteId = $estateSite.id; buildingCode = $b.Code; name = $b.Name; description = $b.Description
            }
        } | Out-Null
}
$allBuildings = Invoke-Facilities -Path "/buildings?siteCode=$Site"
function Get-Building { param([string] $Code) $allBuildings | Where-Object { $_.buildingCode -eq $Code } | Select-Object -First 1 }

$floors = @(
    @{ Building = "MAIN"; Code = "GF"; Name = "Ground floor"; Level = 0 },
    @{ Building = "MAIN"; Code = "L1"; Name = "First floor"; Level = 1 },
    @{ Building = "MAIN"; Code = "L2"; Name = "Second floor"; Level = 2 },
    @{ Building = "ANNEX"; Code = "GF"; Name = "Ground floor"; Level = 0 },
    @{ Building = "ANNEX"; Code = "L1"; Name = "First floor"; Level = 1 },
    # A mezzanine has no honest level number, and the column is nullable for exactly this. Seeded so
    # the floor label's "no level" branch appears on screen rather than only in a unit test.
    @{ Building = "ANNEX"; Code = "MEZZ"; Name = "Mezzanine"; Level = $null },
    @{ Building = "PLANT"; Code = "B1"; Name = "Basement plant room"; Level = -1 },
    @{ Building = "PLANT"; Code = "GF"; Name = "Generator yard"; Level = 0 }
)
foreach ($f in $floors) {
    $building = Get-Building $f.Building
    if (-not $building) { continue }
    New-IfAbsent -Kind "floor" -Code "$($f.Building)/$($f.Code)" `
        -Find { (Invoke-Facilities -Path "/buildings/$($building.id)/floors") | Where-Object { $_.floorCode -eq $f.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/floors" -IdempotencyKey "seed-floor-$($f.Building)-$($f.Code)" -Body @{
                buildingId = $building.id; floorCode = $f.Code; name = $f.Name; levelNumber = $f.Level
            }
        } | Out-Null
}

function Get-Floor {
    param([string] $BuildingCode, [string] $FloorCode)
    $building = Get-Building $BuildingCode
    if (-not $building) { return $null }
    return (Invoke-Facilities -Path "/buildings/$($building.id)/floors") |
        Where-Object { $_.floorCode -eq $FloorCode } | Select-Object -First 1
}

# ---- 2. Spaces -----------------------------------------------------------------------------------
#
# The mix is the point. Two examination halls and a moot courtroom are what the readiness rules exist
# for; a plant room and a store are what they must *not* apply to, and a register showing only halls
# would make the space-type filter look decorative.
Write-Step "Spaces"

$spaces = @(
    @{ Building = "MAIN"; Floor = "GF"; Code = "HALL-A"; Name = "Moot Courtroom A"; Type = "MOOT_COURTROOM"; Capacity = 120; Area = 240; Bookable = $true; Exam = $true },
    @{ Building = "MAIN"; Floor = "GF"; Code = "HALL-B"; Name = "Lecture Hall B"; Type = "LECTURE_HALL"; Capacity = 80; Area = 160; Bookable = $true; Exam = $true },
    @{ Building = "MAIN"; Floor = "L1"; Code = "HALL-D"; Name = "Examination Hall D"; Type = "EXAMINATION_HALL"; Capacity = 200; Area = 400; Bookable = $true; Exam = $true },
    @{ Building = "MAIN"; Floor = "L1"; Code = "HALL-E"; Name = "Examination Hall E"; Type = "EXAMINATION_HALL"; Capacity = 180; Area = 360; Bookable = $true; Exam = $true },
    @{ Building = "MAIN"; Floor = "L2"; Code = "SEM-1"; Name = "Seminar Room 1"; Type = "MEETING_ROOM"; Capacity = 24; Area = 48; Bookable = $true; Exam = $false },
    @{ Building = "MAIN"; Floor = "L2"; Code = "SEM-2"; Name = "Seminar Room 2"; Type = "MEETING_ROOM"; Capacity = 24; Area = 48; Bookable = $true; Exam = $false },
    @{ Building = "ANNEX"; Floor = "GF"; Code = "REG-1"; Name = "Registry"; Type = "OFFICE"; Capacity = 12; Area = 60; Bookable = $false; Exam = $false },
    @{ Building = "ANNEX"; Floor = "GF"; Code = "LIB-1"; Name = "Law Library"; Type = "LIBRARY"; Capacity = 90; Area = 320; Bookable = $true; Exam = $false },
    @{ Building = "ANNEX"; Floor = "MEZZ"; Code = "STORE-1"; Name = "Records store"; Type = "STORE"; Capacity = $null; Area = 40; Bookable = $false; Exam = $false },
    @{ Building = "PLANT"; Floor = "B1"; Code = "PLANT-1"; Name = "Chiller room"; Type = "PLANT_ROOM"; Capacity = $null; Area = 85; Bookable = $false; Exam = $false }
)
foreach ($s in $spaces) {
    $floor = Get-Floor $s.Building $s.Floor
    if (-not $floor) { continue }
    New-IfAbsent -Kind "space" -Code $s.Code `
        -Find { (Invoke-Facilities -Path "/rooms?siteCode=$Site") | Where-Object { $_.roomCode -eq $s.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/rooms" -IdempotencyKey "seed-room-$($s.Code)" -Body @{
                floorId = $floor.id; roomCode = $s.Code; name = $s.Name; spaceType = $s.Type
                capacity = $s.Capacity; areaSqm = $s.Area; costCentre = "CC-ESTATE"
                bookable = $s.Bookable; examinationCapable = $s.Exam
            }
        } | Out-Null
}
$allSpaces = Invoke-Facilities -Path "/rooms?siteCode=$Site"
function Get-Space { param([string] $Code) $allSpaces | Where-Object { $_.roomCode -eq $Code } | Select-Object -First 1 }

# ---- 3. Facility assets ---------------------------------------------------------------------------
#
# Criticality and condition are seeded together on purpose. A critical asset out of service raises a
# CRITICAL readiness blocker, which is the one thing that forbids a space being READY - so the two
# rows below marked OUT_OF_SERVICE and UNDER_MAINTENANCE are what makes the dashboard's blocked-space
# and critical-blocker counts non-zero without anybody having to stage it during the take.
Write-Step "Facility assets"

$assets = @(
    @{ Code = "GEN-01"; Name = "Standby generator, north yard"; Category = "GENERATOR"; Criticality = "CRITICAL"; Space = $null; Location = "Generator yard, Plant House"; Make = "Perkins"; Model = "P400"; Serial = "PK-400-11842"; Interval = 90; Status = "OPERATIONAL" },
    @{ Code = "CHW-01"; Name = "Chiller 1"; Category = "HVAC"; Criticality = "CRITICAL"; Space = "PLANT-1"; Location = $null; Make = "Carrier"; Model = "30XA"; Serial = "CR-30XA-0071"; Interval = 120; Status = "OUT_OF_SERVICE" },
    @{ Code = "CHW-02"; Name = "Chiller 2"; Category = "HVAC"; Criticality = "HIGH"; Space = "PLANT-1"; Location = $null; Make = "Carrier"; Model = "30XA"; Serial = "CR-30XA-0072"; Interval = 120; Status = "OPERATIONAL" },
    @{ Code = "UPS-01"; Name = "Registry UPS"; Category = "UPS"; Criticality = "HIGH"; Space = "REG-1"; Location = $null; Make = "APC"; Model = "Symmetra"; Serial = "APC-SYM-2210"; Interval = 180; Status = "DEGRADED" },
    @{ Code = "LIFT-01"; Name = "Main Block passenger lift"; Category = "LIFT"; Criticality = "HIGH"; Space = $null; Location = "Main Block core"; Make = "Kone"; Model = "MonoSpace"; Serial = "KN-MS-4417"; Interval = 60; Status = "UNDER_MAINTENANCE" },
    @{ Code = "FIRE-01"; Name = "Main Block fire panel"; Category = "FIRE_SYSTEM"; Criticality = "CRITICAL"; Space = $null; Location = "Main Block reception"; Make = "Notifier"; Model = "NFS2-3030"; Serial = "NF-3030-0912"; Interval = 180; Status = "OPERATIONAL" },
    @{ Code = "AV-HALLD"; Name = "Examination Hall D projector and PA"; Category = "AUDIO_VISUAL"; Criticality = "MEDIUM"; Space = "HALL-D"; Location = $null; Make = "Epson"; Model = "EB-L735U"; Serial = "EP-L735-3318"; Interval = 365; Status = "OPERATIONAL" },
    @{ Code = "WTR-01"; Name = "Booster pump set"; Category = "WATER_SYSTEM"; Criticality = "MEDIUM"; Space = "PLANT-1"; Location = $null; Make = "Grundfos"; Model = "Hydro MPC"; Serial = "GF-MPC-5521"; Interval = 150; Status = "OPERATIONAL" }
)
foreach ($a in $assets) {
    $roomId = if ($a.Space) { (Get-Space $a.Space).id } else { $null }
    $asset = New-IfAbsent -Kind "asset" -Code $a.Code `
        -Find { (Invoke-Facilities -Path "/assets?siteCode=$Site&size=200").items | Where-Object { $_.assetCode -eq $a.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/assets" -IdempotencyKey "seed-asset-$($a.Code)" -Body @{
                siteCode = $Site; assetCode = $a.Code; name = $a.Name; category = $a.Category
                criticality = $a.Criticality; roomId = $roomId; locationCode = $a.Location
                manufacturer = $a.Make; modelNumber = $a.Model; serialNumber = $a.Serial
                installedOn = (Days -420); warrantyExpiresOn = (Days 320)
                serviceIntervalDays = $a.Interval; custodian = "estates.team"
            }
        }

    # The condition is a separate call because it is a separate act: the service re-derives the
    # readiness of the space the asset sits in, and that derivation is what the dashboard reports.
    if ($a.Status -ne "OPERATIONAL" -and $asset.operationalStatus -eq "OPERATIONAL") {
        Invoke-Facilities -Method Patch -Path "/assets/$($asset.id)/status" -Body @{
            operationalStatus = $a.Status
            notes             = "Recorded during the estate condition survey."
            expectedVersion   = $asset.metadata.version
        } | Out-Null
        Write-Host "      condition -> $($a.Status)" -ForegroundColor DarkYellow
    }
}

# ---- 4. Device references -------------------------------------------------------------------------
#
# These start UNKNOWN and stay there: no vendor feed is configured locally, and the register says so
# on screen. That is the honest state to film - a seeded ONLINE would be this service asserting an
# observation nothing has made.
Write-Step "Device references"

$devices = @(
    @{ Code = "CAM-01"; Name = "Main entrance, external"; Type = "CCTV_CAMERA"; Space = $null; Location = "Main Block entrance"; Vendor = "Acme Security"; Ref = "ACME-CAM-0001" },
    @{ Code = "CAM-02"; Name = "Examination Hall D, front"; Type = "CCTV_CAMERA"; Space = "HALL-D"; Location = $null; Vendor = "Acme Security"; Ref = "ACME-CAM-0002" },
    @{ Code = "CAM-03"; Name = "Examination Hall E, front"; Type = "CCTV_CAMERA"; Space = "HALL-E"; Location = $null; Vendor = "Acme Security"; Ref = "ACME-CAM-0003" },
    @{ Code = "CAM-04"; Name = "Perimeter, east fence"; Type = "CCTV_CAMERA"; Space = $null; Location = "East perimeter"; Vendor = "Acme Security"; Ref = "ACME-CAM-0004" },
    @{ Code = "ACR-01"; Name = "Registry door reader"; Type = "ACCESS_READER"; Space = "REG-1"; Location = $null; Vendor = "Gateway Access"; Ref = "GW-RD-1180" },
    @{ Code = "ACR-02"; Name = "Records store reader"; Type = "ACCESS_READER"; Space = "STORE-1"; Location = $null; Vendor = "Gateway Access"; Ref = "GW-RD-1181" },
    @{ Code = "BIO-01"; Name = "Examination Hall D biometric station"; Type = "BIOMETRIC_READER"; Space = "HALL-D"; Location = $null; Vendor = "Gateway Access"; Ref = "GW-BIO-0442" },
    @{ Code = "FP-01"; Name = "Main Block fire panel"; Type = "FIRE_PANEL"; Space = $null; Location = "Main Block reception"; Vendor = "Notifier Ghana"; Ref = "NF-PANEL-0912" },
    @{ Code = "IOT-01"; Name = "Chiller room temperature sensor"; Type = "IOT_SENSOR"; Space = "PLANT-1"; Location = $null; Vendor = "Sensorix"; Ref = "SX-T-7741" },
    @{ Code = "INT-01"; Name = "Annex intrusion panel"; Type = "INTRUSION_PANEL"; Space = $null; Location = "Annex plant cupboard"; Vendor = "Acme Security"; Ref = "ACME-INT-0022" }
)
foreach ($d in $devices) {
    $roomId = if ($d.Space) { (Get-Space $d.Space).id } else { $null }
    New-IfAbsent -Kind "device" -Code $d.Code `
        -Find { (Invoke-Facilities -Path "/device-references?siteCode=$Site") | Where-Object { $_.deviceCode -eq $d.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/device-references" -IdempotencyKey "seed-device-$($d.Code)" -Body @{
                siteCode = $Site; deviceCode = $d.Code; name = $d.Name; type = $d.Type
                roomId = $roomId; locationCode = $d.Location; vendor = $d.Vendor; externalReference = $d.Ref
            }
        } | Out-Null
}
$allDevices = Invoke-Facilities -Path "/device-references?siteCode=$Site"
function Get-Device { param([string] $Code) $allDevices | Where-Object { $_.deviceCode -eq $Code } | Select-Object -First 1 }

# ---- 5. Zones and what they cover ------------------------------------------------------------------
#
# A zone with no members resolves to nobody, so seeding zones without membership would demonstrate
# the failure the screen exists to prevent.
Write-Step "Zones"

$zones = @(
    @{ Code = "ZONE-NORTH"; Name = "North wing"; Purpose = "Evacuation zone for the north stair core"; Members = @(@{ Type = "ROOM"; Code = "HALL-A" }, @{ Type = "ROOM"; Code = "HALL-B" }, @{ Type = "DEVICE"; Code = "CAM-01" }) },
    @{ Code = "ZONE-EXAM"; Name = "Examination floor"; Purpose = "Addressed by examination-period broadcasts and life-safety alarms"; Members = @(@{ Type = "ROOM"; Code = "HALL-D" }, @{ Type = "ROOM"; Code = "HALL-E" }, @{ Type = "DEVICE"; Code = "BIO-01" }, @{ Type = "DEVICE"; Code = "CAM-02" }) },
    @{ Code = "ZONE-ANNEX"; Name = "Annex"; Purpose = "Registry, library and records store"; Members = @(@{ Type = "BUILDING"; Code = "ANNEX" }, @{ Type = "DEVICE"; Code = "ACR-01" }) },
    @{ Code = "ZONE-PLANT"; Name = "Plant House"; Purpose = "Restricted plant. Not part of a general evacuation broadcast."; Members = @(@{ Type = "BUILDING"; Code = "PLANT" }, @{ Type = "ROOM"; Code = "PLANT-1" }) }
)
foreach ($z in $zones) {
    $zone = New-IfAbsent -Kind "zone" -Code $z.Code `
        -Find { (Invoke-Facilities -Path "/zones?siteCode=$Site") | Where-Object { $_.zoneCode -eq $z.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/zones" -IdempotencyKey "seed-zone-$($z.Code)" -Body @{
                siteCode = $Site; zoneCode = $z.Code; name = $z.Name; purpose = $z.Purpose; parentZoneId = $null
            }
        }

    $current = Invoke-Facilities -Path "/zones/$($zone.id)/members"
    foreach ($m in $z.Members) {
        $memberId = switch ($m.Type) {
            "ROOM" { (Get-Space $m.Code).id }
            "DEVICE" { (Get-Device $m.Code).id }
            "BUILDING" { (Get-Building $m.Code).id }
        }
        if (-not $memberId) { continue }
        if ($current | Where-Object { $_.memberId -eq $memberId }) { continue }
        Invoke-Facilities -Method Post -Path "/zones/$($zone.id)/members" -Body @{
            memberType = $m.Type; memberId = $memberId
        } | Out-Null
        Write-Host "      + $($m.Type) $($m.Code)" -ForegroundColor DarkGreen
    }
}

# ---- 6. Readiness checklists ------------------------------------------------------------------------
#
# Three, and the applicability is what makes them worth showing: the baseline applies to everything,
# the examination-hall one narrows by space type, and the examination-mode one narrows by mode. The
# most specific match wins when an assessment is taken, which is a rule you can only demonstrate if
# there is more than one checklist to choose between.
Write-Step "Readiness checklists"

$checklists = @(
    @{
        Code = "BASELINE"; Name = "Baseline space readiness"; SpaceType = $null; Mode = $null
        Description = "Applies to any space in any mode. The floor everything else builds on."
        Items = @(
            @{ Code = "LIGHTING"; Description = "All lighting is working and the space is evenly lit."; Severity = "MAJOR"; Mandatory = $true; Weight = 2 },
            @{ Code = "CLEANLINESS"; Description = "The space is clean and clear of obstruction."; Severity = "MINOR"; Mandatory = $false; Weight = 1 },
            @{ Code = "POWER"; Description = "Sockets and fixed power are live and undamaged."; Severity = "MAJOR"; Mandatory = $true; Weight = 2 },
            @{ Code = "EGRESS"; Description = "Exit routes are unobstructed and exit signage is lit."; Severity = "CRITICAL"; Mandatory = $true; Weight = 3 }
        )
    },
    @{
        Code = "EXAM-HALL-STD"; Name = "Examination hall standard"; SpaceType = "EXAMINATION_HALL"; Mode = $null
        Description = "Everything the baseline asks, plus what an examination hall needs on any day."
        Items = @(
            @{ Code = "LIGHTING"; Description = "All lighting is working and the hall is evenly lit."; Severity = "MAJOR"; Mandatory = $true; Weight = 2 },
            @{ Code = "EGRESS"; Description = "Exit routes are unobstructed and exit signage is lit."; Severity = "CRITICAL"; Mandatory = $true; Weight = 3 },
            @{ Code = "SEATING"; Description = "Desks are laid out at examination spacing and are stable."; Severity = "MAJOR"; Mandatory = $true; Weight = 2 },
            @{ Code = "VENTILATION"; Description = "Ventilation or cooling is working across the hall."; Severity = "MAJOR"; Mandatory = $false; Weight = 2 },
            @{ Code = "CLOCK"; Description = "The wall clock is working and visible from every seat."; Severity = "MINOR"; Mandatory = $false; Weight = 1 }
        )
    },
    @{
        Code = "EXAM-MODE"; Name = "Examination mode - hall sign-off"; SpaceType = "EXAMINATION_HALL"; Mode = "EXAMINATION"
        Description = "The stricter sign-off, applied only while the centre has declared examination mode."
        Items = @(
            @{ Code = "LIGHTING"; Description = "All lighting is working and the hall is evenly lit."; Severity = "CRITICAL"; Mandatory = $true; Weight = 3 },
            @{ Code = "EGRESS"; Description = "Exit routes are unobstructed and exit signage is lit."; Severity = "CRITICAL"; Mandatory = $true; Weight = 3 },
            @{ Code = "SEATING"; Description = "Desks are laid out at examination spacing and numbered."; Severity = "CRITICAL"; Mandatory = $true; Weight = 3 },
            @{ Code = "CCTV"; Description = "Invigilation cameras are in place and unobstructed."; Severity = "CRITICAL"; Mandatory = $true; Weight = 3 },
            @{ Code = "SIGNAL"; Description = "Mobile signal blockers or collection points are in place."; Severity = "MAJOR"; Mandatory = $true; Weight = 2 },
            @{ Code = "WATER"; Description = "Drinking water is available and sanitary facilities are open."; Severity = "MINOR"; Mandatory = $false; Weight = 1 }
        )
    }
)
foreach ($c in $checklists) {
    New-IfAbsent -Kind "checklist" -Code $c.Code `
        -Find { (Invoke-Facilities -Path "/readiness/checklists?siteCode=$Site") | Where-Object { $_.checklistCode -eq $c.Code } | Select-Object -First 1 } `
        -Create {
            $items = @()
            $order = 0
            foreach ($i in $c.Items) {
                $items += @{
                    itemCode = $i.Code; description = $i.Description; severityIfFailed = $i.Severity
                    mandatory = $i.Mandatory; weight = $i.Weight; sortOrder = $order
                }
                $order++
            }
            Invoke-Facilities -Method Post -Path "/readiness/checklists" -IdempotencyKey "seed-checklist-$($c.Code)" -Body @{
                siteCode = $Site; checklistCode = $c.Code; name = $c.Name; description = $c.Description
                spaceType = $c.SpaceType; operatingMode = $c.Mode; items = $items
            }
        } | Out-Null
}

# ---- 7. Readiness assessments ------------------------------------------------------------------------
#
# The checklist is named on every request, and that is a correction rather than a preference.
#
# The first version left `checklistId` null and let the service resolve it, which is the nicer
# demonstration - and then built the answers from whichever checklist *this script* guessed the
# service would pick. The two disagreed. Every item the script did not answer counts as **failed**,
# by design, so six assessments intended to pass came back carrying critical EGRESS blockers and the
# dashboard reported six blocked spaces that were not blocked. An answer set has to be built against
# the checklist it will actually be graded on, and the only way to guarantee that is to name it.
#
# One assessment fails a mandatory critical item on purpose. That blocker is what puts a hall on the
# examination-risk table, and it has to be real for the drilldown to have anything behind it.
Write-Step "Readiness assessments"

$assessments = @(
    @{ Space = "HALL-A"; Checklist = "BASELINE"; Fail = @(); Notes = "Routine pre-term check. Everything in order." },
    @{ Space = "HALL-B"; Checklist = "BASELINE"; Fail = @("CLEANLINESS"); Notes = "Fit for use. Cleaning raised separately." },
    @{ Space = "HALL-D"; Checklist = "EXAM-HALL-STD"; Fail = @(); Notes = "Checked ahead of the examination period." },
    @{ Space = "HALL-E"; Checklist = "EXAM-HALL-STD"; Fail = @("EGRESS", "SEATING"); Notes = "East exit obstructed by stacked desks. Hall not usable until cleared." },
    @{ Space = "LIB-1"; Checklist = "BASELINE"; Fail = @(); Notes = "Routine check." },
    @{ Space = "SEM-1"; Checklist = "BASELINE"; Fail = @("POWER"); Notes = "Two dead sockets on the north wall." },
    @{ Space = "REG-1"; Checklist = "BASELINE"; Fail = @(); Notes = "Routine check." },
    @{ Space = "SEM-2"; Checklist = "BASELINE"; Fail = @(); Notes = "Routine check." }
)
$allChecklists = Invoke-Facilities -Path "/readiness/checklists?siteCode=$Site"
$existingAssessments = Invoke-Facilities -Path "/readiness/assessments?siteCode=$Site&limit=200"
foreach ($a in $assessments) {
    $space = Get-Space $a.Space
    if (-not $space) { continue }
    $checklist = $allChecklists | Where-Object { $_.checklistCode -eq $a.Checklist } | Select-Object -First 1
    if (-not $checklist) { continue }

    # Keyed on room *and* checklist rather than room alone, so a re-run after this correction takes
    # a fresh assessment against the right checklist instead of leaving the wrong one standing.
    if ($existingAssessments | Where-Object { $_.roomId -eq $space.id -and $_.checklistCode -eq $a.Checklist }) {
        Note-Existing "assessment"; Write-Host "    = $($a.Space)" -ForegroundColor DarkGray; continue
    }

    $answers = @()
    foreach ($item in $checklist.items) {
        $passed = -not ($a.Fail -contains $item.itemCode)
        $answers += @{
            itemCode = $item.itemCode
            passed   = $passed
            comment  = if ($passed) { $null } else { "Failed at the pre-term check." }
        }
    }
    Invoke-Facilities -Method Post -Path "/readiness/assessments" -IdempotencyKey "seed-assessment-$($a.Space)-$($a.Checklist)" -Body @{
        roomId = $space.id; checklistId = $checklist.id; answers = $answers; notes = $a.Notes
    } | Out-Null
    Note-Created "assessment"
    Write-Host "    + $($a.Space) against $($a.Checklist)" -ForegroundColor Green
}

# ---- 8. Maintenance vendors --------------------------------------------------------------------------
#
# One contract has already expired. The service answers `assignable: false` with a reason on that
# vendor, and the assignment dialog reads it - so the register has something to show beyond three
# rows that are all fine.
Write-Step "Maintenance vendors"

$vendors = @(
    @{ Code = "ACME-FM"; Name = "Acme Facilities Ltd"; Spec = "HVAC, chillers and building services"; Contact = "Kojo Mensah"; Email = "kojo@acmefm.example.gh"; Phone = "+233 30 123 4567"; Hours = 4; Ref = "PO-2026-0114"; Expires = (Days 240) },
    @{ Code = "POWERLINE"; Name = "Powerline Engineering"; Spec = "Generators, UPS and power distribution"; Contact = "Ama Owusu"; Email = "ama@powerline.example.gh"; Phone = "+233 30 987 6543"; Hours = 2; Ref = "PO-2026-0087"; Expires = (Days 90) },
    @{ Code = "LIFTCO"; Name = "LiftCo Ghana"; Spec = "Lifts and hoists"; Contact = "Yaw Boateng"; Email = "yaw@liftco.example.gh"; Phone = "+233 30 555 0110"; Hours = 8; Ref = "PO-2025-0431"; Expires = (Days -30) },
    @{ Code = "SAFEGUARD"; Name = "Safeguard Fire Services"; Spec = "Fire detection, suppression and extinguishers"; Contact = "Efua Asante"; Email = "efua@safeguard.example.gh"; Phone = "+233 30 222 8890"; Hours = 6; Ref = "PO-2026-0203"; Expires = (Days 400) }
)
foreach ($v in $vendors) {
    New-IfAbsent -Kind "vendor" -Code $v.Code `
        -Find { (Invoke-Facilities -Path "/maintenance/vendors?siteCode=$Site") | Where-Object { $_.vendorCode -eq $v.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/maintenance/vendors" -IdempotencyKey "seed-vendor-$($v.Code)" -Body @{
                siteCode = $Site; vendorCode = $v.Code; name = $v.Name; specialisation = $v.Spec
                contactName = $v.Contact; contactEmail = $v.Email; contactPhone = $v.Phone
                responseHours = $v.Hours; contractReference = $v.Ref; contractExpiresOn = $v.Expires
                externalVendorId = "PROC-$($v.Code)"
            }
        } | Out-Null
}
$allVendors = Invoke-Facilities -Path "/maintenance/vendors?siteCode=$Site"
function Get-Vendor { param([string] $Code) $allVendors | Where-Object { $_.vendorCode -eq $Code } | Select-Object -First 1 }

# ---- 9. Preventive schedules --------------------------------------------------------------------------
#
# One is due in the past, so the generation run has something to raise. A register of schedules that
# are all comfortably in the future demonstrates the table and none of the behaviour.
Write-Step "Preventive schedules"

$schedules = @(
    @{ Code = "GEN-QUARTERLY"; Name = "Generator quarterly service"; Asset = "GEN-01"; Interval = 90; Lead = 14; Priority = "HIGH"; Type = "PREVENTIVE"; Due = (Days -3); Description = "Load bank test, oil and filters, fuel polish." },
    @{ Code = "CHW-SEMI"; Name = "Chiller half-yearly service"; Asset = "CHW-02"; Interval = 180; Lead = 21; Priority = "HIGH"; Type = "PREVENTIVE"; Due = (Days 40); Description = "Condenser clean, refrigerant check, controls verification." },
    @{ Code = "LIFT-MONTHLY"; Name = "Lift monthly inspection"; Asset = "LIFT-01"; Interval = 30; Lead = 5; Priority = "CRITICAL"; Type = "PREVENTIVE"; Due = (Days 12); Description = "Statutory inspection. Certificate filed as evidence." },
    @{ Code = "FIRE-BIANNUAL"; Name = "Fire panel half-yearly test"; Asset = "FIRE-01"; Interval = 180; Lead = 14; Priority = "CRITICAL"; Type = "PREVENTIVE"; Due = (Days 60); Description = "Full device test, battery check, log download." },
    @{ Code = "UPS-ANNUAL"; Name = "UPS annual service"; Asset = "UPS-01"; Interval = 365; Lead = 30; Priority = "MEDIUM"; Type = "PREVENTIVE"; Due = (Days 150); Description = "Battery capacity test and firmware review." }
)
$allAssets = (Invoke-Facilities -Path "/assets?siteCode=$Site&size=200").items
function Get-Asset { param([string] $Code) $allAssets | Where-Object { $_.assetCode -eq $Code } | Select-Object -First 1 }

foreach ($s in $schedules) {
    $asset = Get-Asset $s.Asset
    if (-not $asset) { continue }
    New-IfAbsent -Kind "schedule" -Code $s.Code `
        -Find { (Invoke-Facilities -Path "/maintenance/schedules?siteCode=$Site") | Where-Object { $_.scheduleCode -eq $s.Code } | Select-Object -First 1 } `
        -Create {
            Invoke-Facilities -Method Post -Path "/maintenance/schedules" -IdempotencyKey "seed-schedule-$($s.Code)" -Body @{
                siteCode = $Site; scheduleCode = $s.Code; name = $s.Name; description = $s.Description
                assetId = $asset.id; intervalDays = $s.Interval; leadTimeDays = $s.Lead
                priority = $s.Priority; workOrderType = $s.Type; firstDueOn = $s.Due
            }
        } | Out-Null
}

if ($SkipWorkflow) {
    Write-Host "`n-SkipWorkflow: leaving faults and work orders alone." -ForegroundColor Yellow
} else {

# ---- 10. Faults and the work-order queue -----------------------------------------------------------
#
# Seeded across the whole lifecycle rather than as a pile of open rows, because the queue screen's
# columns - assignee, SLA, escalation level, evidence shortfall - only say anything when the records
# are in different states. Reported by a requester, so `reported_by` is theirs and the personal
# portal narrows to them correctly.
Write-Step "Faults"

$faults = @(
    @{ Key = "chiller"; Space = "PLANT-1"; Asset = "CHW-01"; Title = "Chiller 1 has tripped and will not restart"; Description = "Compressor fault on the panel. Hall D and E cooling is on one chiller."; Category = "HVAC"; Priority = "CRITICAL"; Workflow = "close" },
    # No room: the lift is in a core rather than a space, which is exactly why the service accepts a
    # location code instead - and refuses a fault that carries neither.
    @{ Key = "lift"; Space = $null; Location = "Main Block lift core"; Asset = "LIFT-01"; Title = "Passenger lift stopping short of the first floor"; Description = "Levelling is out by about 40mm. Trip hazard."; Category = "LIFT"; Priority = "HIGH"; Workflow = "complete" },
    @{ Key = "sockets"; Space = "SEM-1"; Asset = $null; Title = "Two dead sockets on the north wall"; Description = "Found during the pre-term readiness check."; Category = "ELECTRICAL"; Priority = "MEDIUM"; Workflow = "progress" },
    @{ Key = "exit"; Space = "HALL-E"; Asset = $null; Title = "East exit obstructed by stacked desks"; Description = "Desks stacked against the exit door since the last examination."; Category = "BUILDING_FABRIC"; Priority = "HIGH"; Workflow = "assign" },
    @{ Key = "projector"; Space = "HALL-D"; Asset = "AV-HALLD"; Title = "Projector lamp warning on the hall display"; Description = "Lamp hours warning. Still projecting."; Category = "AUDIO_VISUAL"; Priority = "LOW"; Workflow = "none" },
    @{ Key = "leak"; Space = "LIB-1"; Asset = $null; Title = "Water staining on the library ceiling"; Description = "Spreading slowly. Books moved off the affected shelving."; Category = "PLUMBING"; Priority = "MEDIUM"; Workflow = "none" },
    @{ Key = "door"; Space = "REG-1"; Asset = $null; Title = "Registry door closer slamming"; Description = "Closer has lost damping."; Category = "BUILDING_FABRIC"; Priority = "LOW"; Workflow = "none" }
)

$existingFaults = Invoke-Facilities -Path "/faults?siteCode=$Site&limit=200"
$raised = @{}
foreach ($f in $faults) {
    $found = $existingFaults | Where-Object { $_.title -eq $f.Title } | Select-Object -First 1
    if ($found) {
        Note-Existing "fault"; Write-Host "    = $($f.Key)" -ForegroundColor DarkGray
        $raised[$f.Key] = $found
        continue
    }
    $roomId = if ($f.Space) { (Get-Space $f.Space).id } else { $null }
    $assetId = if ($f.Asset) { (Get-Asset $f.Asset).id } else { $null }
    $location = if ($f.ContainsKey("Location")) { $f.Location } else { $null }
    $raised[$f.Key] = Invoke-Facilities -Actor $Requester -Method Post -Path "/faults" -IdempotencyKey "seed-fault-$($f.Key)" -Body @{
        siteCode = $Site; roomId = $roomId; locationCode = $location; assetId = $assetId
        title = $f.Title; description = $f.Description; category = $f.Category; priority = $f.Priority
    }
    Note-Created "fault"
    Write-Host "    + $($f.Key) [$($f.Priority)]" -ForegroundColor Green
}

Write-Step "Triage and the work-order queue"

foreach ($f in $faults) {
    if ($f.Workflow -eq "none") { continue }
    $fault = $raised[$f.Key]
    if (-not $fault) { continue }

    # Triage confirms the priority and starts the SLA clock. It is the only place priority may change.
    if ($fault.status -eq "REPORTED") {
        $fault = Invoke-Facilities -Method Patch -Path "/faults/$($fault.id)/triage" -Body @{
            priority = $f.Priority
            notes    = "Confirmed on the estates round."
            expectedVersion = $fault.metadata.version
        }
        Write-Host "    triaged $($f.Key)" -ForegroundColor DarkGreen
    }
    $vendorCode = switch ($f.Category) {
        "HVAC" { "ACME-FM" }
        "LIFT" { "LIFTCO" }
        "ELECTRICAL" { "POWERLINE" }
        default { $null }
    }
    # LiftCo's contract has expired, so the service refuses to assign to them. Raising the order
    # unassigned is the honest outcome and leaves the queue showing a real unassigned row.
    $vendor = if ($vendorCode) { Get-Vendor $vendorCode } else { $null }
    $vendorId = if ($vendor -and $vendor.assignable) { $vendor.id } else { $null }

    if ($fault.workOrderId) {
        $order = Invoke-Facilities -Path "/work-orders/$($fault.workOrderId)"
        Note-Existing "work order"
        Write-Host "    = work order for $($f.Key) [$($order.status)]" -ForegroundColor DarkGray
    } else {
        $order = Invoke-Facilities -Method Post -Path "/work-orders/from-fault" -IdempotencyKey "seed-wo-$($f.Key)" -Body @{
            facilityFaultId = $fault.id; vendorId = $vendorId; assignTo = $null
        }
        Note-Created "work order"
        Write-Host "    + work order for $($f.Key)" -ForegroundColor Green
    }

    <#
        Drive the order forward from wherever it actually is, rather than from where a fresh run
        would have left it.

        The first version skipped any fault that already had an order, which is fine until a run
        dies halfway - and the first one did, on the evidence call. That left a work order stranded
        at COMPLETED with no way for a re-run to finish it, so the "closed" row the demo needs never
        appeared and nothing said why. Reading the current status and applying only the missing
        transitions makes the script resumable, and it is also the honest way round: the service's
        state machine decides what is legal, so asking it where the record is beats assuming.
    #>
    if ($f.Workflow -ne "assign") {
        if ($order.status -eq "OPEN") {
            $order = Invoke-Facilities -Method Patch -Path "/work-orders/$($order.id)/assignment" -Body @{
                assignedTo = "yaw.technician"; vendorId = $vendorId; expectedVersion = $order.metadata.version
            }
            Write-Host "      assigned" -ForegroundColor DarkGreen
        }
        if ($order.status -eq "ASSIGNED") {
            $order = Invoke-Facilities -Method Patch -Path "/work-orders/$($order.id)/start" -Body @{
                notes = "Attended."; expectedVersion = $order.metadata.version
            }
            Write-Host "      started" -ForegroundColor DarkGreen
        }
    }

    if ($f.Workflow -eq "complete" -or $f.Workflow -eq "close") {
        if ($order.status -eq "IN_PROGRESS") {
            $order = Invoke-Facilities -Method Patch -Path "/work-orders/$($order.id)/completion" -Body @{
                notes = "Work finished, awaiting sign-off."; expectedVersion = $order.metadata.version
            }
            Write-Host "      completed" -ForegroundColor DarkGreen
        }
    }

    if ($f.Workflow -eq "complete" -and $order.status -eq "COMPLETED") {
        Write-Host "      left at COMPLETED - closing it needs evidence, which is worth showing" -ForegroundColor DarkYellow
    }

    if ($f.Workflow -eq "close" -and $order.status -eq "COMPLETED") {
        # Closing a work order above the evidence threshold is refused without evidence attached.
        # Rather than dodging that with a low-priority fault, the evidence is attached properly -
        # which also gives the evidence register something to show.
        $attached = Invoke-Facilities -Path "/work-orders/$($order.id)/evidence"
        if ($order.evidenceRequired -gt 0 -and @($attached).Count -lt $order.evidenceRequired) {
            $reference = "s3://clet-evidence/$Site/$($order.id)/service-report.pdf"
            Invoke-Facilities -Method Post -Path "/work-orders/$($order.id)/evidence" -IdempotencyKey "seed-evidence-$($f.Key)" -Body @{
                evidenceType   = "SERVICE_REPORT"
                fileReference  = $reference
                fileName       = "service-report.pdf"
                mediaType      = "application/pdf"
                sizeBytes      = 284119
                contentHash    = (Get-Digest $reference)
                retentionClass = "OPERATIONAL"
                notes          = "Signed service report from the contractor."
            } | Out-Null
            Write-Host "      + evidence" -ForegroundColor DarkGreen
            $order = Invoke-Facilities -Path "/work-orders/$($order.id)"
        }

        Invoke-Facilities -Method Patch -Path "/work-orders/$($order.id)/closure" -Body @{
            closureNotes = "Compressor contactor replaced and the unit returned to service. Verified on load."
            expectedVersion = $order.metadata.version
        } | Out-Null
        Write-Host "      closed" -ForegroundColor DarkGreen
    }
}

<#
    Faults this script did not raise, dismissed on request.

    A development database accumulates them - "Fire is in the building" and a vehicle-service fault
    that belongs to another system are both sitting in this one - and each carries a blocker that
    shows up on the dashboard as an estate problem nobody can explain on camera.

    Off by default and deliberately so: dismissing somebody's data because a script did not recognise
    it is not a decision a seed should take on its own. Pass -DismissStrayFaults when the database is
    known to be a scratch one, and every dismissal is named as it happens.
#>
if ($DismissStrayFaults) {
    Write-Step "Dismissing faults this script did not raise"
    $seeded = $faults | ForEach-Object { $_.Title }
    $open = Invoke-Facilities -Path "/faults?siteCode=$Site&openOnly=true&limit=200"
    foreach ($stray in $open) {
        if ($seeded -contains $stray.title) { continue }
        Invoke-Facilities -Method Patch -Path "/faults/$($stray.id)/dismissal" -Body @{
            outcome            = "CANCELLED"
            reason             = "Left over from hand testing. Cancelled while preparing the demonstration estate."
            duplicateOfFaultId = $null
            expectedVersion    = $stray.metadata.version
        } | Out-Null
        Note-Created "dismissal"
        Write-Host "    - $($stray.title)" -ForegroundColor DarkYellow
    }
}

}  # -SkipWorkflow

# ---- Summary ------------------------------------------------------------------------------------
Write-Host "`nDone." -ForegroundColor Cyan
$kinds = ($script:Created.Keys + $script:Existing.Keys) | Sort-Object -Unique
foreach ($kind in $kinds) {
    $new = if ($script:Created.ContainsKey($kind)) { $script:Created[$kind] } else { 0 }
    $had = if ($script:Existing.ContainsKey($kind)) { $script:Existing[$kind] } else { 0 }
    Write-Host ("  {0,-12} {1} created, {2} already there" -f $kind, $new, $had)
}
Write-Host "`nSign in at $FacilitiesUrl/home/ as facilitiesdirector@clet.gh" -ForegroundColor Cyan
Write-Host "Two controls that account will not see, both by design and both recorded in the gap report:" -ForegroundColor DarkGray
Write-Host "  - Configuration edit      needs FACILITIES_CONFIG_MANAGE" -ForegroundColor DarkGray
Write-Host "  - Verify the audit chain  needs FACILITIES_AUDIT_INTEGRITY_CHECK" -ForegroundColor DarkGray
Write-Host "Film those two as sfladmin@clet.gh, or leave them out of the take." -ForegroundColor DarkGray
