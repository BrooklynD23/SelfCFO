# Architecture Decision Records (ADRs) - LedgerLens

This document records all architectural decisions that were previously "open" and are now resolved. Each ADR follows the standard format: Context, Decision, Consequences.

**Status:** APPROVED (resolves `11-open-decisions.md` blockers)
**Date:** 2026-01-12
**Resolves:** B-01 through B-05, PR2-H-01, PR2-H-03, PR2-H-05

---

## Table of Contents

1. [ADR-001: Shared Core Technology](#adr-001-shared-core-technology)
2. [ADR-002: OCR Library Selection](#adr-002-ocr-library-selection)
3. [ADR-003: Encryption Strategy](#adr-003-encryption-strategy)
4. [ADR-004: Sync Approach](#adr-004-sync-approach)
5. [ADR-005: Model Update Mechanism](#adr-005-model-update-mechanism)
6. [ADR-006: Money Type Representation](#adr-006-money-type-representation)
7. [ADR-007: Encryption Boundaries](#adr-007-encryption-boundaries)

---

## ADR-001: Shared Core Technology

**Status:** ACCEPTED
**Resolves:** B-01, `11-open-decisions.md` item 1

### Context

LedgerLens requires a shared core library ("Finance Engine") that runs on both Android and Desktop (Windows/macOS/Linux). The two leading candidates are:

1. **Kotlin Multiplatform (KMP)** - JetBrains' official multiplatform solution
2. **Rust with FFI bindings** - Systems language with C-compatible FFI

### Decision

**We will use Kotlin Multiplatform (KMP) for the shared core.**

### Rationale

| Criteria | KMP | Rust |
|----------|-----|------|
| Compose integration | Native (Compose Multiplatform) | Requires bridge layer |
| Team learning curve | Lower (Kotlin is familiar) | Higher (new language) |
| Android performance | Excellent (native) | Excellent (native via JNI) |
| Desktop performance | Good (JVM) | Excellent (native) |
| Build complexity | Moderate | High (cross-compilation) |
| Debugging | Unified tooling | Split tooling |
| Library ecosystem | Rich (JVM + native) | Growing (crates.io) |

**Key factors:**
1. Performance requirements are met by KMP for target workloads (sub-10ms categorization)
2. Compose Desktop integration is seamless with KMP
3. Lower risk - team productivity is higher
4. SQLDelight provides excellent multiplatform database support

### Consequences

**Positive:**
- Single codebase for business logic
- Type-safe SQL with SQLDelight
- Seamless Compose UI on both platforms
- Faster development velocity

**Negative:**
- JVM on desktop has higher memory footprint than native Rust
- Some KMP libraries are less mature than JVM equivalents
- Desktop packaging requires bundled JRE (~50MB)

**Mitigations:**
- Use Proguard/R8 for size optimization
- Monitor memory usage; optimize hot paths if needed
- Evaluate GraalVM native-image for future desktop builds

### Implementation Notes

```
Project Structure:
├── shared/                    # KMP shared module
│   ├── commonMain/           # Platform-agnostic code
│   ├── androidMain/          # Android-specific implementations
│   └── desktopMain/          # Desktop-specific implementations
├── android/                   # Android app (Jetpack Compose)
└── desktop/                   # Desktop app (Compose Desktop)
```

---

## ADR-002: OCR Library Selection

**Status:** ACCEPTED
**Resolves:** B-02, `11-open-decisions.md` item 2

### Context

LedgerLens requires OCR capability for:
1. Scanned PDF bank statements (desktop + Android)
2. Receipt images (primarily Android, camera capture)
3. Screenshot/email PDFs (both platforms)

### Decision

**We will use platform-specific OCR implementations:**

| Platform | Primary | Fallback |
|----------|---------|----------|
| **Android** | ML Kit Text Recognition v2 | None (ML Kit is sufficient) |
| **Desktop** | Tesseract 5.x via tess4j | Cloud OCR (opt-in, Phase 3+) |

### Rationale

**Android (ML Kit):**
- On-device, no network required
- Hardware-accelerated on modern devices
- Excellent accuracy for receipts and documents
- Free, no API limits
- ~3MB model size
- Latin script support built-in; other scripts downloadable

**Desktop (Tesseract):**
- Open source (Apache 2.0)
- Proven accuracy (LSTM-based in v5)
- Supports 100+ languages
- No cloud dependency
- ~30MB per language model

### Consequences

**Positive:**
- Both solutions work offline (local-first requirement met)
- No per-request costs
- Privacy-preserving (no data leaves device)
- Well-documented, mature solutions

**Negative:**
- Tesseract requires language pack management
- ML Kit may require Google Play Services on some devices
- Different accuracy characteristics between platforms

**Mitigations:**
- Bundle English language pack by default; others downloadable
- Provide fallback for devices without Play Services (Tesseract Android port)
- Implement confidence-based review routing to catch OCR errors

### Implementation Notes

```kotlin
// Common interface in shared/commonMain
interface OcrEngine {
    suspend fun recognizeText(image: ByteArray): OcrResult
}

data class OcrResult(
    val text: String,
    val blocks: List<TextBlock>,
    val confidence: Float  // 0.0 to 1.0
)

data class TextBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)

// Platform implementations
// androidMain: MlKitOcrEngine
// desktopMain: TesseractOcrEngine
```

---

## ADR-003: Encryption Strategy

**Status:** ACCEPTED
**Resolves:** B-03, PR2-H-03, `11-open-decisions.md` item 3

### Context

LedgerLens handles sensitive financial data requiring encryption at rest. Requirements:
1. Protect SQLite database
2. Protect file attachments (PDFs, images)
3. Support backup/restore
4. Support future multi-device sync
5. Enable crypto-erasure (delete key = delete data)

### Decision

**We will use envelope encryption with a two-tier key hierarchy:**

```
┌─────────────────────────────────────────────────────────────┐
│                     KEY HIERARCHY                            │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌─────────────────────────────────────────────────────┐    │
│  │              MASTER KEY (MK)                         │    │
│  │  Derived from: User passphrase OR Recovery key       │    │
│  │  Algorithm: Argon2id (memory-hard)                   │    │
│  │  Storage: NEVER stored; derived on-demand            │    │
│  └─────────────────────────────────────────────────────┘    │
│                          │                                   │
│                          ▼                                   │
│  ┌─────────────────────────────────────────────────────┐    │
│  │           KEY ENCRYPTION KEY (KEK)                   │    │
│  │  Derived from: MK + device salt                      │    │
│  │  Storage: Platform keystore (hardware-backed)        │    │
│  │  Purpose: Wraps DEKs                                 │    │
│  └─────────────────────────────────────────────────────┘    │
│                          │                                   │
│            ┌─────────────┴─────────────┐                    │
│            ▼                           ▼                    │
│  ┌──────────────────┐       ┌──────────────────┐           │
│  │    DB DEK        │       │   FILE DEK       │           │
│  │  (SQLCipher)     │       │  (Attachments)   │           │
│  │                  │       │                  │           │
│  │  AES-256-GCM     │       │  AES-256-GCM     │           │
│  │  Per-database    │       │  Per-file        │           │
│  └──────────────────┘       └──────────────────┘           │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### Encryption Components

| Component | Solution | Details |
|-----------|----------|---------|
| **SQLite Database** | SQLCipher | AES-256-CBC, PBKDF2 key derivation |
| **File Attachments** | AES-256-GCM | Per-file random IV, authenticated |
| **Key Storage** | Platform Keystore | Android Keystore / macOS Keychain / Windows DPAPI |
| **Key Derivation** | Argon2id | Memory: 64MB, Iterations: 3, Parallelism: 4 |

### Backup/Restore Strategy

```
BACKUP EXPORT:
1. User provides passphrase (minimum 12 chars)
2. Derive export key from passphrase using Argon2id
3. Generate fresh DEK for backup
4. Re-encrypt database and attachments with backup DEK
5. Wrap backup DEK with export key
6. Package as single encrypted archive (.llbackup)

RESTORE:
1. User provides passphrase
2. Derive export key
3. Unwrap backup DEK
4. Decrypt archive
5. Generate new device-specific KEK
6. Re-encrypt with new KEK
7. Store KEK in platform keystore
```

### Crypto-Erasure

```
To irreversibly delete all data:
1. Delete KEK from platform keystore
2. Overwrite DEK storage locations
3. SQLCipher data becomes unrecoverable
4. Attachment files become unrecoverable
```

### Consequences

**Positive:**
- Hardware-backed key storage on modern devices
- Backup/restore works across devices
- Supports future multi-device sync
- Crypto-erasure for privacy compliance
- Industry-standard algorithms

**Negative:**
- User must remember passphrase for backup
- Performance overhead (~5-10% for SQLCipher)
- Recovery key management is user responsibility

**Mitigations:**
- Offer recovery key as alternative to passphrase
- Display recovery key during setup; user stores securely
- Performance impact is acceptable for target workloads

### Implementation Notes

```kotlin
// Key management interface
interface KeyManager {
    suspend fun initializeKeys(passphrase: String): Result<Unit>
    suspend fun unlockDatabase(): Result<DatabaseKey>
    suspend fun getFileEncryptionKey(fileId: String): Result<ByteArray>
    suspend fun exportForBackup(passphrase: String): Result<BackupKeys>
    suspend fun importFromBackup(passphrase: String, backup: BackupKeys): Result<Unit>
    suspend fun cryptoErase(): Result<Unit>
}

// SQLCipher configuration
object DatabaseConfig {
    const val CIPHER = "aes-256-cbc"
    const val KDF_ITER = 256000
    const val PAGE_SIZE = 4096
    const val HMAC_ALGORITHM = "HMAC_SHA512"
}
```

---

## ADR-004: Sync Approach

**Status:** ACCEPTED
**Resolves:** B-04, `11-open-decisions.md` item 4

### Context

Future phases (Phase 3+) require multi-device sync. Options:
1. **Record-level sync** - Sync individual entities with field-level conflict resolution
2. **Encrypted blob snapshots** - Sync entire database as encrypted blob

### Decision

**We will use record-level sync with client-side conflict resolution.**

### Rationale

| Criteria | Record-Level | Blob Snapshots |
|----------|--------------|----------------|
| Bandwidth efficiency | High (delta sync) | Low (full DB each time) |
| Conflict resolution | Granular | All-or-nothing |
| Partial sync | Supported | Not supported |
| Complexity | Higher | Lower |
| Offline duration tolerance | Excellent | Limited |
| E2E encryption compatible | Yes (with client merge) | Yes |

### Sync Protocol Design

```
ENTITY SYNC MODEL:
┌─────────────────────────────────────────────────────────────┐
│  Each syncable entity has:                                   │
│  - id: UUID (stable across devices)                         │
│  - version: Int (monotonic per-entity)                      │
│  - updated_at: Timestamp (logical clock)                    │
│  - deleted: Boolean (soft delete)                           │
│  - sync_state: enum { local, pending, synced, conflict }    │
└─────────────────────────────────────────────────────────────┘

CONFLICT RESOLUTION (per entity type):
┌────────────────────┬─────────────────────────────────────┐
│ Entity Type        │ Resolution Strategy                  │
├────────────────────┼─────────────────────────────────────┤
│ Transaction        │ Imported: immutable; Overrides: LWW │
│ Category           │ Last-write-wins (LWW)               │
│ Rule               │ Merge by priority; flag conflicts   │
│ Receipt            │ Imported: immutable; Edits: LWW     │
│ Participant        │ LWW with name normalization         │
│ ItemAllocation     │ LWW                                  │
│ Settings           │ LWW                                  │
└────────────────────┴─────────────────────────────────────┘
```

### Consequences

**Positive:**
- Efficient sync after long offline periods
- Granular conflict resolution
- Supports selective sync (e.g., sync receipts but not statements)
- Works with E2E encryption (client does merge)

**Negative:**
- More complex client implementation
- Server cannot help with conflict resolution (E2E encrypted)
- Requires stable entity IDs across imports

**Mitigations:**
- Use deterministic UUID generation for imported entities
- Client-side conflict UI for unresolvable conflicts
- Comprehensive test suite for merge scenarios

---

## ADR-005: Model Update Mechanism

**Status:** ACCEPTED
**Resolves:** B-05, PR2-M-05, `11-open-decisions.md` item 5

### Context

ML models (categorization, receipt parsing) need periodic updates. Options:
1. **App release** - Bundle models with app; update via app store
2. **Downloadable bundles** - Separate model packages downloaded in-app

### Decision

**We will use downloadable bundles with cryptographic verification.**

### Bundle Specification

```yaml
bundle_format:
  version: 1
  structure:
    manifest.json:
      bundle_id: "categorization-model-v2.3.0"
      bundle_version: "2.3.0"
      min_app_version: "1.2.0"
      max_app_version: null  # null = no upper limit
      created_at: "2026-01-12T00:00:00Z"
      files:
        - path: "model.tflite"
          sha256: "abc123..."
          size_bytes: 15000000
        - path: "merchant_priors.db"
          sha256: "def456..."
          size_bytes: 500000
      signature: "base64-encoded-signature"
    model.tflite: <binary>
    merchant_priors.db: <binary>

signing:
  algorithm: Ed25519
  public_key_distribution: embedded in app binary
  key_rotation: new public key added in app update; old key valid for 6 months
```

### Security Controls

```
SUPPLY CHAIN PROTECTION:
1. All bundles signed with Ed25519
2. Public key embedded in app binary (not downloaded)
3. Signature verification before extraction
4. SHA-256 hash verification per file
5. Version compatibility check (min_app_version)
6. Rollback protection: bundle_version must be >= installed version
7. Quarantine: new bundles validated in staging area before activation

THREAT MODEL ADDITIONS:
- Threat: Compromised bundle server
  Control: Signature verification with embedded key
- Threat: Downgrade attack
  Control: Version monotonicity check
- Threat: Partial bundle corruption
  Control: Per-file hash verification
```

### Consequences

**Positive:**
- Faster model iteration (no app store review)
- Smaller initial app size (models downloaded on demand)
- Users get improvements without updating app
- A/B testing possible with bundle targeting

**Negative:**
- Additional infrastructure (bundle hosting)
- Key management complexity
- Requires network for initial model download

**Mitigations:**
- Bundle basic model with app; downloads enhance
- Simple bundle hosting (S3/CDN with signed URLs)
- Key rotation planned into app release cycle

---

## ADR-006: Money Type Representation

**Status:** ACCEPTED
**Resolves:** PR2-H-01

### Context

Financial calculations require precise decimal arithmetic. Kotlin Multiplatform lacks a common `BigDecimal`. Floating-point causes rounding errors. Need consistent representation across:
- Database storage
- Business logic
- UI display
- Export formats
- Settlement calculations

### Decision

**We will use integer minor units with explicit scale and currency.**

### Money Type Specification

```kotlin
/**
 * Canonical money representation.
 * Amount stored as integer minor units (e.g., cents for USD).
 *
 * Examples:
 * - $12.34 USD = Money(1234, "USD", 2)
 * - ¥1234 JPY = Money(1234, "JPY", 0)
 * - 0.00001234 BTC = Money(1234, "BTC", 8)
 */
data class Money(
    val minorUnits: Long,      // Integer amount in smallest unit
    val currencyCode: String,  // ISO 4217 currency code
    val scale: Int             // Decimal places (from currency metadata)
) {
    companion object {
        fun fromMajorUnits(amount: Double, currencyCode: String): Money {
            val scale = CurrencyMetadata.getScale(currencyCode)
            val minorUnits = (amount * 10.0.pow(scale)).roundToLong()
            return Money(minorUnits, currencyCode, scale)
        }
    }

    fun toMajorUnits(): Double = minorUnits / 10.0.pow(scale)

    fun format(locale: Locale): String {
        // Platform-specific formatting
    }
}

object CurrencyMetadata {
    private val scales = mapOf(
        "USD" to 2, "EUR" to 2, "GBP" to 2, "JPY" to 0,
        "BTC" to 8, "ETH" to 18, /* ... */
    )

    fun getScale(currencyCode: String): Int =
        scales[currencyCode] ?: 2  // Default to 2 decimal places
}
```

### Rounding Policy

```yaml
rounding_rules:
  default_mode: HALF_UP  # Banker's rounding alternative: HALF_EVEN

  contexts:
    split_calculation:
      mode: HALF_UP
      remainder_assignment: PAYER  # Payer absorbs remainder

    tax_allocation:
      mode: HALF_UP
      remainder_assignment: LARGEST_SHARE

    settlement:
      mode: HALF_UP
      remainder_assignment: CREDITOR  # Creditor gets benefit of doubt

    export_display:
      mode: HALF_UP
      truncation: NEVER  # Always show full precision

    aggregation:
      mode: HALF_UP
      intermediate_precision: FULL  # No rounding until final result
```

### Database Storage

```sql
-- All amounts stored as INTEGER (minor units)
-- Currency and scale stored alongside or in related table

CREATE TABLE transactions (
    id TEXT PRIMARY KEY,
    amount_minor_units INTEGER NOT NULL,
    currency_code TEXT NOT NULL DEFAULT 'USD',
    -- ...
);

-- Index for efficient currency-specific queries
CREATE INDEX idx_transactions_currency ON transactions(currency_code);
```

### Consequences

**Positive:**
- No floating-point errors
- Consistent across all platforms (KMP common code)
- Efficient storage (Long vs BigDecimal)
- Exact arithmetic for splits and settlements

**Negative:**
- Requires currency metadata table
- Conversion to/from Double for UI input
- Multi-currency math requires explicit handling

**Mitigations:**
- Provide `Money` utilities in shared core
- Document required conversions at API boundaries
- Test suite with known rounding edge cases

---

## ADR-007: Encryption Boundaries

**Status:** ACCEPTED
**Resolves:** PR2-H-05

### Context

LedgerLens has multiple features with different encryption requirements:
1. **Local storage** - Always encrypted (MVP)
2. **Multi-device sync** - E2E encrypted (Phase 3)
3. **Cloud parsing/OCR** - Server needs plaintext access (Phase 3 opt-in)
4. **Cloud categorization** - Can work on features, not raw text (Phase 3 opt-in)

These are in tension: E2E sync means server can't read data, but cloud compute needs data access.

### Decision

**We will define explicit encryption boundaries per feature with separate opt-in consent.**

### Encryption Boundary Model

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                         ENCRYPTION BOUNDARIES                                │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  BOUNDARY 1: LOCAL DEVICE (MVP - Phase 1-2)                                 │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  Encrypted at rest with device-bound keys                          │     │
│  │  - SQLCipher database                                              │     │
│  │  - Encrypted attachments                                           │     │
│  │  - NO network access required                                      │     │
│  │  Consent: Implicit (app usage)                                     │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  BOUNDARY 2: E2E ENCRYPTED SYNC (Phase 3)                                   │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  Client-side encryption before upload                              │     │
│  │  - Encrypted record payloads                                       │     │
│  │  - Server stores opaque blobs                                      │     │
│  │  - Conflict resolution on client                                   │     │
│  │  - Server CANNOT read financial data                               │     │
│  │  Consent: Explicit opt-in ("Enable multi-device sync")             │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  BOUNDARY 3: CLOUD COMPUTE (Phase 3 - Separate Opt-in)                      │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  Selective plaintext upload for server processing                  │     │
│  │                                                                    │     │
│  │  3A: Cloud OCR/Parsing                                             │     │
│  │  - User explicitly uploads specific files                          │     │
│  │  - Server processes and returns structured data                    │     │
│  │  - Server MAY retain for quality improvement (separate consent)    │     │
│  │  Consent: Per-file ("Process this document in cloud")              │     │
│  │                                                                    │     │
│  │  3B: Cloud Categorization                                          │     │
│  │  - Upload: merchant_normalized + amount_bucket + tokens            │     │
│  │  - NO raw descriptions or account numbers                          │     │
│  │  - Server returns category predictions                             │     │
│  │  Consent: Explicit opt-in ("Use cloud categorization")             │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
│  BOUNDARY 4: BANK AGGREGATION (Phase 4 - Separate Opt-in)                   │
│  ┌────────────────────────────────────────────────────────────────────┐     │
│  │  Third-party aggregator handles bank credentials                   │     │
│  │  - LedgerLens receives transactions via aggregator API             │     │
│  │  - Token-based auth (no bank passwords stored by us)               │     │
│  │  - Aggregator's privacy policy applies                             │     │
│  │  Consent: Explicit per-institution connection                      │     │
│  └────────────────────────────────────────────────────────────────────┘     │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Consent Model

```yaml
consent_gates:
  sync_enabled:
    description: "Sync your data across devices"
    data_scope: "All ledger data (E2E encrypted)"
    server_access: "Encrypted blobs only - we cannot read your data"
    default: false
    revocable: true
    revocation_action: "Delete server data, disable sync"

  cloud_ocr_enabled:
    description: "Use cloud processing for difficult documents"
    data_scope: "Individual files you choose to upload"
    server_access: "Document content during processing"
    retention: "Deleted after processing unless quality consent given"
    default: false
    revocable: true

  cloud_categorization_enabled:
    description: "Improve categorization accuracy with cloud AI"
    data_scope: "Merchant names, amount ranges, text tokens"
    server_access: "Feature data only - no account numbers or raw descriptions"
    default: false
    revocable: true

  quality_data_donation:
    description: "Help improve LedgerLens for everyone"
    data_scope: "Anonymized parsing/categorization examples"
    server_access: "Anonymized samples for model training"
    default: false
    revocable: true
```

### Implementation Rules

```yaml
implementation_guardrails:
  - "E2E sync and cloud compute are mutually exclusive for the same data field"
  - "Cloud compute features upload minimal required data, not full records"
  - "Each consent gate has independent enable/disable"
  - "Consent changes propagate to all devices via sync"
  - "Server-side data deletion honored within 24 hours of revocation"
  - "Audit log records all consent changes with timestamp"
```

### Consequences

**Positive:**
- Clear user understanding of data exposure
- Granular privacy control
- E2E sync remains truly private
- Cloud features available for users who want them

**Negative:**
- Complex consent UI
- Users may be confused by options
- Some features degraded without cloud opt-in

**Mitigations:**
- Default to most private option (local only)
- Clear, non-technical consent language
- In-app privacy dashboard showing what's enabled

---

## Summary of Resolved Decisions

| Original Issue | ADR | Decision |
|----------------|-----|----------|
| B-01: KMP vs Rust | ADR-001 | Kotlin Multiplatform |
| B-02: OCR libraries | ADR-002 | ML Kit (Android), Tesseract (Desktop) |
| B-03: Encryption | ADR-003 | Envelope encryption with SQLCipher |
| B-04: Sync approach | ADR-004 | Record-level with client-side merge |
| B-05: Model updates | ADR-005 | Signed downloadable bundles |
| PR2-H-01: Money type | ADR-006 | Integer minor units with scale |
| PR2-H-03: Full encryption | ADR-003 | Two-tier key hierarchy |
| PR2-H-05: Encryption boundaries | ADR-007 | Per-feature opt-in boundaries |

---

*All ADRs are considered ACCEPTED and ready for implementation.*
