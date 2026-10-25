# Arrête les serveurs NeoForge de CE projet restés vivants (après un timeout, le java reste orphelin et garde le port 25565).
param([string]$Root)
Get-CimInstance Win32_Process -Filter "Name='java.exe'" | Where-Object { $_.CommandLine -and $_.CommandLine.Contains('serverRunClasspath.txt') -and $_.CommandLine.Contains($Root) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
