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
                upgradeUuid = "b8f7c9d2-1e4a-4f6b-9c3d-5a2e8f7b1c0d"
            }

            macOS {
                bundleID = "com.ledgerlens"
            }
        }
    }
}
