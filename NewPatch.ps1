[CmdletBinding(DefaultParameterSetName = "Repository")]
param(
    [Parameter(Mandatory = $true)]
    [string]$OutputPath,

    [Parameter(ParameterSetName = "Directories", Mandatory = $true)]
    [string]$BaselineDirectory,

    [Parameter(ParameterSetName = "Directories", Mandatory = $true)]
    [string]$ModifiedDirectory,

    [Parameter(ParameterSetName = "Repository")]
    [string]$RepositoryRoot = (Get-Location).Path
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Git {
    param(
        [Parameter(Mandatory = $true)][string]$WorkingDirectory,
        [Parameter(Mandatory = $true)][string[]]$Arguments
    )

    $output = & git -C $WorkingDirectory @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) {
        throw "git $($Arguments -join ' ') failed in '$WorkingDirectory':`n$($output -join [Environment]::NewLine)"
    }
    return $output
}

function Copy-DirectoryBytes {
    param(
        [Parameter(Mandatory = $true)][string]$Source,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    if (-not (Test-Path -LiteralPath $Source -PathType Container)) {
        throw "Directory not found: $Source"
    }

    New-Item -ItemType Directory -Force -Path $Destination | Out-Null
    Get-ChildItem -LiteralPath $Source -Force | ForEach-Object {
        if ($_.Name -eq ".git") {
            return
        }
        Copy-Item -LiteralPath $_.FullName -Destination $Destination -Recurse -Force
    }
}

function Write-BinaryPatch {
    param(
        [Parameter(Mandatory = $true)][string]$GitRoot,
        [Parameter(Mandatory = $true)][string]$Destination
    )

    Invoke-Git $GitRoot @("add", "-A") | Out-Null
    $patchBytes = @(& git -C $GitRoot diff --cached --binary --full-index --no-ext-diff HEAD)
    if ($LASTEXITCODE -ne 0) {
        throw "git diff failed while producing '$Destination'."
    }

    $parent = Split-Path -Parent $Destination
    if ($parent) {
        New-Item -ItemType Directory -Force -Path $parent | Out-Null
    }

    [System.IO.File]::WriteAllText(
        $Destination,
        ($patchBytes -join "`n") + $(if ($patchBytes.Count -gt 0) { "`n" } else { "" }),
        [System.Text.UTF8Encoding]::new($false)
    )
}

$resolvedOutput = [System.IO.Path]::GetFullPath($OutputPath)
$tempRoot = Join-Path ([System.IO.Path]::GetTempPath()) ("steadyarc-patch-" + [Guid]::NewGuid().ToString("N"))
New-Item -ItemType Directory -Path $tempRoot | Out-Null

try {
    if ($PSCmdlet.ParameterSetName -eq "Directories") {
        $baseline = (Resolve-Path -LiteralPath $BaselineDirectory).Path
        $modified = (Resolve-Path -LiteralPath $ModifiedDirectory).Path
        $workTree = Join-Path $tempRoot "work"

        Copy-DirectoryBytes $baseline $workTree
        Invoke-Git $workTree @("init", "--quiet") | Out-Null
        Invoke-Git $workTree @("config", "user.name", "Steady Arc Patch Tool") | Out-Null
        Invoke-Git $workTree @("config", "user.email", "steadyarc@invalid.local") | Out-Null
        Invoke-Git $workTree @("add", "-A") | Out-Null
        Invoke-Git $workTree @("commit", "--quiet", "-m", "Baseline") | Out-Null

        Get-ChildItem -LiteralPath $workTree -Force |
            Where-Object { $_.Name -ne ".git" } |
            Remove-Item -Recurse -Force
        Copy-DirectoryBytes $modified $workTree
        Write-BinaryPatch $workTree $resolvedOutput
    }
    else {
        $root = (Resolve-Path -LiteralPath $RepositoryRoot).Path
        Invoke-Git $root @("rev-parse", "--show-toplevel") | Out-Null

        $workTree = Join-Path $tempRoot "worktree"
        Invoke-Git $root @("worktree", "add", "--quiet", "--detach", $workTree, "HEAD") | Out-Null
        try {
            $paths = Invoke-Git $root @("ls-files", "--cached", "--others", "--exclude-standard")
            foreach ($relativePath in $paths) {
                $source = Join-Path $root $relativePath
                $destination = Join-Path $workTree $relativePath
                if (Test-Path -LiteralPath $source -PathType Leaf) {
                    $parent = Split-Path -Parent $destination
                    if ($parent) {
                        New-Item -ItemType Directory -Force -Path $parent | Out-Null
                    }
                    Copy-Item -LiteralPath $source -Destination $destination -Force
                }
                elseif (-not (Test-Path -LiteralPath $source)) {
                    Remove-Item -LiteralPath $destination -Force -ErrorAction SilentlyContinue
                }
            }
            Write-BinaryPatch $workTree $resolvedOutput
        }
        finally {
            Invoke-Git $root @("worktree", "remove", "--force", $workTree) | Out-Null
        }
    }

    Write-Host "Patch written: $resolvedOutput"
}
finally {
    Remove-Item -LiteralPath $tempRoot -Recurse -Force -ErrorAction SilentlyContinue
}
