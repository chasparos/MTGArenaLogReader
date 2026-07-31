# RunWidget.ps1 — repository-local launcher for MTGArenaLogReader
# Requires: Java 24+, Maven Wrapper (mvnw.cmd)
# Usage: .\RunWidget.ps1

$ErrorActionPreference = 'Stop'
Set-Location -Path $PSScriptRoot

if (-not (Test-Path '.\mvnw.cmd')) {
    Write-Error "mvnw.cmd not found. Ensure you are running from the repository root."
    exit 1
}

Write-Host "Starting MTGArenaLogReader via Maven Wrapper..." -ForegroundColor Cyan
& .\mvnw.cmd exec:java
