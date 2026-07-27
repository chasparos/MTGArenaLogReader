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
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

Write-Host "Applying patch: $resolvedPatch"
#git apply --ignore-whitespace -- "$resolvedPatch"
#if ($LASTEXITCODE -ne 0) {
#    throw "git apply failed with exit code $LASTEXITCODE"
#}

Write-Host "Running tests..."

# Windows PowerShell 5.1 converts native-process stderr into NativeCommandError
# records. Maven/JDK warnings written to stderr can therefore stop the script
# when ErrorActionPreference is Stop. Redirect stderr inside cmd.exe so that
# PowerShell receives one ordinary output stream while preserving Maven's exit
# code.
$testCommand = '""{0}" test 2>&1"' -f $mavenWrapper
& $env:ComSpec /d /s /c $testCommand | Tee-Object -FilePath $testLog
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