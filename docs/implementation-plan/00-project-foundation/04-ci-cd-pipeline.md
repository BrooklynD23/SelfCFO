# 04: CI/CD Pipeline

## Overview

Set up GitHub Actions workflow for automated building, testing, and artifact publishing.

---

## Implementation Steps

### Step 1: Create Main CI Workflow

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

env:
  GRADLE_OPTS: "-Dorg.gradle.daemon=false"

jobs:
  build:
    strategy:
      matrix:
        os: [ubuntu-latest, macos-latest, windows-latest]
    runs-on: ${{ matrix.os }}

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2
        with:
          cache-read-only: ${{ github.ref != 'refs/heads/main' }}

      - name: Run checks
        run: ./gradlew check

      - name: Build shared module
        run: ./gradlew :shared:build

      - name: Build desktop app
        if: matrix.os != 'ubuntu-latest' || github.ref == 'refs/heads/main'
        run: ./gradlew :desktop:build

      - name: Upload test results
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-results-${{ matrix.os }}
          path: '**/build/reports/tests/'
          retention-days: 7

  android:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Build Android debug APK
        run: ./gradlew :android:assembleDebug

      - name: Run Android unit tests
        run: ./gradlew :android:testDebugUnitTest

      - name: Upload APK
        uses: actions/upload-artifact@v4
        with:
          name: android-debug-apk
          path: android/build/outputs/apk/debug/*.apk
          retention-days: 7

  lint:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/gradle-build-action@v2

      - name: Run ktlint
        run: ./gradlew ktlintCheck

      - name: Run detekt
        run: ./gradlew detekt

      - name: Upload detekt report
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: detekt-report
          path: '**/build/reports/detekt/'
          retention-days: 7
```

### Step 2: Create Release Workflow

```yaml
# .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release-android:
    runs-on: ubuntu-latest

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Decode keystore
        env:
          KEYSTORE_BASE64: ${{ secrets.KEYSTORE_BASE64 }}
        run: echo "$KEYSTORE_BASE64" | base64 -d > keystore.jks

      - name: Build release APK
        env:
          KEYSTORE_PATH: ${{ github.workspace }}/keystore.jks
          KEYSTORE_PASSWORD: ${{ secrets.KEYSTORE_PASSWORD }}
          KEY_ALIAS: ${{ secrets.KEY_ALIAS }}
          KEY_PASSWORD: ${{ secrets.KEY_PASSWORD }}
        run: ./gradlew :android:assembleRelease

      - name: Upload release APK
        uses: actions/upload-artifact@v4
        with:
          name: android-release-apk
          path: android/build/outputs/apk/release/*.apk

  release-desktop:
    strategy:
      matrix:
        include:
          - os: macos-latest
            task: packageDmg
            artifact: '*.dmg'
          - os: windows-latest
            task: packageMsi
            artifact: '*.msi'
          - os: ubuntu-latest
            task: packageDeb
            artifact: '*.deb'

    runs-on: ${{ matrix.os }}

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Build installer
        run: ./gradlew :desktop:${{ matrix.task }}

      - name: Upload installer
        uses: actions/upload-artifact@v4
        with:
          name: desktop-${{ matrix.os }}
          path: desktop/build/compose/binaries/main/*/

  create-release:
    needs: [release-android, release-desktop]
    runs-on: ubuntu-latest

    steps:
      - name: Download all artifacts
        uses: actions/download-artifact@v4
        with:
          path: artifacts

      - name: Create GitHub Release
        uses: softprops/action-gh-release@v1
        with:
          files: artifacts/**/*
          generate_release_notes: true
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

### Step 3: Dependabot Configuration

```yaml
# .github/dependabot.yml
version: 2
updates:
  - package-ecosystem: "gradle"
    directory: "/"
    schedule:
      interval: "weekly"
    open-pull-requests-limit: 5

  - package-ecosystem: "github-actions"
    directory: "/"
    schedule:
      interval: "weekly"
```

### Step 4: Branch Protection Rules

Configure in GitHub repository settings:

```
Branch: main
- Require pull request reviews: 1
- Require status checks:
  - build (ubuntu-latest)
  - build (macos-latest)
  - android
  - lint
- Require branches to be up to date
- Require linear history
```

---

## Acceptance Criteria

- [ ] CI workflow runs on push and PR
- [ ] All platform builds succeed in CI
- [ ] Tests run and report results
- [ ] Lint checks gate PRs
- [ ] Release workflow creates artifacts
- [ ] Dependabot configured
- [ ] Branch protection rules documented

---

## Dependencies

- GitHub Actions
- GitHub Secrets for signing keys

---

## Estimated Complexity

**Medium** - Multi-platform CI configuration.

---

## Required Secrets

| Secret | Description |
|--------|-------------|
| `KEYSTORE_BASE64` | Base64-encoded Android keystore |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Signing key alias |
| `KEY_PASSWORD` | Key password |
