[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string]$EnvFile,

    [Parameter(Mandatory = $true)]
    [string]$SeedSql,

    [Parameter(Mandatory = $true)]
    [string]$Container
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $EnvFile)) {
    Write-Host "Missing env file: $EnvFile"
    exit 1
}

$lines = Get-Content $EnvFile
foreach ($line in $lines) {
    if ($line.StartsWith('#')) { continue }
    $idx = $line.IndexOf('=')
    if ($idx -gt 0) {
        $key = $line.Substring(0, $idx).Trim()
        $val = $line.Substring($idx + 1).Trim()
        [Environment]::SetEnvironmentVariable($key, $val, 'Process')
    }
}

$dbName = [Environment]::GetEnvironmentVariable('SCADA_MOBILE_POSTGRES_DB')
$dbUser = [Environment]::GetEnvironmentVariable('SCADA_MOBILE_POSTGRES_USER')
$dbPass = [Environment]::GetEnvironmentVariable('SCADA_MOBILE_DATABASE_PASSWORD')

if ([string]::IsNullOrWhiteSpace($dbName)) {
    Write-Host 'Missing SCADA_MOBILE_POSTGRES_DB in env file.'
    exit 1
}
if ([string]::IsNullOrWhiteSpace($dbUser)) {
    Write-Host 'Missing SCADA_MOBILE_POSTGRES_USER in env file.'
    exit 1
}
if ([string]::IsNullOrWhiteSpace($dbPass)) {
    Write-Host 'Missing SCADA_MOBILE_DATABASE_PASSWORD in env file.'
    exit 1
}

if (-not (Test-Path $SeedSql)) {
    Write-Host "Missing seed SQL file: $SeedSql"
    exit 1
}

$running = docker inspect --format='{{.State.Running}}' $Container 2>$null
if ($running -ne 'true') {
    Write-Host "Container $Container is not running. Start it first: make docker-prod-up"
    exit 1
}

$proc = Start-Process -FilePath 'docker' `
    -ArgumentList @('exec', '-i', '-e', "PGPASSWORD=$dbPass", $Container, 'psql', '-U', $dbUser, '-d', $dbName, '-v', 'ON_ERROR_STOP=1', '-f', '-') `
    -RedirectStandardInput $SeedSql `
    -NoNewWindow -Wait -PassThru

exit $proc.ExitCode
