[CmdletBinding()]
param(
    [ValidateSet("all", "local", "remote")]
    [string]$Scope = "all",

    [string]$TargetBranch = "sprint04/integration",
    [string]$Remote = "origin",

    [switch]$Apply,
    [switch]$Force,

    [string[]]$KeepPatterns = @(
        "^main$",
        "^master$",
        "^develop$",
        "^dev$",
        "^staging$",
        "^production$",
        "^release/",
        "^hotfix/"
    ),

    [string]$ReportPath = ""
)

Set-StrictMode -Version Latest

function Test-GitRef {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Ref
    )

    & git show-ref --verify --quiet $Ref
    return ($LASTEXITCODE -eq 0)
}

function Test-IsAncestor {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Ancestor,

        [Parameter(Mandatory = $true)]
        [string]$Descendant
    )

    & git merge-base --is-ancestor $Ancestor $Descendant 2>$null
    return ($LASTEXITCODE -eq 0)
}

function Get-WorktreeBranches {
    $branches = New-Object "System.Collections.Generic.HashSet[string]"
    $lines = & git worktree list --porcelain 2>$null
    foreach ($line in $lines) {
        if ($line -like "branch refs/heads/*") {
            $name = $line.Substring("branch refs/heads/".Length)
            [void]$branches.Add($name)
        }
    }
    return $branches
}

function Should-KeepBranch {
    param(
        [Parameter(Mandatory = $true)]
        [string]$BranchName,

        [Parameter(Mandatory = $true)]
        [string[]]$Patterns
    )

    foreach ($p in $Patterns) {
        if ($BranchName -match $p) { return $true }
    }
    return $false
}

function Get-DivergenceCounts {
    param(
        [Parameter(Mandatory = $true)]
        [string]$TargetRef,

        [Parameter(Mandatory = $true)]
        [string]$BranchRef
    )

    $out = & git rev-list --left-right --count "$TargetRef...$BranchRef" 2>$null
    if ($LASTEXITCODE -ne 0) {
        return $null
    }

    $parts = @((($out.Trim()) -split '\s+'))
    if ($parts.Count -lt 2) { return $null }

    return [PSCustomObject]@{
        Behind = [int]$parts[0]
        Ahead  = [int]$parts[1]
    }
}

$repoRoot = (& git rev-parse --show-toplevel 2>$null).Trim()
if (-not $repoRoot) {
    Write-Host "Not inside a git repository." -ForegroundColor Red
    exit 1
}

Set-Location $repoRoot

Write-Host "=== Branch Cleanup (report-first) ===" -ForegroundColor Cyan
Write-Host ("Repo: {0}" -f $repoRoot) -ForegroundColor DarkGray

& git fetch --all --prune | Out-Null

$currentBranch = (& git rev-parse --abbrev-ref HEAD 2>$null).Trim()
if (-not $currentBranch) { $currentBranch = "(detached)" }

$keep = @($KeepPatterns + @("^$([regex]::Escape($TargetBranch))$"))
$worktreeBranches = Get-WorktreeBranches

$localTargetExists = Test-GitRef ("refs/heads/$TargetBranch")
$remoteTargetExists = Test-GitRef ("refs/remotes/$Remote/$TargetBranch")

if (-not $localTargetExists -and -not $remoteTargetExists) {
    Write-Host ""
    Write-Host ("Target branch not found locally or on {0}: {1}" -f $Remote, $TargetBranch) -ForegroundColor Red
    exit 1
}

$localTargetRef = if ($localTargetExists) { $TargetBranch } else { "$Remote/$TargetBranch" }
$remoteTargetRef = if ($remoteTargetExists) { "$Remote/$TargetBranch" } else { $localTargetRef }

Write-Host ("Target: {0} (local check), {1} (remote check)" -f $localTargetRef, $remoteTargetRef) -ForegroundColor White
Write-Host ("Current: {0}" -f $currentBranch) -ForegroundColor White
Write-Host ""

$reportLines = New-Object System.Collections.Generic.List[string]
$reportLines.Add("# Branch Cleanup Report") | Out-Null
$reportLines.Add("") | Out-Null
$reportLines.Add(("Generated: {0}" -f (Get-Date -Format "yyyy-MM-dd HH:mm:ss"))) | Out-Null
$reportLines.Add(('Target branch: `{0}`' -f $TargetBranch)) | Out-Null
$reportLines.Add(('Current branch: `{0}`' -f $currentBranch)) | Out-Null
$reportLines.Add("") | Out-Null

function Add-Section {
    param([string]$Title)
    $reportLines.Add(("## {0}" -f $Title)) | Out-Null
    $reportLines.Add("") | Out-Null
}

function Add-Bullet {
    param([string]$Text)
    $reportLines.Add(("- {0}" -f $Text)) | Out-Null
}

