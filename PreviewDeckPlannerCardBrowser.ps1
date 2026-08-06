[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven Wrapper not found at: $mavenWrapper"
}

Set-Location $repoRoot
$mavenArguments = @(
    "-q"
    "-DskipTests"
    "-Dexec.mainClass=devtools.DeckPlannerCardBrowserPreview"
    "exec:java"
)

& $mavenWrapper @mavenArguments
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
