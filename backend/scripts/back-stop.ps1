param(
    [string]$BackendDir = "backend",
    [int]$Port = 8080
)

$ErrorActionPreference = 'SilentlyContinue'

$backendPath = Resolve-Path $BackendDir
$logsDir = Join-Path $backendPath "logs"
$pidFile = Join-Path $logsDir "backend.pid"

$killedAny = $false

if (Test-Path $pidFile) {
    $raw = (Get-Content $pidFile | Select-Object -First 1).ToString().Trim()
    $pidNum = 0
    if ([int]::TryParse($raw, [ref]$pidNum) -and (Get-Process -Id $pidNum -ErrorAction SilentlyContinue)) {
        cmd /c "taskkill /PID $pidNum /T /F >NUL 2>&1"
        if ($LASTEXITCODE -eq 0) {
            $killedAny = $true
        }
    }
    Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
}

if ($killedAny) {
    Write-Host "Backend stopped."
} else {
    Write-Host "Backend process not found (or stale PID file). Use back-stop-force for emergency port kill."
}

exit 0