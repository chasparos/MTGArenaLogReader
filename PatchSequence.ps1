param(
    [Parameter(Mandatory = $false, Position = 0)]
    [string]$PatchFile,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$CommitMessage
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $repoRoot


$archive = Join-Path $repoRoot "latest snapshot.zip"
$testLog = Join-Path $repoRoot "latest test results.log"
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"


# If no patch is given, skip patching and continue. This supports manual edits
# through the same test, commit, and archive flow.
if (-not $PatchFile) {
    Write-Host "No patch file given. Skipping patch application."
} else {
    $downloads = Join-Path $HOME "Downloads"
    $requestedPatch = $PatchFile

    if (-not [System.IO.Path]::IsPathRooted($requestedPatch)) {
        $downloadCandidate = Join-Path $downloads $requestedPatch
        if (Test-Path -LiteralPath $downloadCandidate) {
            $requestedPatch = $downloadCandidate
        }
    }

    $sourcePatch = (Resolve-Path -LiteralPath $requestedPatch).Path
    $rootPatch = Join-Path $repoRoot (Split-Path -Leaf $sourcePatch)

    if ($sourcePatch -ne $rootPatch) {
        Write-Host "Copying patch to project root: $rootPatch"
        Copy-Item -LiteralPath $sourcePatch -Destination $rootPatch -Force
    }

    Write-Host "Applying patch: $rootPatch"
    git apply --ignore-whitespace -- "$rootPatch"
    if ($LASTEXITCODE -ne 0) {
        throw "git apply failed with exit code $LASTEXITCODE"
    }

    $appliedPatches = Join-Path $repoRoot "applied patches"
    New-Item -ItemType Directory -Path $appliedPatches -Force | Out-Null
    $archivedPatch = Join-Path $appliedPatches (Split-Path -Leaf $rootPatch)

    Write-Host "Archiving applied patch: $archivedPatch"
    Move-Item -LiteralPath $rootPatch -Destination $archivedPatch -Force
}

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