param(
    [Parameter(Mandatory = $true, Position = 0)]
    [string]$PatchFile,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$CommitMessage
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot

$resolvedPatch = (Resolve-Path $PatchFile).Path
$archive = Join-Path $repoRoot "latest snapshot.zip"
$testLog = Join-Path $repoRoot "latest test results.log"

Write-Host "Applying patch: $resolvedPatch"
git apply --ignore-whitespace -- "$resolvedPatch"
if ($LASTEXITCODE -ne 0) {
    throw "git apply failed with exit code $LASTEXITCODE"
}

Write-Host "Running tests..."
& .\mvnw.cmd test 2>&1 | Tee-Object -FilePath $testLog
$testExit = $LASTEXITCODE

Write-Host "Staging repository state..."
git add -A
if ($LASTEXITCODE -ne 0) {
    throw "git add failed with exit code $LASTEXITCODE"
}

Write-Host "Committing: $CommitMessage"
git commit -m "$CommitMessage"
if ($LASTEXITCODE -ne 0) {
    throw "git commit failed with exit code $LASTEXITCODE"
}

if (Test-Path $archive) {
    Remove-Item $archive -Force
}

Write-Host "Creating archive: $archive"
git archive --format=zip --output="$archive" HEAD
if ($LASTEXITCODE -ne 0) {
    throw "git archive failed with exit code $LASTEXITCODE"
}

Write-Host ""
Write-Host "Test exit code: $testExit"
Write-Host "Test log: $testLog"
Write-Host "Snapshot: $archive"

exit $testExit
