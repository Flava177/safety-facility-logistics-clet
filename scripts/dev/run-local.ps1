param(
    [string] $Profile = "local",
    [string] $DbUrl = "jdbc:postgresql://localhost:5434/sfl_java",
    [string] $DbUsername = "postgres",
    [string] $Port = "8081"
)

$ErrorActionPreference = "Stop"

$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")

if (-not $env:SFL_DB_PASSWORD) {
    $securePassword = Read-Host "PostgreSQL password for $DbUsername" -AsSecureString
    $passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $env:SFL_DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    }
    finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    }
}

$env:SPRING_PROFILES_ACTIVE = $Profile
$env:SFL_DB_URL = $DbUrl
$env:SFL_DB_USERNAME = $DbUsername
$env:SFL_PORT = $Port
# Load-bearing since A1: absent now means SECURE. Removing this line makes the service demand a
# token, which is correct everywhere except a developer laptop with no Keycloak running.
$env:SFL_SECURITY_ENABLED = "false"

Write-Host "Starting SFL Spring Boot on http://localhost:$Port with profile '$Profile'..."
Set-Location $repoRoot
& .\mvnw.cmd spring-boot:run
