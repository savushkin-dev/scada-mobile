param(
    [Parameter(Mandatory = $true)][string]$BaseDir,
    [int]$DurationMinutes = 7,
    [int]$IntervalSeconds = 10
)

$ErrorActionPreference = 'Continue'
$OutputDir = Join-Path $BaseDir 'printsrv-logs'
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

# Хосты:порты и устройства автоматов — актуальные данные из админ-панели (карточки автоматов).
# Системные устройства: Line, scada, BatchQueue (PrintSrvTopologyJpaAdapter.java:149-151).
# Внимание: "CamAgregation" — с одной "g", так задано кодами устройств в БД.
$Instances = @(
    @{ Name = 'Bosch';     Addr = '10.151.2.50'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11', 'Printer12') },
    @{ Name = 'Grunwald5'; Addr = '10.140.2.40'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'CamEanChecker1', 'CamEanChecker2', 'CamEanChecker3', 'CamEanChecker4', 'Printer11', 'Printer12') },
    @{ Name = 'Hassia1';   Addr = '10.140.2.46'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11') },
    @{ Name = 'Hassia2';   Addr = '10.140.2.54'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11') },
    @{ Name = 'Hassia3';   Addr = '10.140.2.51'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11', 'Printer12') },
    @{ Name = 'Hassia4';   Addr = '10.140.2.48'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11', 'Printer12', 'Printer2') },
    @{ Name = 'Hassia5';   Addr = '10.140.2.56'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11', 'Printer12') },
    @{ Name = 'Hassia6';   Addr = '10.140.2.60'; Port = 10101; Devices = @('CamAgregation', 'CamAgregationBox', 'CamChecker', 'Printer11', 'Printer12') }
)

$SystemDevices = @('Line', 'scada', 'BatchQueue')

$ConnectTimeoutMs = 5000
$ReadTimeoutMs    = 5000
$MaxResponseSize  = 10MB

$files = @{}
foreach ($inst in $Instances) {
    foreach ($dev in ($SystemDevices + $inst.Devices)) {
        $key = $inst.Name + '_' + $dev
        $files[$key] = Join-Path $OutputDir ($key + '.jsonl')
        # Очищаем файлы от прошлых запусков
        Set-Content -Path $files[$key] -Value '' -Encoding UTF8
    }
}

function Read-Fully {
    param([System.Net.Sockets.NetworkStream]$Stream, [byte[]]$Buffer, [int]$Count)
    $read = 0
    while ($read -lt $Count) {
        $n = $Stream.Read($Buffer, $read, $Count - $read)
        if ($n -le 0) { throw "connection closed after $read of $Count bytes" }
        $read += $n
    }
}

function Send-QueryAll {
    param([string]$Addr, [int]$Port, [string]$Device)

    $body = [System.Text.Encoding]::GetEncoding('windows-1251').GetBytes(
        (@{ DeviceName = $Device; Command = 'QueryAll' } | ConvertTo-Json -Compress))
    $len = [System.BitConverter]::GetBytes([int]$body.Length)
    if ([System.BitConverter]::IsLittleEndian) { [Array]::Reverse($len) }  # big-endian

    $client = New-Object System.Net.Sockets.TcpClient
    try {
        $iar = $client.BeginConnect($Addr, $Port, $null, $null)
        if (-not $iar.AsyncWaitHandle.WaitOne($ConnectTimeoutMs)) { throw "connect timeout" }
        $client.EndConnect($iar)
        $client.ReceiveTimeout = $ReadTimeoutMs
        $client.SendTimeout    = $ReadTimeoutMs
        $stream = $client.GetStream()

        $frame = [byte[]]([System.Text.Encoding]::ASCII.GetBytes('P001')) + $len + $body
        $stream.Write($frame, 0, $frame.Length)
        $stream.Flush()

        # Заголовок: 4 байта магия + 4 байта big-endian длина
        $header = New-Object byte[] 8
        Read-Fully $stream $header 8 | Out-Null
        $magic = [System.Text.Encoding]::ASCII.GetString($header, 0, 4)
        if ($magic -ne 'P001') { throw "bad magic: $magic" }
        $lenBytes = [byte[]]$header[4..7]
        if ([System.BitConverter]::IsLittleEndian) { [Array]::Reverse($lenBytes) }
        $respLen = [System.BitConverter]::ToInt32($lenBytes, 0)
        if ($respLen -le 0 -or $respLen -gt $MaxResponseSize) { throw "bad length: $respLen" }

        $respBody = New-Object byte[] $respLen
        Read-Fully $stream $respBody $respLen | Out-Null
        return [System.Text.Encoding]::GetEncoding('windows-1251').GetString($respBody)
    }
    finally {
        $client.Close()
    }
}

$deadline = (Get-Date).AddMinutes($DurationMinutes)
$cycle = 0
while ((Get-Date) -lt $deadline) {
    $cycle++
    Write-Host ("=== Срез {0}, {1:HH:mm:ss} ===" -f $cycle, (Get-Date))
    foreach ($inst in $Instances) {
        foreach ($dev in ($SystemDevices + $inst.Devices)) {
            $key = $inst.Name + '_' + $dev
            try {
                $json = Send-QueryAll -Addr $inst.Addr -Port $inst.Port -Device $dev
                $record = @{ ts = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'); ok = $true; host = $inst.Addr; port = $inst.Port; device = $dev; data = $json } | ConvertTo-Json -Compress
                Write-Host ("OK   {0}:{1} {2} ({3} bytes)" -f $inst.Addr, $inst.Port, $dev, $json.Length)
            }
            catch {
                $record = @{ ts = (Get-Date).ToString('yyyy-MM-dd HH:mm:ss'); ok = $false; host = $inst.Addr; port = $inst.Port; device = $dev; error = $_.Exception.Message } | ConvertTo-Json -Compress
                Write-Host ("FAIL {0}:{1} {2}: {3}" -f $inst.Addr, $inst.Port, $dev, $_.Exception.Message)
            }
            Add-Content -Path $files[$key] -Value $record -Encoding UTF8
        }
    }
    if ((Get-Date) -lt $deadline) { Start-Sleep -Seconds $IntervalSeconds }
}

Write-Host ""
Write-Host ("Готово. Срезов: {0}. Логи в: {1}" -f $cycle, $OutputDir)
