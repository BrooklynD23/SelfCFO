import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)

    // Coroutines for desktop
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)

    // Testing
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

compose.desktop {
    application {
        mainClass = "com.ledgerlens.desktop.MainKt"

        nativeDistributions {
            targetFormats(
                TargetFormat.Dmg,
                TargetFormat.Msi,
                TargetFormat.Deb,
                TargetFormat.Rpm
            )

            packageName = "LedgerLens"
            packageVersion = "1.0.0"
            description = "Local-first personal finance application"
            copyright = "2026 LedgerLens"
            vendor = "LedgerLens"

            // Windows-specific configuration
            windows {
                menuGroup = "LedgerLens"
                perUserInstall = true
                dirChooser = true
                upgradeUuid = "b8f7c9d2-1e4a-4f6b-9c3d-5a2e8f7b1c0d"
                // Uncomment when icon is available
                // iconFile.set(project.file("src/main/resources/icons/icon.ico"))
            }

            // macOS-specific configuration
            macOS {
                bundleID = "com.ledgerlens.desktop"
                dmgPackageVersion = "1.0.0"
                pkgPackageVersion = "1.0.0"
                // Uncomment when icon is available
                // iconFile.set(project.file("src/main/resources/icons/icon.icns"))
            }

            // Linux-specific configuration
            linux {
                debMaintainer = "support@ledgerlens.com"
                menuGroup = "Office;Finance"
                appCategory = "Finance"
                // Uncomment when icon is available
                // iconFile.set(project.file("src/main/resources/icons/icon.png"))
            }

            // Module configuration for JVM 17+
            modules("java.sql", "java.naming")
        }

        // Release build with ProGuard
        buildTypes.release {
            proguard {
                configurationFiles.from("proguard-rules.pro")
                isEnabled.set(true)
            }
        }
    }
}

// JVM target configuration
kotlin {
    jvmToolchain(17)
}
