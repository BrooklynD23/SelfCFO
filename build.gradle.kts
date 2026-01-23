plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose) apply false
    alias(libs.plugins.sqldelight) apply false

    // Development Tooling - using version catalog for consistency
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    alias(libs.plugins.dokka)
}

allprojects {
    repositories {
        google()
        mavenCentral()
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
        maven("https://jitpack.io") // For SQLCipher JDBC and other GitHub packages
    }
}

// Configure ktlint for all subprojects
subprojects {
    apply(plugin = "org.jlleitschuh.gradle.ktlint")
    apply(plugin = "io.gitlab.arturbosch.detekt")
    apply(plugin = "org.jetbrains.dokka")

    configure<org.jlleitschuh.gradle.ktlint.KtlintExtension> {
        version.set("1.1.1")
        android.set(true)
        outputColorName.set("RED")
        ignoreFailures.set(false)

        filter {
            exclude("**/generated/**")
            include("**/kotlin/**")
        }
    }

    detekt {
        buildUponDefaultConfig = true
        config.setFrom(files("$rootDir/config/detekt/detekt.yml"))
    }
}

// Dokka multi-module documentation
//
// Use type-based configuration so builds don't fail if the specific task name
// isn't registered by the applied Dokka plugin/version in a given environment.
tasks.withType<org.jetbrains.dokka.gradle.DokkaMultiModuleTask>().configureEach {
    outputDirectory.set(layout.buildDirectory.dir("dokka"))
}
