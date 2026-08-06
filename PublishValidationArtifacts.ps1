[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$SourceCommitMessage = "Validate current branch",

    [Parameter(Mandatory = $false)]
    [string]$ArtifactCommitMessage = "Record validation artifacts"
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$patchSequence = Join-Path $repoRoot "PatchSequence.ps1"
$testLog = Join-Path $repoRoot "latest test results.log"
$snapshotManifest = Join-Path $repoRoot "latest snapshot manifest.json"

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & git @Arguments
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed with exit code $LASTEXITCODE"
    }
}

if (-not (Test-Path -LiteralPath $patchSequence -PathType Leaf)) {
    throw "PatchSequence.ps1 not found at: $patchSequence"
}

Set-Location $repoRoot

& $patchSequence -PatchFile "" -CommitMessage $SourceCommitMessage
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

foreach ($artifact in @($testLog, $snapshotManifest)) {
    if (-not (Test-Path -LiteralPath $artifact -PathType Leaf)) {
        throw "PatchSequence.ps1 did not produce required validation artifact: $artifact"
    }
}

$branch = (& git branch --show-current).Trim()
if ($LASTEXITCODE -ne 0 -or [string]::IsNullOrWhiteSpace($branch)) {
    throw "Validation artifacts can only be published from a named branch."
}

Invoke-Git @("add", "--force", "--", $testLog, $snapshotManifest)
Invoke-Git @("commit", "-m", $ArtifactCommitMessage)
Invoke-Git @("push")
