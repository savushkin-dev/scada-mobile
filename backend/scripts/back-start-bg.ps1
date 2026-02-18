param(
    [string]$BackendDir = "backend",
    [int]$Port = 8080,
    [string]$JavaToolOptions = "-Dfile.encoding=UTF-8 -Dsun.stdout.encoding=UTF-8 -Dsun.stderr.encoding=UTF-8",
    [ValidateSet('dev', 'prod')]
    [string]$Profile = "dev"
)

$ErrorActionPreference = 'Stop'

$backendPath = Resolve-Path $BackendDir
$logsDir = Join-Path $backendPath "logs"
$logFile = Join-Path $logsDir "backend.log"
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

New-Item -ItemType Directory -Path $logsDir -Force | Out-Null

$isRunning = $false
if (Test-Path $pidFile) {
    $raw = (Get-Content $pidFile | Select-Object -First 1).ToString().Trim()
    $pidNum = 0
    if ([int]::TryParse($raw, [ref]$pidNum) -and (Get-Process -Id $pidNum -ErrorAction SilentlyContinue) -and (Test-PidOnPort -TargetPort $Port -TargetPid $pidNum)) {
        $isRunning = $true
    }
}

if (-not $isRunning) {
    $portPid = Get-PortPid -TargetPort $Port
    if ($portPid) {
        Set-Content -Path $pidFile -Value $portPid
        Write-Host "Backend already running on port $Port. PID=$portPid"
        exit 0
    }
}

if ($isRunning) {
    Write-Host "Backend already running."
    exit 0
}

New-Item -ItemType File -Path $logFile -Force | Out-Null

$runCmdFile = Join-Path $logsDir "run-backend.cmd"
$cmdBody = @(
    '@echo off',
    'chcp 65001 >NUL',
    'if not exist logs mkdir logs',
    ('set "JAVA_TOOL_OPTIONS=' + $JavaToolOptions + '"'),    ('set "SPRING_PROFILES_ACTIVE=' + $Profile + '"'),    'call gradlew.bat bootRun >> logs\\backend.log 2>&1'
) -join "`r`n"
Set-Content -Path $runCmdFile -Value $cmdBody -Encoding ASCII

$process = Start-Process -FilePath "cmd.exe" -WorkingDirectory $backendPath -ArgumentList "/c", "logs\\run-backend.cmd" -WindowStyle Hidden -PassThru
Set-Content -Path $pidFile -Value $process.Id

Start-Sleep -Milliseconds 500
if (-not (Get-Process -Id $process.Id -ErrorAction SilentlyContinue)) {
    Write-Host "Backend failed to stay running. Check $logFile"
    exit 0
}

Write-Host "Backend started in background."
Write-Host "Logs: $logFile"
Write-Host "PID file: $pidFile"