@echo off
powershell -NoProfile -ExecutionPolicy Bypass -Command "$sb = [ScriptBlock]::Create((Get-Content -LiteralPath '%~dp0collect_printsrv_logs.ps1' -Raw -Encoding UTF8)); & $sb '%~dp0'"
pause
