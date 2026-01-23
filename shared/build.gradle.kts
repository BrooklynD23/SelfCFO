plugins {
    kotlin("multiplatform")
    id("com.android.library")
    id("org.jetbrains.compose")
    alias(libs.plugins.kotlin.serialization)
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
                implementation(compose.materialIconsExtended)

                // Coroutines - using version catalog
                implementation(libs.kotlinx.coroutines.core)

                // Serialization - using version catalog
                implementation(libs.kotlinx.serialization.json)

                // DateTime - using version catalog
                implementation(libs.kotlinx.datetime)

                // SQLDelight runtime
                implementation(libs.sqldelight.runtime)
                implementation(libs.sqldelight.coroutines)

                // Koin - Dependency Injection
                implementation(libs.koin.core)
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
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.turbine)
                implementation(libs.koin.test)
            }
        }

        val jvmTest by creating {
            dependsOn(commonTest)
            dependencies {
                implementation(libs.junit5.api)
                runtimeOnly(libs.junit5.engine)
                implementation(libs.mockk)
            }
        }

        val androidMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(libs.androidx.core.ktx)
                // SQLDelight Android driver
                implementation(libs.sqldelight.android.driver)
                // SQLCipher for encrypted database
                implementation(libs.sqlcipher.android)
                // AndroidX Security for EncryptedSharedPreferences
                implementation(libs.androidx.security.crypto)
                // Koin Android
                implementation(libs.koin.android)
            }
        }

        val androidUnitTest by getting {
            dependsOn(jvmTest)
            dependencies {
                implementation(libs.junit)
                implementation(libs.kotlin.test.junit)
            }
        }

        val desktopMain by getting {
            dependsOn(jvmMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                // SQLDelight JVM/Desktop driver
                implementation(libs.sqldelight.sqlite.driver)
                // Note: Desktop encryption deferred - SQLCipher JDBC not readily available
                // TODO: Add desktop-compatible encryption in future sprint
            }
        }

        val desktopTest by getting {
            dependsOn(jvmTest)
            dependencies {
                implementation(libs.mockk)
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

// Dokka documentation configuration
tasks.withType<org.jetbrains.dokka.gradle.DokkaTask>().configureEach {
    dokkaSourceSets {
        named("commonMain") {
            displayName.set("Common")
            platform.set(org.jetbrains.dokka.Platform.common)
        }
        named("androidMain") {
            displayName.set("Android")
            platform.set(org.jetbrains.dokka.Platform.jvm)
        }
        named("desktopMain") {
            displayName.set("Desktop")
            platform.set(org.jetbrains.dokka.Platform.jvm)
        }
    }
}
