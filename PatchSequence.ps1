param(
    [Parameter(Mandatory = $false, Position = 0)]
    [string]$PatchFile,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$CommitMessage
)

$ErrorActionPreference = "Stop"
$scriptPath = $MyInvocation.MyCommand.Path
$repoRoot = Split-Path -Parent $scriptPath
$scriptHashBeforePatch = (Get-FileHash -LiteralPath $scriptPath -Algorithm SHA256).Hash
Set-Location $repoRoot

$archive = Join-Path $repoRoot "latest snapshot.zip"
$testLog = Join-Path $repoRoot "latest test results.log"
$snapshotManifest = Join-Path $repoRoot "latest snapshot manifest.json"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

function Invoke-GitText {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)
    $output = & git @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE`n$output"
    }
    return ($output -join [Environment]::NewLine).Trim()
}

function Get-FileSha256 {
    param([Parameter(Mandatory = $true)][string]$Path)
    return (Get-FileHash -LiteralPath $Path -Algorithm SHA256).Hash.ToLowerInvariant()
}

function Get-TestSummary {
    param([Parameter(Mandatory = $true)][string]$Path)
    $text = Get-Content -LiteralPath $Path -Raw
    $matches = [regex]::Matches(
        $text,
        'Tests run:\s*(\d+),\s*Failures:\s*(\d+),\s*Errors:\s*(\d+),\s*Skipped:\s*(\d+)')
    $last = if ($matches.Count -gt 0) { $matches[$matches.Count - 1] } else { $null }
    return [ordered]@{
        outcome = if ($text -match 'BUILD SUCCESS') { 'passed' } elseif ($text -match 'BUILD FAILURE') { 'failed' } else { 'unknown' }
        testsRun = if ($last) { [int]$last.Groups[1].Value } else { $null }
        failures = if ($last) { [int]$last.Groups[2].Value } else { $null }
        errors = if ($last) { [int]$last.Groups[3].Value } else { $null }
        skipped = if ($last) { [int]$last.Groups[4].Value } else { $null }
    }
}

function Get-JavaVersionLine {
    try {
        $output = & $env:ComSpec /d /s /c "java -version 2>&1"
        if ($LASTEXITCODE -ne 0) { return "unavailable (java exit code $LASTEXITCODE)" }
        $firstLine = @($output | Select-Object -First 1)
        if ($firstLine.Count -eq 0) { return 'unavailable (no version output)' }
        return $firstLine[0].ToString().Trim()
    } catch {
        return "unavailable ($($_.Exception.Message))"
    }
}
function Get-LargeTrackedFiles {
    param([int]$Limit = 20)
    $records = @()
    foreach ($line in (& git ls-tree -r -l HEAD)) {
        if ($line -match '^\d+\s+\w+\s+[0-9a-f]+\s+(\d+)\t(.+)$') {
            $records += [pscustomobject]@{ path = $matches[2]; bytes = [int64]$matches[1] }
        }
    }
    return @($records | Sort-Object -Property bytes -Descending | Select-Object -First $Limit)
}

function Write-SnapshotManifest {
    param(
        [Parameter(Mandatory = $true)][string]$ArchivePath,
        [Parameter(Mandatory = $true)][string]$TestLogPath,
        [Parameter(Mandatory = $true)][string]$OutputPath,
        [Parameter(Mandatory = $true)][int]$TestExitCode
    )

    $statusLines = @(& git status --short --untracked-files=all)
    if ($LASTEXITCODE -ne 0) { throw "git status failed with exit code $LASTEXITCODE" }

    $branch = Invoke-GitText @('branch', '--show-current')
    if (-not $branch) { $branch = '(detached HEAD)' }

    $manifest = [ordered]@{
        schemaVersion = 1
        generatedAtUtc = [DateTime]::UtcNow.ToString('o')
        repository = [ordered]@{
            commit = Invoke-GitText @('rev-parse', 'HEAD')
            branch = $branch
            workingTreeStatus = @($statusLines)
        }
        snapshot = [ordered]@{
            file = Split-Path -Leaf $ArchivePath
            bytes = (Get-Item -LiteralPath $ArchivePath).Length
            sha256 = Get-FileSha256 $ArchivePath
            production = 'git archive --format=zip HEAD'
            ignoredFilePolicy = 'Contains committed tracked files from HEAD only; excludes .git metadata, ignored files, and untracked files.'
        }
        validation = [ordered]@{
            buildCommand = '.\mvnw.cmd test'
            testExitCode = $TestExitCode
            testLog = Split-Path -Leaf $TestLogPath
            testLogSha256 = Get-FileSha256 $TestLogPath
            summary = Get-TestSummary $TestLogPath
        }
        runtime = [ordered]@{
            operatingSystem = [System.Environment]::OSVersion.VersionString
            powershell = $PSVersionTable.PSVersion.ToString()
            git = Invoke-GitText @('--version')
            java = Get-JavaVersionLine
        }
        largeTrackedFiles = @(Get-LargeTrackedFiles)
    }

    $manifest | ConvertTo-Json -Depth 8 | Set-Content -LiteralPath $OutputPath -Encoding utf8
}

if (-not (Test-Path -LiteralPath $mavenWrapper)) {
    throw @"
Maven Wrapper not found at: $mavenWrapper

Steady Arc requires the repository to commit its Maven Wrapper so sandboxed
assistants and human operators use the same Maven version. Ask a human with a
working Maven installation to run `mvn wrapper:wrapper`, then commit:
  mvnw
  mvnw.cmd
  .mvn/wrapper/maven-wrapper.properties
"@
}

if (-not $PatchFile) {
    Write-Host "No patch file given. Skipping patch application."
} else {
    $downloads = Join-Path $HOME "Downloads"
    $requestedPatch = $PatchFile
    if (-not [System.IO.Path]::IsPathRooted($requestedPatch)) {
        $downloadCandidate = Join-Path $downloads $requestedPatch
        if (Test-Path -LiteralPath $downloadCandidate) { $requestedPatch = $downloadCandidate }
    }
    $sourcePatch = (Resolve-Path -LiteralPath $requestedPatch).Path
    $rootPatch = Join-Path $repoRoot (Split-Path -Leaf $sourcePatch)
    if ($sourcePatch -ne $rootPatch) {
        Write-Host "Copying patch to project root: $rootPatch"
        Copy-Item -LiteralPath $sourcePatch -Destination $rootPatch -Force
    }
    Write-Host "Checking patch: $rootPatch"
    git apply --check --ignore-whitespace -- "$rootPatch"
    if ($LASTEXITCODE -ne 0) { throw "git apply --check failed with exit code $LASTEXITCODE" }
    Write-Host "Applying patch: $rootPatch"
    git apply --ignore-whitespace -- "$rootPatch"
    if ($LASTEXITCODE -ne 0) { throw "git apply failed with exit code $LASTEXITCODE" }
    $appliedPatches = Join-Path $repoRoot "applied patches"
    New-Item -ItemType Directory -Path $appliedPatches -Force | Out-Null
    $archivedPatch = Join-Path $appliedPatches (Split-Path -Leaf $rootPatch)
    Write-Host "Archiving applied patch: $archivedPatch"
    Move-Item -LiteralPath $rootPatch -Destination $archivedPatch -Force

    $scriptHashAfterPatch = (Get-FileHash -LiteralPath $scriptPath -Algorithm SHA256).Hash
    if ($scriptHashAfterPatch -ne $scriptHashBeforePatch) {
        $hostExecutable = (Get-Process -Id $PID).Path
        Write-Host "PatchSequence.ps1 changed. Restarting with the updated script before validation..."
        & $hostExecutable -NoProfile -ExecutionPolicy Bypass -File $scriptPath -CommitMessage $CommitMessage
        exit $LASTEXITCODE
    }
}

Write-Host "Running tests..."
$testCommand = '""{0}" test 2>&1"' -f $mavenWrapper
& $env:ComSpec /d /s /c $testCommand | Tee-Object -FilePath $testLog
$testExit = $LASTEXITCODE

if ($testExit -ne 0) {
    Write-Host "Tests failed. Leaving applied changes uncommitted and unstaged."

    foreach ($generatedFile in @($archive, $snapshotManifest)) {
        if (Test-Path -LiteralPath $generatedFile) { Remove-Item -LiteralPath $generatedFile -Force }
    }

    Write-Host "Creating diagnostic archive from unchanged HEAD: $archive"
    git archive --format=zip --output="$archive" HEAD
    if ($LASTEXITCODE -ne 0) { throw "git archive failed with exit code $LASTEXITCODE" }

    Write-Host "Creating failed-validation snapshot manifest: $snapshotManifest"
    Write-SnapshotManifest -ArchivePath $archive -TestLogPath $testLog -OutputPath $snapshotManifest -TestExitCode $testExit

    Write-Host ""
    Write-Host "Test exit code: $testExit"
    Write-Host "Test log: $testLog"
    Write-Host "Snapshot: $archive"
    Write-Host "Snapshot manifest: $snapshotManifest"
    exit $testExit
}

Write-Host "Staging repository state..."
git add -A
if ($LASTEXITCODE -ne 0) { throw "git add failed with exit code $LASTEXITCODE" }

if (Test-Path -LiteralPath (Join-Path $repoRoot "mvnw")) {
    git update-index --chmod=+x -- mvnw
    if ($LASTEXITCODE -ne 0) { throw "git update-index failed to preserve mvnw executable intent" }
}

$stagedChanges = git diff --cached --quiet
$hasChanges = $LASTEXITCODE -ne 0
if ($hasChanges) {
    Write-Host "Committing: $CommitMessage"
    git commit -m "$CommitMessage"
    if ($LASTEXITCODE -ne 0) { throw "git commit failed with exit code $LASTEXITCODE" }
} else {
    Write-Host "No staged changes. Skipping commit."
}

foreach ($generatedFile in @($archive, $snapshotManifest)) {
    if (Test-Path -LiteralPath $generatedFile) { Remove-Item -LiteralPath $generatedFile -Force }
}

Write-Host "Creating archive: $archive"
git archive --format=zip --output="$archive" HEAD
if ($LASTEXITCODE -ne 0) { throw "git archive failed with exit code $LASTEXITCODE" }

Write-Host "Creating snapshot manifest: $snapshotManifest"
Write-SnapshotManifest -ArchivePath $archive -TestLogPath $testLog -OutputPath $snapshotManifest -TestExitCode $testExit

Write-Host ""
Write-Host "Test exit code: $testExit"
Write-Host "Test log: $testLog"
Write-Host "Snapshot: $archive"
Write-Host "Snapshot manifest: $snapshotManifest"

exit $testExit
