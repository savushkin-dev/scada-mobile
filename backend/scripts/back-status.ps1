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
    $line = netstat -ano -p tcp | Select-String -Pattern ("^\s*TCP\s+\S+:" + $TargetPort + "\s+\S+\s+LISTENING\s+(\d+)\s*$") | Select-Object -First 1
    if (-not $line) { return $null }
    $value = $line.Matches[0].Groups[1].Value
    $parsedPid = 0
    if ([int]::TryParse($value, [ref]$parsedPid)) { return $parsedPid }
    return $null
}

function Test-PidOnPort {
    param([int]$TargetPort, [int]$TargetPid)
    $portPidValue = Get-PortPid -TargetPort $TargetPort
    return ($portPidValue -eq $TargetPid)
}

$pidNum = 0
$running = $false

if (Test-Path $pidFile) {
    $raw = (Get-Content $pidFile | Select-Object -First 1).ToString().Trim()
    if ([int]::TryParse($raw, [ref]$pidNum) -and (Get-Process -Id $pidNum -ErrorAction SilentlyContinue) -and (Test-PidOnPort -TargetPort $Port -TargetPid $pidNum)) {
        $running = $true
    }
}

if (-not $running) {
    $portPid = Get-PortPid -TargetPort $Port
    if ($portPid) {
        $running = $true
        $pidNum = $portPid
        New-Item -ItemType Directory -Path $logsDir -Force | Out-Null
        Set-Content -Path $pidFile -Value $pidNum
    }
}

if ($running) {
    Write-Host "Backend is running. PID=$pidNum, port=$Port"
} else {
    Write-Host "Backend is not running."
}

exit 0