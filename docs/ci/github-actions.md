# GitHub Actions CI

This repo uses GitHub Actions workflows under `.github/workflows/`.

## Checks

The primary CI workflow runs three logical groups:

- `build`: `./gradlew check`, `:shared:build`, and `:desktop:build` (matrix across OS)
- `android`: `:android:assembleDevDebug` and `:android:testDevDebugUnitTest`
- `lint`: `ktlintCheck` and `detekt`

## What changed (Jan 28, 2026)

Recent CI failures were caused by a combination of formatting/lint issues, compilation issues, and flaky/host-dependent tests. Fixes included:

- **Formatting**: ran `ktlintFormat` and fixed remaining style violations so `ktlintCheck` passes.
- **Detekt**: refactored `MainActivity` and desktop `Main.kt` to satisfy detekt rules (function length/complexity, naming, and forbidden `TODO` comments).
- **Shared UI compilation**: re-enabled `com/ledgerlens/ui/**` sources in `shared/build.gradle.kts` so app modules can compile UI code again.
- **Compose Material3 compatibility**: replaced `HorizontalDivider` usages with `Divider`, updated `LinearProgressIndicator` usage, and added missing `@OptIn(ExperimentalMaterial3Api::class)` in `RulesScreen`.
- **Android app dependencies**: added `koin` dependency to the Android app module since `MainActivity` uses Koin APIs directly.
- **Kotlin compiler stability**: set `kotlin.compiler.execution.strategy=in-process` in `gradle.properties` to avoid Kotlin daemon connection issues in constrained environments/CI.
- **Unit tests**:
  - Added an in-memory `PlatformKeystore` for common tests and used it from both Android unit tests and desktop tests.
  - Made desktop DB tests hermetic by allowing `DatabaseDriverFactory` to use an override app-data directory via:
    - system property `ledgerlens.appDataDir`, or
    - environment variable `LEDGERLENS_APPDATA_DIR`

## Running locally

Common commands:

```bash
./gradlew ktlintCheck
./gradlew detekt
./gradlew check
./gradlew :android:assembleDevDebug :android:testDevDebugUnitTest
./gradlew :desktop:build
```

If you need to redirect desktop DB storage (useful for tests or sandboxed environments):

```bash
./gradlew :desktop:run -Dledgerlens.appDataDir=/path/to/writable/dir
```

