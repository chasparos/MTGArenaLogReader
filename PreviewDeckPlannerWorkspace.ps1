[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$mavenWrapper = Join-Path $repoRoot "mvnw.cmd"
$targetDirectory = Join-Path $repoRoot "target"
$classPathFile = Join-Path $targetDirectory "deck-planner-workspace-preview-classpath.txt"
$mainClass = "devtools.DeckPlannerWorkspacePreview"

if (-not (Test-Path -LiteralPath $mavenWrapper -PathType Leaf)) {
    throw "Maven Wrapper not found at: $mavenWrapper"
}

Set-Location $repoRoot
$mavenArguments = @(
    "--quiet"
    "--define"
    "skipTests=true"
    "compile"
    "dependency:build-classpath"
    "--define"
    "mdep.outputFile=$classPathFile"
)
& $mavenWrapper @mavenArguments
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
if (-not (Test-Path -LiteralPath $classPathFile -PathType Leaf)) {
    throw "Preview runtime classpath was not generated at: $classPathFile"
}

$dependencyClassPath = (Get-Content -LiteralPath $classPathFile -Raw).Trim()
$classesDirectory = Join-Path $targetDirectory "classes"
$runtimeClassPath = if ([string]::IsNullOrWhiteSpace($dependencyClassPath)) {
    $classesDirectory
} else {
    "$classesDirectory;$dependencyClassPath"
}

Write-Host "Launching $mainClass"
& java "-cp" $runtimeClassPath $mainClass
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
