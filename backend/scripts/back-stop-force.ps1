param(
    [string]$BackendDir = "backend",
    [int]$Port = 8080
)

$ErrorActionPreference = 'SilentlyContinue'

$backendPath = Resolve-Path $BackendDir
$logsDir = Join-Path $backendPath "logs"
$pidFile = Join-Path $logsDir "backend.pid"

function Get-PortPid {
    param([int]$TargetPort)
    $lines = netstat -ano -p tcp | Select-String -Pattern ("^\s*TCP\s+\S+:" + $TargetPort + "\s+\S+\s+LISTENING\s+(\d+)\s*$")
    if (-not $lines) { return $null }
    $line = @($lines)[0]
    if ($line.Matches.Count -eq 0) { return $null }
    $value = $line.Matches[0].Groups[1].Value
    $parsedPid = 0
    if ([int]::TryParse($value, [ref]$parsedPid)) { return $parsedPid }
    return $null
}

$portPid = Get-PortPid -TargetPort $Port

$killedAny = $false
if ($portPid) {
    cmd /c "taskkill /PID $portPid /T /F >NUL 2>&1"
    if ($LASTEXITCODE -eq 0) {
        $killedAny = $true
    }
}

if (Test-Path $pidFile) {
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if ($killedAny) {
    Write-Host "Emergency stop executed for port $Port."
} elseif ($portPid) {
    Write-Host "Process on port $Port was found (PID=$portPid), but kill failed (check permissions)."
} else {
    Write-Host "No process found on port $Port."
}

exit 0