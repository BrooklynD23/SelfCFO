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
        maven("https://jitpack.io") // For SQLCipher JDBC and other GitHub packages
    }
    // libs.versions.toml is automatically loaded by Gradle 8.x from gradle/libs.versions.toml
}

include(":shared")
include(":android")
include(":desktop")
