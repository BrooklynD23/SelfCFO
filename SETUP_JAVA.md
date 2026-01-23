# Setting Up Java for Gradle (Windows)

This project requires **JDK 17** to build. Follow these steps to configure Java for Gradle.

## Quick Setup (Recommended)

1. **Run the setup script:**
   ```powershell
   .\setup-java.ps1
   ```

   This script will:
   - Search for Java installations on your system
   - Set `JAVA_HOME` for the current PowerShell session
   - Verify the installation works with Gradle

2. **Test Gradle:**
   ```powershell
   .\gradlew.bat --version
   ```

## Manual Setup

If the script doesn't find Java, or you prefer to set it up manually:

### Step 1: Install JDK 17 (if not installed)

Download JDK 17 from one of these sources:
- **Eclipse Temurin** (recommended): https://adoptium.net/temurin/releases/?version=17
- **Microsoft Build of OpenJDK**: https://learn.microsoft.com/en-us/java/openjdk/download
- **Amazon Corretto**: https://aws.amazon.com/corretto/

### Step 2: Find Your Java Installation

Java is typically installed in one of these locations:
- `C:\Program Files\Java\jdk-17`
- `C:\Program Files\Eclipse Adoptium\jdk-17.x.x-hotspot`
- `C:\Program Files\Microsoft\jdk-17.x.x`
- `C:\Program Files\Amazon Corretto\jdk17.x.x`

### Step 3: Set JAVA_HOME (Temporary - Current Session Only)

In PowerShell:
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
# Replace with your actual Java path
```

Verify it works:
```powershell
$env:JAVA_HOME\bin\java.exe -version
.\gradlew.bat --version
```

### Step 4: Set JAVA_HOME Permanently (Optional but Recommended)

**Option A: Using Windows Settings**
1. Press `Win + X` and select "System"
2. Click "Advanced system settings"
3. Click "Environment Variables"
4. Under "User variables" or "System variables", click "New"
5. Variable name: `JAVA_HOME`
6. Variable value: `C:\Program Files\Java\jdk-17` (your actual path)
7. Click OK

**Option B: Using PowerShell (Run as Administrator)**
```powershell
[System.Environment]::SetEnvironmentVariable('JAVA_HOME', 'C:\Program Files\Java\jdk-17', 'User')
```

**Also add Java to PATH:**
1. In Environment Variables, find "Path" in User variables
2. Click "Edit"
3. Click "New"
4. Add: `%JAVA_HOME%\bin`
5. Click OK on all dialogs

### Step 5: Verify Setup

Close and reopen PowerShell, then run:
```powershell
java -version
echo $env:JAVA_HOME
.\gradlew.bat --version
```

## Project-Specific Setup (Alternative)

If you don't want to set JAVA_HOME system-wide, you can create a local script:

**Create `set-java.ps1` in the project root:**
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"  # Update with your path
```

**Then run before Gradle commands:**
```powershell
.\set-java.ps1
.\gradlew.bat check
```

## Troubleshooting

### "java: command not found"
- Java is not in your PATH
- Set JAVA_HOME and add `%JAVA_HOME%\bin` to PATH (see Step 4 above)

### "JAVA_HOME is not set"
- Run `.\setup-java.ps1` or set JAVA_HOME manually (see Step 3)

### "Unsupported class file major version"
- You're using the wrong Java version
- This project requires JDK 17 (Gradle 8.5 requirement)
- Check: `java -version` should show version 17.x.x

### Gradle still can't find Java
- Make sure JAVA_HOME points to the JDK folder (not JRE)
- The JDK folder should contain `bin\java.exe`
- Restart your terminal/PowerShell after setting environment variables

## Quick Reference

```powershell
# Check if Java is found
java -version

# Check JAVA_HOME
echo $env:JAVA_HOME

# Set JAVA_HOME for current session
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"

# Test Gradle
.\gradlew.bat --version

# Run tests
.\gradlew.bat check
```
