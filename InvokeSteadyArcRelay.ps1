param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("git-status", "git-diff-check", "maven-test")]
    [string]$Operation,

    [Parameter(Mandatory = $false)]
    [ValidateRange(1, 600)]
    [int]$WaitSeconds = 30
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$relayRoot = Join-Path $repoRoot ".steadyarc\relay"
$sessionPath = Join-Path $relayRoot "session.properties"
$requestDirectory = Join-Path $relayRoot "requests"
$resultDirectory = Join-Path $relayRoot "results"

if (-not (Test-Path -LiteralPath $sessionPath -PathType Leaf)) {
    throw "No active Steady Arc support relay. Start it with the Widget's relay button."
}

function Read-SimpleProperties {
    param([Parameter(Mandatory = $true)][string]$Path)

    $values = @{}
    foreach ($line in Get-Content -LiteralPath $Path -Encoding UTF8) {
        if ([string]::IsNullOrWhiteSpace($line) -or $line.StartsWith("#")) {
            continue
        }
        $separator = $line.IndexOf("=")
        if ($separator -le 0) {
            throw "Invalid relay property in $Path"
        }
        $value = $line.Substring($separator + 1)
        # java.util.Properties escapes punctuation such as the colons in an ISO
        # timestamp. The relay's values contain no general escape language, so
        # these two deterministic reversals are sufficient for this protocol.
        $value = $value.Replace('\:', ':').Replace('\=', '=').Replace('\\', '\')
        $values[$line.Substring(0, $separator)] = $value
    }
    return $values
}

$session = Read-SimpleProperties -Path $sessionPath
if ($session["schemaVersion"] -ne "1") {
    throw "Unsupported relay session schema."
}
if ([string]::IsNullOrWhiteSpace($session["sessionToken"])) {
    throw "Relay session does not contain a token."
}
$sessionRepository = [Text.Encoding]::UTF8.GetString(
    [Convert]::FromBase64String($session["repositoryBase64"]))
if ($sessionRepository -ne $repoRoot) {
    throw "Relay session belongs to a different repository."
}
if ([DateTimeOffset]::Parse($session["expiresAtUtc"]) -le [DateTimeOffset]::UtcNow) {
    throw "Relay session has expired."
}
if (($session["operations"] -split ",") -notcontains $Operation) {
    throw "The active relay does not support operation '$Operation'."
}

$requestId = [Guid]::NewGuid().ToString()
$temporaryRequest = Join-Path $requestDirectory "$requestId.request.properties.tmp"
$publishedRequest = Join-Path $requestDirectory "$requestId.request.properties"
$resultPath = Join-Path $resultDirectory "$requestId.result.properties"

# The client never writes a command, executable, argument, directory, or
# environment variable. Operation is constrained by ValidateSet above, and the
# trusted Widget maps it to a fixed process invocation.
$requestLines = @(
    "schemaVersion=1"
    "requestId=$requestId"
    "sessionToken=$($session["sessionToken"])"
    "operation=$Operation"
)

# Publishing by rename is the transport's commit point. The Widget ignores the
# .tmp suffix, so it cannot parse or execute a request while this file is only
# partly written.
[IO.File]::WriteAllLines(
    $temporaryRequest,
    $requestLines,
    [Text.UTF8Encoding]::new($false))
Move-Item -LiteralPath $temporaryRequest -Destination $publishedRequest

Write-Host "Relay request submitted: $requestId ($Operation)"

$deadline = [DateTimeOffset]::UtcNow.AddSeconds($WaitSeconds)
while ([DateTimeOffset]::UtcNow -lt $deadline) {
    if (Test-Path -LiteralPath $resultPath -PathType Leaf) {
        $result = Read-SimpleProperties -Path $resultPath
        Write-Host "Relay status: $($result["status"])"
        if ($result.ContainsKey("exitCode")) {
            Write-Host "Exit code: $($result["exitCode"])"
        }
        if ($result.ContainsKey("error")) {
            Write-Host "Error: $($result["error"])"
        }
        if ($result.ContainsKey("outputFile")) {
            $outputPath = Join-Path $resultDirectory $result["outputFile"]
            if (Test-Path -LiteralPath $outputPath -PathType Leaf) {
                Get-Content -LiteralPath $outputPath -Encoding UTF8
            }
        }
        if ($result["status"] -eq "completed" -and $result["exitCode"] -eq "0") {
            exit 0
        }
        exit 1
    }
    Start-Sleep -Milliseconds 200
}

throw "Timed out waiting for relay result $requestId after $WaitSeconds seconds."
