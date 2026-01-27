## LedgerLens / SelfCFO

**Local-first personal finance intake** that parses messy bank statements and receipts, auto-categorizes transactions with explanations, learns from corrections, and supports item-level bill splitting.

- **Platforms**: Android + Desktop (Compose Multiplatform)
- **Local-first**: Works offline, no account required
- **Auditability**: Categorization and splits are explainable (rules + priors + model signals)

### Project goals (end state)

- **Ingest** PDF/CSV statements from many banks into a canonical ledger (normalize + dedupe)
- **Categorize** transactions with confidence + explanations, improving over time from corrections
- **Split** receipts into line items, assign participants, allocate tax/fees, compute settlements
- **Stay local-first** for MVP, with a clear path to optional encrypted sync later

### Current status

**Overall:** Sprints 00-05 complete. Sprint 06 (Testing & Integration) in progress.  
**Active integration branch:** `sprint04/integration` (pending merge to `main` once CI is green).

#### Implemented (high level)

- **Core data layer**
  - SQLDelight schema (transactions, categories, rules, receipts, imports, stats, etc.)
  - Encryption: Android SQLCipher + envelope encryption for files (desktop DB encryption currently deferred)
  - Money type: integer minor units (no floats)
  - Import pipeline: ImportService (CSV end-to-end today; PDF parser scaffolded), normalization, immutable imported records, SHA-256 file-hash idempotency, FingerprintGenerator-based dedupe, and persistent review/duplicate-candidate queues
- **Categorization engine**
  - Category hierarchy + defaults
  - Hybrid categorization: rules + merchant priors + lightweight ML pipeline
  - Explanations: reasons/factors + confidence levels
  - Learning loop: correction events + incremental learning
- **Receipt splitting**
  - OCR: ML Kit (Android) + Tesseract (Desktop)
  - Item extraction + validation
  - Participants/groups + allocation primitives
- **UI (Compose)**
  - App shell + navigation + screens (Dashboard, Transactions, Import, Review Inbox, Receipts, Categories, Settings)
  - ViewModels with a complete unit test suite (mock/fake-backed during Sprint 06)
- **Data layer integration**
  - SQLDelight repository implementations + mappers + Koin DI modules + repository tests

#### In progress (Sprint 06: Testing & Integration)

- **Wire all ViewModels to real repositories/services** (remove mock generators)
- **Platform verification** (Android emulator + Desktop JVM paths)
- **Performance + polish** (profiling and UX refinement as needed)

#### Todo / upcoming

- **Services layer** (as needed for end-to-end flows): review queue, backup/restore, settings persistence, production correction processing
- **Desktop database encryption** (SQLCipher on Desktop is currently deferred; Android remains encrypted)
- **Quality & scale (Phase 2)**: more bank templates, better transfer detection/dedupe, faster OCR + caching, improved backup/restore
- **Optional cloud sync (later phases)**: encrypted sync + multi-device support (opt-in)

### Documentation

- **Implementation status (source of truth)**: [`IMPLEMENTATION_STATUS.md`](./IMPLEMENTATION_STATUS.md)
- **Branch inventory / next actions**: [`BRANCH_STATUS.md`](./BRANCH_STATUS.md)
- **Product requirements (PRDs)**: [`docs/PRDs/README.md`](./docs/PRDs/README.md)
- **Sprint-by-sprint implementation plan**: [`docs/implementation-plan/README.md`](./docs/implementation-plan/README.md)
- **Architecture audit / guardrails**: [`docs/ARCHITECTURE_AUDIT_REPORT.md`](./docs/ARCHITECTURE_AUDIT_REPORT.md)

### Tech stack

- **Language**: Kotlin (Kotlin Multiplatform)
- **UI**: Compose Multiplatform (Android + Desktop)
- **Database**: SQLite + SQLDelight (Android uses SQLCipher)
- **DI**: Koin
- **Tooling**: ktlint, detekt, Dokka, GitHub Actions CI

### Getting started (dev)

#### Prerequisites

- **JDK 17** (required)
  - **Windows users**: Run `.\setup-java.ps1` to automatically find and configure Java
  - **Manual setup**: See [`SETUP_JAVA.md`](./SETUP_JAVA.md) for detailed instructions
- **Android Studio** (for Android runs) or a configured Android SDK

#### Build & test (all)

```bash
./gradlew check
```

Windows PowerShell:

```powershell
.\gradlew.bat check
```

#### Android (dev flavor)

```bash
./gradlew :android:assembleDevDebug
./gradlew :android:testDevDebugUnitTest
```

#### Desktop (Compose Desktop)

```bash
./gradlew :desktop:run
```

Build installers (MSI/DMG/DEB/RPM):

```bash
./gradlew :desktop:packageReleaseDistributionForCurrentOS
```

### CI

CI lives in `.github/workflows/ci.yml` and runs:
- `./gradlew check`
- `./gradlew :shared:build`
- `./gradlew :desktop:build` (macOS/Windows for PRs; all OSes on `main`)
- `./gradlew :android:assembleDevDebug` + `:android:testDevDebugUnitTest`
- `./gradlew ktlintCheck` + `./gradlew detekt`

### Security & privacy posture

- **Offline-first by default**: no cloud dependency required for MVP.
- **No bank credential storage** (explicit non-goal for MVP).
- **Encryption at rest**: Android DB uses SQLCipher; desktop DB encryption is currently deferred (see status docs).

### Contributing

- Please run `./gradlew check` before opening a PR.
- Follow the patterns in `docs/implementation-plan/` and keep changes aligned with the PRDs.