if ($Scope -eq "all" -or $Scope -eq "local") {
    Add-Section "Local branches"

    $localBranches = & git for-each-ref refs/heads --format="%(refname:short)" 2>$null
    $localMerged = @()
    $localNeedsReview = @()
    $localProtected = @()

    foreach ($b in $localBranches) {
        $b = $b.Trim()
        if (-not $b) { continue }

        $isProtected = (
            ($b -eq $currentBranch) -or
            (Should-KeepBranch -BranchName $b -Patterns $keep) -or
            $worktreeBranches.Contains($b)
        )

        if ($isProtected) {
            $reason = if ($b -eq $currentBranch) { "current" } elseif ($worktreeBranches.Contains($b)) { "worktree" } else { "keep-pattern" }
            $localProtected += [PSCustomObject]@{ Branch = $b; Reason = $reason }
            continue
        }

        if (Test-IsAncestor -Ancestor $b -Descendant $localTargetRef) {
            $localMerged += $b
            continue
        }

        $div = Get-DivergenceCounts -TargetRef $localTargetRef -BranchRef $b
        $localNeedsReview += [PSCustomObject]@{
            Branch = $b
            Behind = if ($div) { $div.Behind } else { $null }
            Ahead  = if ($div) { $div.Ahead } else { $null }
        }
    }

    Add-Bullet ("Protected (not touched): {0}" -f ($localProtected.Count))
    foreach ($p in $localProtected | Sort-Object Branch) {
        Add-Bullet ('`{0}` ({1})' -f $p.Branch, $p.Reason)
    }

    $reportLines.Add("") | Out-Null
    Add-Bullet ("Merged into target (safe delete): {0}" -f ($localMerged.Count))
    foreach ($b in ($localMerged | Sort-Object)) {
        Add-Bullet ('`{0}`' -f $b)
    }

    $reportLines.Add("") | Out-Null
    Add-Bullet ("Not merged (needs review): {0}" -f ($localNeedsReview.Count))
    foreach ($b in ($localNeedsReview | Sort-Object Branch)) {
        if ($null -ne $b.Behind -and $null -ne $b.Ahead) {
            Add-Bullet ('`{0}` (behind {1}, ahead {2})' -f $b.Branch, $b.Behind, $b.Ahead)
        } else {
            Add-Bullet ('`{0}`' -f $b.Branch)
        }
    }

    if ($Apply) {
        Write-Host "Local deletions:" -ForegroundColor Cyan
        foreach ($b in ($localMerged | Sort-Object)) {
            $flag = if ($Force) { "-D" } else { "-d" }
            Write-Host ("  git branch {0} {1}" -f $flag, $b) -ForegroundColor DarkGray
            & git branch $flag $b | Out-Null
        }
        Write-Host ""
    }
}

if ($Scope -eq "all" -or $Scope -eq "remote") {
    Add-Section "Remote branches ($Remote)"

    $remoteRefs = & git for-each-ref ("refs/remotes/$Remote") --format="%(refname)" 2>$null
    $remoteMerged = @()
    $remoteNeedsReview = @()
    $remoteProtected = @()

    foreach ($ref in $remoteRefs) {
        $ref = $ref.Trim()
        if (-not $ref) { continue }
        if (-not ($ref -like "refs/remotes/$Remote/*")) { continue }

        $remoteName = $ref.Substring("refs/remotes/".Length) # e.g. origin/main, origin/HEAD
        $branchName = $remoteName.Substring(($Remote + "/").Length) # e.g. main, HEAD

        if ($branchName -eq "HEAD") {
            $remoteProtected += [PSCustomObject]@{ Branch = $remoteName; Reason = "symbolic" }
            continue
        }

        $isProtected = (
            (Should-KeepBranch -BranchName $branchName -Patterns $keep) -or
            ($branchName -eq $TargetBranch)
        )

        if ($isProtected) {
            $remoteProtected += [PSCustomObject]@{ Branch = $remoteName; Reason = "keep-pattern" }
            continue
        }

        if (Test-IsAncestor -Ancestor $remoteName -Descendant $remoteTargetRef) {
            $remoteMerged += $branchName
            continue
        }

        $div = Get-DivergenceCounts -TargetRef $remoteTargetRef -BranchRef $remoteName
        $remoteNeedsReview += [PSCustomObject]@{
            Branch = $branchName
            Behind = if ($div) { $div.Behind } else { $null }
            Ahead  = if ($div) { $div.Ahead } else { $null }
        }
    }

    Add-Bullet ("Protected (not touched): {0}" -f ($remoteProtected.Count))
    foreach ($p in $remoteProtected | Sort-Object Branch) {
        Add-Bullet ('`{0}` ({1})' -f $p.Branch, $p.Reason)
    }

    $reportLines.Add("") | Out-Null
    Add-Bullet ("Merged into target (safe delete): {0}" -f ($remoteMerged.Count))
    foreach ($b in ($remoteMerged | Sort-Object)) {
        Add-Bullet ('`{0}`' -f $b)
    }

    $reportLines.Add("") | Out-Null
    Add-Bullet ("Not merged (needs review): {0}" -f ($remoteNeedsReview.Count))
    foreach ($b in ($remoteNeedsReview | Sort-Object Branch)) {
        if ($null -ne $b.Behind -and $null -ne $b.Ahead) {
            Add-Bullet ('`{0}` (behind {1}, ahead {2})' -f $b.Branch, $b.Behind, $b.Ahead)
        } else {
            Add-Bullet ('`{0}`' -f $b.Branch)
        }
    }

    if ($Apply) {
        Write-Host "Remote deletions:" -ForegroundColor Cyan
        foreach ($b in ($remoteMerged | Sort-Object)) {
            Write-Host ("  git push {0} --delete {1}" -f $Remote, $b) -ForegroundColor DarkGray
            & git push $Remote --delete $b | Out-Null
        }
        Write-Host ""
    }
}

Write-Host "Report summary:" -ForegroundColor Cyan
Write-Host ("  Local target:  {0}" -f $localTargetRef) -ForegroundColor DarkGray
Write-Host ("  Remote target: {0}" -f $remoteTargetRef) -ForegroundColor DarkGray
Write-Host ""

$reportText = $reportLines -join "`r`n"
if ($ReportPath) {
    $outPath = Join-Path $repoRoot $ReportPath
    $reportText | Out-File -FilePath $outPath -Encoding UTF8
    Write-Host ("Wrote: {0}" -f $outPath) -ForegroundColor Green
} else {
    Write-Output $reportText
}
