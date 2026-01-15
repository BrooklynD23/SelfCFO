# 01: KMP Project Setup

## Overview

Set up the Kotlin Multiplatform project structure per ADR-001, establishing the foundation for shared code between Android and Desktop platforms.

**Reference:** [ADR-001: Shared Core Technology](../../PRDs/13-architecture-decision-records.md#adr-001-shared-core-technology)

---

## Implementation Steps

### Step 1: Initialize Project Structure

Create the following directory structure:

```
LedgerLens/
├── build.gradle.kts              # Root build file
├── settings.gradle.kts           # Module configuration
├── gradle.properties             # Gradle settings
├── shared/                       # KMP shared module
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/
│       │   └── kotlin/
│       │       └── com/ledgerlens/
│       │           ├── domain/       # Entities and use cases
│       │           ├── data/         # Repositories
│       │           └── services/     # Business logic
│       ├── commonTest/
│       │   └── kotlin/
│       ├── androidMain/
│       │   └── kotlin/
│       ├── androidUnitTest/
│       │   └── kotlin/
│       ├── desktopMain/
│       │   └── kotlin/
│       └── desktopTest/
│           └── kotlin/
├── android/                      # Android app module
│   ├── build.gradle.kts
│   └── src/
│       └── main/
│           ├── kotlin/
│           │   └── com/ledgerlens/android/
│           ├── res/
│           └── AndroidManifest.xml
└── desktop/                      # Desktop app module
    ├── build.gradle.kts
    └── src/
        └── main/
            └── kotlin/
                └── com/ledgerlens/desktop/
```

**Add JVM shared source sets (recommended):**
- `shared/src/jvmMain/kotlin/` for shared JVM-only implementations (`java.*`) used by both Android + Desktop
- `shared/src/jvmTest/kotlin/` for shared JVM-only tests (if needed)

### Step 2: Configure Root Build File

```kotlin
// build.gradle.kts (root)
plugins {
    kotlin("multiplatform") version "1.9.22" apply false
    kotlin("android") version "1.9.22" apply false
    id("com.android.application") version "8.2.0" apply false
    id("com.android.library") version "8.2.0" apply false
    id("org.jetbrains.compose") version "1.5.12" apply false
    id("app.cash.sqldelight") version "2.0.1" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}
```

### Step 3: Configure Settings

```kotlin
// settings.gradle.kts
rootProject.name = "LedgerLens"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}

include(":shared")
include(":android")
include(":desktop")
```

### Step 4: Configure Shared Module

```kotlin
// shared/build.gradle.kts
plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    kotlin("plugin.serialization") version "1.9.22"
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }

    jvm("desktop") {
        compilations.all {
            kotlinOptions.jvmTarget = "17"
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)

                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")

                // DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
            }
        }

        /**
         * JVM-only shared code used by both Android and Desktop.
         *
         * Use this for `java.*` APIs (file IO, crypto, formatting, etc).
         * Keep `commonMain` free of `java.*` and `android.*` to preserve portability.
         */
        val jvmMain by creating {
            dependsOn(commonMain)
        }

        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
            }
        }

        val androidMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(compose.desktop.currentOs)
            }
        }
    }
}

android {
    namespace = "com.ledgerlens.shared"
    compileSdk = 34

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("LedgerLensDatabase") {
            packageName.set("com.ledgerlens.db")
            schemaOutputDirectory.set(file("src/commonMain/sqldelight/databases"))
        }
    }
}
```

### Step 5: Configure Android App Module

```kotlin
// android/build.gradle.kts
plugins {
    id("com.android.application")
    kotlin("android")
    id("org.jetbrains.compose")
}

android {
    namespace = "com.ledgerlens.android"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ledgerlens"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
}

dependencies {
    implementation(project(":shared"))

    implementation("androidx.activity:activity-compose:1.8.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
}
```

### Step 6: Configure Desktop App Module

```kotlin
// desktop/build.gradle.kts
plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
}

compose.desktop {
    application {
        mainClass = "com.ledgerlens.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Dmg,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Msi,
                org.jetbrains.compose.desktop.application.dsl.TargetFormat.Deb
            )
            packageName = "LedgerLens"
            packageVersion = "1.0.0"

            windows {
                menuGroup = "LedgerLens"
                upgradeUuid = "unique-uuid-here"
            }

            macOS {
                bundleID = "com.ledgerlens"
            }
        }
    }
}
```

### Step 7: Create Entry Points

**Android MainActivity:**
```kotlin
// android/src/main/kotlin/com/ledgerlens/android/MainActivity.kt
package com.ledgerlens.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.ledgerlens.App

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            App()
        }
    }
}
```

**Desktop Main:**
```kotlin
// desktop/src/main/kotlin/com/ledgerlens/desktop/Main.kt
package com.ledgerlens.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.ledgerlens.App

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "LedgerLens"
    ) {
        App()
    }
}
```

**Shared App Composable:**
```kotlin
// shared/src/commonMain/kotlin/com/ledgerlens/App.kt
package com.ledgerlens

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

@Composable
fun App() {
    MaterialTheme {
        Surface {
            Text("LedgerLens")
        }
    }
}
```

---

## Acceptance Criteria

- [x] Project builds successfully: `./gradlew build`
- [x] Android app runs on emulator/device
- [x] Desktop app runs on development machine
- [x] Shared code compiles for both targets
- [x] Source sets properly isolated (commonMain, androidMain, desktopMain)
- [x] SQLDelight plugin configured (schema generation works)
- [x] Compose Multiplatform UI renders on both platforms
- [x] Version catalog used for all dependencies (post-review fix)
- [x] Test source sets configured with proper dependencies

---

## Dependencies

**Gradle Plugins:**
- Kotlin Multiplatform 1.9.22+
- Android Gradle Plugin 8.2.0+
- Compose Multiplatform 1.5.12+
- SQLDelight 2.0.1+

**Libraries:**
- Kotlinx Coroutines 1.7.3+
- Kotlinx Serialization 1.6.2+
- Kotlinx DateTime 0.5.0+
- AndroidX Core KTX 1.12.0+

---

## Estimated Complexity

**Medium** - Requires understanding of KMP configuration and Gradle multi-module setup.

---

## Verification Commands

```bash
# Build all platforms
./gradlew build

# Run Android app
./gradlew :android:installDebug

# Run Desktop app
./gradlew :desktop:run

# Run tests
./gradlew check
```
