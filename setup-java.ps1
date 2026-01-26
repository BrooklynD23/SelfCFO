Set-StrictMode -Version Latest

# Setup Java for Gradle - LedgerLens Project (Windows)
# - Finds JDK installs (prefers JDK 17)
# - Sets JAVA_HOME for the current PowerShell session
# - Optionally prepends %JAVA_HOME%\bin to PATH (current session)

Write-Host "=== Java Setup for LedgerLens ===" -ForegroundColor Cyan
Write-Host ""

function Test-JavaHome($path) {
    if (-not $path) { return $false }
    return (Test-Path (Join-Path $path "bin\java.exe"))
}

# If JAVA_HOME already set and valid, just verify.
if (Test-JavaHome $env:JAVA_HOME) {
    Write-Host "JAVA_HOME is already set:" -ForegroundColor Green
    Write-Host "  $env:JAVA_HOME" -ForegroundColor White
    & (Join-Path $env:JAVA_HOME "bin\java.exe") -version
    Write-Host ""
    Write-Host "You're good to run:" -ForegroundColor Cyan
    Write-Host "  .\gradlew.bat check" -ForegroundColor White
    exit 0
}

Write-Host "Searching for Java installations..." -ForegroundColor Cyan

$programFilesX86 = ${env:ProgramFiles(x86)}

$roots = @(
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Microsoft",
    "C:\Program Files\Amazon Corretto",
    "C:\Program Files\Android\Android Studio\jbr",
    "$env:LOCALAPPDATA\Programs",
    "$env:ProgramFiles\Java",
    (Join-Path $programFilesX86 "Java")
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique

$candidates = @()

foreach ($root in $roots) {
    Get-ChildItem $root -Directory -ErrorAction SilentlyContinue | ForEach-Object {
        $javaExe = Join-Path $_.FullName "bin\java.exe"
        if (Test-Path $javaExe) {
            $verLine = (& $javaExe -version 2>&1 | Select-Object -First 1)
            $candidates += [PSCustomObject]@{
                Path    = $_.FullName
                Version = $verLine
            }
        }
    }
}

if ($candidates.Count -eq 0) {
    Write-Host ""
    Write-Host "No Java installations found in common locations." -ForegroundColor Red
    Write-Host ""
    Write-Host "Install JDK 17, then re-run this script." -ForegroundColor Yellow
    Write-Host "  - Eclipse Temurin: https://adoptium.net/temurin/releases/?version=17" -ForegroundColor White
    Write-Host "  - Microsoft OpenJDK: https://learn.microsoft.com/en-us/java/openjdk/download" -ForegroundColor White
    Write-Host ""
    Write-Host "Manual (current session):" -ForegroundColor Yellow
    Write-Host "  `$env:JAVA_HOME = ""C:\Program Files\Java\jdk-17""" -ForegroundColor White
    exit 1
}

Write-Host ""
Write-Host "Found Java installation(s):" -ForegroundColor Green
$i = 1
foreach ($c in $candidates) {
    Write-Host ("  [{0}] {1}" -f $i, $c.Path) -ForegroundColor White
    Write-Host ("      {0}" -f $c.Version) -ForegroundColor DarkGray
    $i++
}

# Prefer JDK 17 if present; else first candidate.
# Prefer JDK 17, then JDK 21, else first candidate.
$selected = $candidates | Where-Object { $_.Version -match 'version \"17' -or $_.Path -match 'jdk-?17|\\17($|\\)' } | Select-Object -First 1
if (-not $selected) {
    $selected = $candidates | Where-Object { $_.Version -match 'version \"21' -or $_.Path -match 'jdk-?21|\\21($|\\)' } | Select-Object -First 1
}
if (-not $selected) { $selected = $candidates[0] }

$env:JAVA_HOME = $selected.Path
$env:Path = (Join-Path $env:JAVA_HOME "bin") + ";" + $env:Path

Write-Host ""
Write-Host "JAVA_HOME set for this session:" -ForegroundColor Green
Write-Host "  $env:JAVA_HOME" -ForegroundColor White

Write-Host ""
Write-Host "Verifying Java..." -ForegroundColor Cyan
& (Join-Path $env:JAVA_HOME "bin\java.exe") -version

Write-Host ""
Write-Host "Testing Gradle wrapper..." -ForegroundColor Cyan
if (Test-Path ".\gradlew.bat") {
    & .\gradlew.bat --version
} else {
    Write-Host "gradlew.bat not found. Run this script from the repo root." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Done." -ForegroundColor Green
Write-Host ""
Write-Host "If you open a new terminal, re-run:" -ForegroundColor Cyan
Write-Host ("  `$env:JAVA_HOME = ""{0}""" -f $env:JAVA_HOME) -ForegroundColor White
Write-Host ""
Write-Host "Persist for your user (new shells):" -ForegroundColor Cyan
Write-Host ("  [System.Environment]::SetEnvironmentVariable('JAVA_HOME','{0}','User')" -f $env:JAVA_HOME) -ForegroundColor White
Write-Host "  # then restart PowerShell" -ForegroundColor DarkGray
Write-Host ""
Write-Host "Or set JAVA_HOME permanently via Windows Environment Variables UI." -ForegroundColor Cyan
