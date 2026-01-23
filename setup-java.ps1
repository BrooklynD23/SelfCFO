# Setup Java for Gradle - LedgerLens Project
# This script helps find and set JAVA_HOME for this project

Write-Host "=== Java Setup for LedgerLens ===" -ForegroundColor Cyan
Write-Host ""

# Check if JAVA_HOME is already set
if ($env:JAVA_HOME) {
    Write-Host "JAVA_HOME is currently set to: $env:JAVA_HOME" -ForegroundColor Yellow
    if (Test-Path "$env:JAVA_HOME\bin\java.exe") {
        Write-Host "✓ Java found at JAVA_HOME" -ForegroundColor Green
        & "$env:JAVA_HOME\bin\java.exe" -version
        Write-Host ""
        Write-Host "JAVA_HOME is already configured correctly!" -ForegroundColor Green
        exit 0
    } else {
        Write-Host "✗ Java not found at JAVA_HOME path" -ForegroundColor Red
    }
}

# Search common installation locations
Write-Host "Searching for Java installations..." -ForegroundColor Cyan

$searchPaths = @(
    "C:\Program Files\Java",
    "C:\Program Files\Eclipse Adoptium",
    "C:\Program Files\Microsoft",
    "C:\Program Files\Amazon Corretto",
    "$env:LOCALAPPDATA\Programs",
    "$env:ProgramFiles\Java",
    "$env:ProgramFiles(x86)\Java"
)

$foundJavas = @()

foreach ($path in $searchPaths) {
    if (Test-Path $path) {
        $jdkDirs = Get-ChildItem $path -Directory -ErrorAction SilentlyContinue | 
            Where-Object { $_.Name -match 'jdk|java|openjdk|temurin|adoptium|corretto' }
        
        foreach ($jdkDir in $jdkDirs) {
            $javaExe = Join-Path $jdkDir.FullName "bin\java.exe"
            if (Test-Path $javaExe) {
                $version = & $javaExe -version 2>&1 | Select-Object -First 1
                $foundJavas += [PSCustomObject]@{
                    Path = $jdkDir.FullName
                    Version = $version
                }
            }
        }
    }
}

if ($foundJavas.Count -eq 0) {
    Write-Host ""
    Write-Host "✗ No Java installations found in common locations." -ForegroundColor Red
    Write-Host ""
    Write-Host "Please install JDK 17 from one of these sources:" -ForegroundColor Yellow
    Write-Host "  - Eclipse Temurin: https://adoptium.net/temurin/releases/?version=17" -ForegroundColor Cyan
    Write-Host "  - Microsoft Build of OpenJDK: https://learn.microsoft.com/en-us/java/openjdk/download" -ForegroundColor Cyan
    Write-Host "  - Amazon Corretto: https://aws.amazon.com/corretto/" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "After installation, run this script again or set JAVA_HOME manually:" -ForegroundColor Yellow
    Write-Host '  $env:JAVA_HOME = "C:\Program Files\Java\jdk-17"' -ForegroundColor White
    exit 1
}

# Display found Java installations
Write-Host ""
Write-Host "Found Java installation(s):" -ForegroundColor Green
for ($i = 0; $i -lt $foundJavas.Count; $i++) {
    Write-Host "  [$($i + 1)] $($foundJavas[$i].Path)" -ForegroundColor Cyan
    Write-Host "      $($foundJavas[$i].Version)" -ForegroundColor Gray
}

# Auto-select JDK 17 if available, otherwise use first found
$selectedJava = $null
foreach ($java in $foundJavas) {
    if ($java.Version -match 'version "17' -or $java.Path -match '17|jdk-17') {
        $selectedJava = $java
        break
    }
}

if (-not $selectedJava) {
    $selectedJava = $foundJavas[0]
    Write-Host ""
    Write-Host "⚠ Warning: JDK 17 not found. Using: $($selectedJava.Path)" -ForegroundColor Yellow
    Write-Host "  Gradle 8.5 requires JDK 17+. Please install JDK 17 if possible." -ForegroundColor Yellow
}

# Set JAVA_HOME for current session
$env:JAVA_HOME = $selectedJava.Path
Write-Host ""
Write-Host "✓ JAVA_HOME set to: $env:JAVA_HOME" -ForegroundColor Green

# Verify
Write-Host ""
Write-Host "Verifying Java installation..." -ForegroundColor Cyan
& "$env:JAVA_HOME\bin\java.exe" -version

# Test Gradle
Write-Host ""
Write-Host "Testing Gradle..." -ForegroundColor Cyan
if (Test-Path ".\gradlew.bat") {
    & .\gradlew.bat --version
} else {
    Write-Host "gradlew.bat not found. Make sure you're in the project root." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Setup Complete ===" -ForegroundColor Green
Write-Host ""
Write-Host "JAVA_HOME is set for this PowerShell session." -ForegroundColor Cyan
Write-Host ""
Write-Host "To make this permanent, set JAVA_HOME system-wide:" -ForegroundColor Yellow
Write-Host "  1. Open 'Environment Variables' in Windows Settings" -ForegroundColor White
Write-Host "  2. Add JAVA_HOME = $env:JAVA_HOME" -ForegroundColor White
Write-Host "  3. Add %JAVA_HOME%\bin to PATH" -ForegroundColor White
Write-Host ""
Write-Host "Or run this command in each new PowerShell session:" -ForegroundColor Yellow
Write-Host "  `$env:JAVA_HOME = `"$env:JAVA_HOME`"" -ForegroundColor White
