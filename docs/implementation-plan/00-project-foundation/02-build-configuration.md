# 02: Build Configuration

## Overview

Configure the multi-platform build system for Android APK and Desktop (Windows/macOS/Linux) distributions.

---

## Implementation Steps

### Step 1: Configure Gradle Properties

```properties
# gradle.properties
org.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8
org.gradle.parallel=true
org.gradle.caching=true

# Android
android.useAndroidX=true
android.nonTransitiveRClass=true

# Kotlin
kotlin.code.style=official
kotlin.mpp.stability.nowarn=true
kotlin.mpp.androidSourceSetLayoutVersion=2

# Compose
org.jetbrains.compose.experimental.jscanvas.enabled=false
org.jetbrains.compose.experimental.macos.enabled=true
```

### Step 2: Version Catalog Setup

```toml
# gradle/libs.versions.toml
[versions]
kotlin = "1.9.22"
agp = "8.2.0"
compose = "1.5.12"
sqldelight = "2.0.1"
coroutines = "1.7.3"
serialization = "1.6.2"
datetime = "0.5.0"
androidx-core = "1.12.0"
androidx-activity = "1.8.2"
androidx-lifecycle = "2.7.0"

[libraries]
kotlinx-coroutines-core = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-core", version.ref = "coroutines" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "coroutines" }
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "serialization" }
kotlinx-datetime = { module = "org.jetbrains.kotlinx:kotlinx-datetime", version.ref = "datetime" }
androidx-core-ktx = { module = "androidx.core:core-ktx", version.ref = "androidx-core" }
androidx-activity-compose = { module = "androidx.activity:activity-compose", version.ref = "androidx-activity" }
androidx-lifecycle-viewmodel = { module = "androidx.lifecycle:lifecycle-viewmodel-compose", version.ref = "androidx-lifecycle" }

[plugins]
kotlin-multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
android-application = { id = "com.android.application", version.ref = "agp" }
android-library = { id = "com.android.library", version.ref = "agp" }
compose = { id = "org.jetbrains.compose", version.ref = "compose" }
sqldelight = { id = "app.cash.sqldelight", version.ref = "sqldelight" }
```

### Step 3: Android Build Variants

```kotlin
// android/build.gradle.kts
android {
    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions += "environment"
    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".dev"
            versionNameSuffix = "-dev"
        }
        create("prod") {
            dimension = "environment"
        }
    }
}
```

### Step 4: Desktop Packaging Configuration

```kotlin
// desktop/build.gradle.kts
compose.desktop {
    application {
        mainClass = "com.ledgerlens.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Rpm
            )

            packageName = "LedgerLens"
            packageVersion = "1.0.0"
            description = "Local-first personal finance application"
            copyright = "2026 LedgerLens"
            vendor = "LedgerLens"

            windows {
                menuGroup = "LedgerLens"
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
                iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }

            macOS {
                bundleID = "com.ledgerlens.desktop"
                iconFile.set(project.file("src/main/resources/icons/icon.icns"))
                dmgPackageVersion = "1.0.0"
                pkgPackageVersion = "1.0.0"
            }

            linux {
                iconFile.set(project.file("src/main/resources/icons/icon.png"))
                debMaintainer = "support@ledgerlens.com"
                menuGroup = "Office;Finance"
                appCategory = "Finance"
            }
        }

        buildTypes.release {
            proguard {
                configurationFiles.from("proguard-rules.pro")
            }
        }
    }
}
```

### Step 5: ProGuard Rules

```proguard
# android/proguard-rules.pro and desktop/proguard-rules.pro

# Keep Kotlin metadata
-keep class kotlin.Metadata { *; }

# Keep serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep SQLDelight
-keep class com.ledgerlens.db.** { *; }

# Keep Compose
-keep class androidx.compose.** { *; }
```

### Step 6: Signing Configuration

```kotlin
// android/build.gradle.kts
android {
    signingConfigs {
        create("release") {
            // Load from environment or local.properties
            val keystorePath = System.getenv("KEYSTORE_PATH")
                ?: project.findProperty("KEYSTORE_PATH")?.toString()
            val keystorePassword = System.getenv("KEYSTORE_PASSWORD")
                ?: project.findProperty("KEYSTORE_PASSWORD")?.toString()
            val keyAlias = System.getenv("KEY_ALIAS")
                ?: project.findProperty("KEY_ALIAS")?.toString()
            val keyPassword = System.getenv("KEY_PASSWORD")
                ?: project.findProperty("KEY_PASSWORD")?.toString()

            if (keystorePath != null && keystorePassword != null) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                this.keyAlias = keyAlias
                this.keyPassword = keyPassword
            }
        }
    }
}
```

---

## Acceptance Criteria

- [ ] Version catalog set up with all dependencies
- [ ] Android debug and release build variants work
- [ ] Desktop builds generate installers for Windows, macOS, Linux
- [ ] ProGuard/R8 configured for release builds
- [ ] Signing configuration ready (placeholder for keys)
- [ ] Build completes without warnings

---

## Dependencies

- Gradle 8.x
- JDK 17+
- Android SDK 34

---

## Estimated Complexity

**Medium** - Multiple platform configurations with signing setup.

---

## Verification Commands

```bash
# Build release APK
./gradlew :android:assembleRelease

# Build desktop installers
./gradlew :desktop:packageDmg      # macOS
./gradlew :desktop:packageMsi      # Windows
./gradlew :desktop:packageDeb      # Debian/Ubuntu

# List available tasks
./gradlew tasks --group=build
```
