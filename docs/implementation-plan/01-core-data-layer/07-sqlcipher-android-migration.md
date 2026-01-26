# 07: SQLCipher Android Dependency Migration (Planned)

## Goal

Migrate the Android SQLCipher dependency from the legacy artifact:

- `net.zetetic:android-database-sqlcipher`

to Zetetic’s **current recommended** Android artifact:

- `net.zetetic:sqlcipher-android` (or equivalent, per Zetetic docs/licensing)

This is a **future maintenance task** intended to reduce supply/availability risk and align with upstream support.

---

## Background / Why this exists

The legacy artifact `android-database-sqlcipher` is **deprecated** upstream and is **not consistently published** for newer SQLCipher versions on Maven Central.

We already saw this in CI when a newer version was referenced but not resolvable.

**Current repo state (as of 2026-01-23):**

- Version catalog pins SQLCipher Android to the latest available legacy version on Maven Central.
- Android database encryption is implemented via SQLCipher + `SupportOpenHelperFactory`.
- Desktop DB encryption is currently deferred (no stable SQLCipher JDBC).

---

## Preconditions / Decisions

- **Licensing/Distribution**: Confirm whether `sqlcipher-android` requires a commercial license for your distribution model.
- **Artifact coordinates**: Confirm the correct group/artifact/version per Zetetic documentation.
- **Migration strategy**: Ensure existing encrypted DBs remain readable after migration (or document a migration/rekey path if required).

---

## Repo dependencies affected (what an agent must change)

### Build / dependency definitions

- `gradle/libs.versions.toml`
  - Replace `sqlcipher-android` library coordinate from `android-database-sqlcipher` → `sqlcipher-android`.
  - Update any version fields and comments.

- `shared/build.gradle.kts`
  - `androidMain` currently depends on `libs.sqlcipher.android`.
  - Verify transitive deps and packaging.

### Android database driver / SQLCipher wiring

- `shared/src/androidMain/kotlin/com/ledgerlens/data/DatabaseDriverFactory.android.kt`
  - Currently imports:
    - `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`
  - Also calls:
    - `System.loadLibrary("sqlcipher")`
  - These APIs/packages may change with the new artifact; update accordingly.

### Proguard/R8

- `android/proguard-rules.pro`
  - May need keep rules depending on the new artifact’s JNI/classes.

### Documentation

- `docs/implementation-plan/01-core-data-layer/02-encryption-layer.md`
  - Add a “Planned follow-up” note linking to this doc.
- `IMPLEMENTATION_STATUS.md`
  - Add a “Planned Migration” entry under encryption notes.
- `README.md` (optional)
  - Keep the “Android DB encrypted” statement accurate and note dependency migration plan.

---

## Implementation steps (suggested)

1. **Read Zetetic migration guidance**
   - Confirm the recommended artifact and any required initialization steps.

2. **Update dependency coordinates**
   - Update `gradle/libs.versions.toml`:
     - Replace module coordinate
     - Adjust version field

3. **Update Android driver factory**
   - Update imports/classes to the new artifact’s APIs.
   - Ensure SQLDelight still receives a valid `SupportSQLiteOpenHelper.Factory`.
   - Validate key format:
     - currently `passphrase = key.toHexString()`
     - factory gets `passphrase.toByteArray()`
     - confirm any required encoding/format changes.

4. **Validate encryption behavior**
   - Create a new DB, verify it’s encrypted (cannot open with plain SQLite).
   - Open existing encrypted DB (if test fixture exists).

5. **CI + local verification**
   - `./gradlew :android:assembleDevDebug`
   - `./gradlew :android:testDevDebugUnitTest`
   - `./gradlew :shared:check`

6. **Update docs**
   - Remove legacy-pinning notes (if migration succeeds).
   - Document any upgrade caveats (e.g., “requires app re-install” or DB migration).

---

## Testing notes

Recommended to add/extend tests around:

- `DatabaseDriverFactory.createEncryptedDriver()`:
  - Key length validation
  - Encrypted DB creation/open works on Android instrumentation (if feasible)

---

## Risks / Gotchas

- **Artifact availability/licensing**: migration may be blocked if the new artifact isn’t publicly distributed.
- **DB compatibility**: encrypted DB format or key derivation expectations might differ; verify existing data stays readable.
- **JNI loading**: `System.loadLibrary("sqlcipher")` behavior can differ; ensure no crashes on app startup.

