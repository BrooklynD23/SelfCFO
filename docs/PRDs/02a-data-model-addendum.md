# Data Model Addendum - LedgerLens

**Status:** APPROVED
**Date:** 2026-01-12
**Resolves:** PR2-H-02, PR2-H-04, PR2-M-01, PR2-M-02, PR2-M-03, PR2-M-04, PR2-M-06
**Extends:** `02-detailed-functional-specification.md`

This addendum defines additional entities and specifications identified during architecture review. All items here are **required for MVP** unless marked otherwise.

---

## Table of Contents

1. [Data Provenance Model](#1-data-provenance-model)
2. [New Core Entities](#2-new-core-entities)
3. [Enhanced Existing Entities](#3-enhanced-existing-entities)
4. [Import Idempotency Specification](#4-import-idempotency-specification)
5. [Category Explanation Schema](#5-category-explanation-schema)
6. [Export Safety Specification](#6-export-safety-specification)
7. [Storage Management Specification](#7-storage-management-specification)

---

## 1. Data Provenance Model

**Resolves:** PR2-H-02

### 1.1 Problem Statement

The original data model conflates:
- Imported data (from bank statements, receipts)
- User edits (category corrections, notes)
- Derived data (settlements, aggregations)

This causes issues when:
- Re-importing the same file (overwrites user edits)
- Template/parser improvements run on existing data
- Syncing between devices (which version wins?)

### 1.2 Solution: Immutable Imports + Mutable Overlays

```
DATA PROVENANCE LAYERS:

┌─────────────────────────────────────────────────────────────────────────┐
│                         DERIVED LAYER                                    │
│  (Computed on-demand, not stored)                                       │
│  - Aggregations, budgets, insights                                      │
│  - Settlement calculations                                              │
│  - Recurring transaction detection                                      │
└─────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │ computed from
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│                         ENRICHMENT LAYER                                 │
│  (User modifications, stored separately)                                │
│  - TransactionOverride: category, notes, tags, is_transfer             │
│  - MerchantAlias: user-defined name mappings                           │
│  - Rules: user-created categorization rules                            │
└─────────────────────────────────────────────────────────────────────────┘
                                    ▲
                                    │ enriches
                                    │
┌─────────────────────────────────────────────────────────────────────────┐
│                         IMPORT LAYER                                     │
│  (Immutable after import)                                               │
│  - ImportedTransaction: raw parsed data from source                    │
│  - ImportedReceiptItem: raw OCR extraction                             │
│  - SourceFile: original files with content hash                        │
└─────────────────────────────────────────────────────────────────────────┘
```

### 1.3 Merge Rules

```yaml
transaction_view_computation:
  description: "How to compute the effective transaction for display/export"

  fields:
    id:
      source: ImportedTransaction.id

    posted_date:
      source: ImportedTransaction.posted_date
      override: NOT_ALLOWED

    amount:
      source: ImportedTransaction.amount_minor_units
      override: NOT_ALLOWED

    description_raw:
      source: ImportedTransaction.description_raw
      override: NOT_ALLOWED

    merchant_normalized:
      priority:
        1: TransactionOverride.merchant_override (if set)
        2: MerchantAlias mapping (if exists)
        3: ImportedTransaction.merchant_normalized (parser output)

    category_id:
      priority:
        1: TransactionOverride.category_id (if set)
        2: Rule match (highest priority rule)
        3: MerchantPrior lookup
        4: ML model prediction

    notes:
      source: TransactionOverride.notes
      default: null

    tags:
      source: TransactionOverride.tags
      merge_strategy: UNION (tags from all sources)

    is_transfer:
      source: TransactionOverride.is_transfer OR Transfer entity exists
      default: false
```

### 1.4 Re-Import Behavior

```yaml
reimport_strategy:
  same_file_hash:
    action: SKIP
    message: "File already imported"

  same_source_different_hash:
    action: MERGE
    description: "Same file, updated content (e.g., redownloaded statement)"
    behavior:
      - Match transactions by fingerprint
      - New transactions: CREATE
      - Matching transactions: KEEP EXISTING (preserve overrides)
      - Missing transactions: FLAG for review (don't auto-delete)

  different_source_overlap:
    action: DEDUPE
    description: "Different files with overlapping date ranges"
    behavior:
      - Match by fingerprint (date + merchant + amount)
      - Exact match: mark as duplicate, keep first import
      - Near match: FLAG for review
```

---

## 2. New Core Entities

### 2.1 Account

**Resolves:** Section 6.3 item 2

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              ACCOUNT                                     │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key                       │
│ display_name       │ string         │ User-facing name ("Chase Checking")│
│ institution_name   │ string (null)  │ Bank/institution name             │
│ account_type       │ enum           │ checking | savings | credit |     │
│                    │                │ investment | cash | other         │
│ currency_code      │ string         │ ISO 4217 (default account currency)│
│ last_four          │ string (null)  │ Last 4 digits of account number   │
│ is_active          │ bool           │ Soft delete / archive flag        │
│ created_at         │ timestamp      │ When account was added            │
│ color              │ string (null)  │ UI color for account badge        │
│ icon               │ string (null)  │ Icon identifier                   │
│ notes              │ string (null)  │ User notes                        │
└────────────────────┴────────────────┴───────────────────────────────────┘

CONSTRAINTS:
- display_name: NOT NULL, max 100 chars
- institution_name: max 100 chars
- last_four: exactly 4 chars if set
- currency_code: valid ISO 4217

INDEXES:
- PRIMARY KEY (id)
- INDEX (is_active, display_name)
```

### 2.2 SourceFile (Asset)

**Resolves:** PR2-H-04, Section 6.3 item 3

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            SOURCE_FILE                                   │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key                       │
│ content_hash       │ string         │ SHA-256 of original file content  │
│ original_filename  │ string         │ Name when imported                │
│ mime_type          │ string         │ Detected MIME type                │
│ size_bytes         │ int64          │ Original file size                │
│ file_type          │ enum           │ pdf_statement | csv_statement |   │
│                    │                │ receipt_image | receipt_pdf |     │
│                    │                │ email_pdf | other                 │
│ imported_at        │ timestamp      │ When file was imported            │
│ encryption_key_id  │ string         │ Reference to DEK used             │
│ storage_path       │ string         │ Encrypted file location           │
│ page_count         │ int (null)     │ For PDFs: number of pages         │
│ parse_status       │ enum           │ pending | parsing | complete |    │
│                    │                │ failed | needs_review             │
│ parse_error        │ string (null)  │ Error message if failed           │
│ retention_policy   │ enum           │ keep | delete_after_import |      │
│                    │                │ user_managed                      │
│ deleted_at         │ timestamp(null)│ Soft delete timestamp             │
│ account_id         │ UUID (null)    │ Associated account if known       │
└────────────────────┴────────────────┴───────────────────────────────────┘

CONSTRAINTS:
- content_hash: NOT NULL, 64 chars (hex SHA-256)
- original_filename: NOT NULL, max 255 chars
- mime_type: NOT NULL
- UNIQUE (content_hash) -- prevents duplicate file imports

INDEXES:
- PRIMARY KEY (id)
- UNIQUE INDEX (content_hash)
- INDEX (file_type, parse_status)
- INDEX (account_id)
- INDEX (imported_at)
```

### 2.3 ImportJob

**Resolves:** Section 6.3 item 4

```
┌─────────────────────────────────────────────────────────────────────────┐
│                            IMPORT_JOB                                    │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key                       │
│ source_file_id     │ UUID           │ Reference to SourceFile           │
│ status             │ enum           │ queued | extracting | parsing |   │
│                    │                │ normalizing | categorizing |      │
│                    │                │ deduping | complete | failed |    │
│                    │                │ cancelled                         │
│ started_at         │ timestamp(null)│ When processing began             │
│ completed_at       │ timestamp(null)│ When processing finished          │
│ progress_percent   │ int            │ 0-100 progress indicator          │
│ current_stage      │ string (null)  │ Human-readable current step       │
│ transactions_found │ int            │ Total transactions parsed         │
│ transactions_new   │ int            │ New transactions added            │
│ transactions_dupe  │ int            │ Duplicates skipped                │
│ transactions_error │ int            │ Transactions with parse errors    │
│ error_message      │ string (null)  │ Error if failed                   │
│ error_details      │ json (null)    │ Structured error info             │
│ checkpoint_data    │ blob (null)    │ Resume checkpoint for crashes     │
└────────────────────┴────────────────┴───────────────────────────────────┘

CONSTRAINTS:
- source_file_id: NOT NULL, FK to SourceFile
- progress_percent: 0 <= value <= 100

INDEXES:
- PRIMARY KEY (id)
- INDEX (source_file_id)
- INDEX (status, started_at)
```

### 2.4 Merchant

**Resolves:** Section 6.3 item 2

```
┌─────────────────────────────────────────────────────────────────────────┐
│                             MERCHANT                                     │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key                       │
│ canonical_name     │ string         │ Normalized merchant name          │
│ display_name       │ string (null)  │ User-preferred display name       │
│ category_id        │ UUID (null)    │ Default category (merchant prior) │
│ logo_url           │ string (null)  │ Merchant logo (future)            │
│ merchant_type      │ enum (null)    │ retail | restaurant | utility |   │
│                    │                │ subscription | transfer | other   │
│ is_subscription    │ bool           │ Detected as recurring charge      │
│ typical_amount_min │ int64 (null)   │ Typical amount range (minor units)│
│ typical_amount_max │ int64 (null)   │ Typical amount range (minor units)│
│ transaction_count  │ int            │ Number of transactions seen       │
│ first_seen_at      │ timestamp      │ First transaction date            │
│ last_seen_at       │ timestamp      │ Most recent transaction date      │
└────────────────────┴────────────────┴───────────────────────────────────┘

INDEXES:
- PRIMARY KEY (id)
- UNIQUE INDEX (canonical_name)
- INDEX (category_id)
- INDEX (is_subscription)
```

### 2.5 MerchantAlias

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          MERCHANT_ALIAS                                  │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key                       │
│ merchant_id        │ UUID           │ Reference to Merchant             │
│ alias_pattern      │ string         │ Raw description pattern           │
│ match_type         │ enum           │ exact | contains | regex          │
│ source             │ enum           │ parser | user | learned           │
│ created_at         │ timestamp      │ When alias was created            │
└────────────────────┴────────────────┴───────────────────────────────────┘

INDEXES:
- PRIMARY KEY (id)
- INDEX (merchant_id)
- INDEX (alias_pattern) -- for lookup during normalization
```

### 2.6 Transfer (replaces is_transfer boolean)

**Resolves:** PR2-M-02

```
┌─────────────────────────────────────────────────────────────────────────┐
│                             TRANSFER                                     │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key                       │
│ from_transaction_id│ UUID (null)    │ Outgoing transaction (debit)      │
│ to_transaction_id  │ UUID (null)    │ Incoming transaction (credit)     │
│ from_account_id    │ UUID           │ Source account                    │
│ to_account_id      │ UUID           │ Destination account               │
│ amount_minor_units │ int64          │ Transfer amount                   │
│ currency_code      │ string         │ Transfer currency                 │
│ fx_rate            │ decimal (null) │ Exchange rate if cross-currency   │
│ fx_amount_minor    │ int64 (null)   │ Amount in destination currency    │
│ status             │ enum           │ suspected | confirmed | rejected  │
│ match_confidence   │ float          │ Auto-match confidence (0-1)       │
│ matched_by         │ enum           │ auto | user | rule                │
│ created_at         │ timestamp      │ When transfer was identified      │
│ confirmed_at       │ timestamp(null)│ When user confirmed               │
│ notes              │ string (null)  │ User notes                        │
└────────────────────┴────────────────┴───────────────────────────────────┘

CONSTRAINTS:
- At least one of from_transaction_id or to_transaction_id must be set
- from_account_id != to_account_id
- If both transactions set: they should have opposite signs

INVARIANTS:
- For same-currency transfers: from_amount + to_amount = 0
- For cross-currency: from_amount * fx_rate ≈ to_amount (within tolerance)

INDEXES:
- PRIMARY KEY (id)
- INDEX (from_transaction_id)
- INDEX (to_transaction_id)
- INDEX (from_account_id, to_account_id)
- INDEX (status)
```

### 2.7 ImportedTransaction (immutable import layer)

**Resolves:** PR2-H-02

```
┌─────────────────────────────────────────────────────────────────────────┐
│                       IMPORTED_TRANSACTION                               │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ id                 │ UUID           │ Primary key (stable across imports)│
│ import_job_id      │ UUID           │ Which import created this         │
│ source_file_id     │ UUID           │ Source file reference             │
│ source_row_ref     │ string         │ Page+line or CSV row index        │
│ fingerprint        │ string         │ Deduplication hash                │
│ account_id         │ UUID (null)    │ Associated account                │
│ posted_date        │ date           │ Transaction posted date           │
│ transaction_date   │ date (null)    │ Original transaction date         │
│ description_raw    │ string         │ Raw description from source       │
│ merchant_normalized│ string         │ Parser's normalized merchant      │
│ merchant_id        │ UUID (null)    │ Linked Merchant entity            │
│ amount_minor_units │ int64          │ Amount in minor units             │
│ currency_code      │ string         │ ISO 4217 currency                 │
│ balance_after      │ int64 (null)   │ Balance if provided in source     │
│ category_id_auto   │ UUID (null)    │ ML/rule assigned category         │
│ category_confidence│ float (null)   │ Confidence of auto-assignment     │
│ category_reason    │ json (null)    │ Explanation (see schema below)    │
│ parse_warnings     │ json (null)    │ Any issues during parsing         │
│ imported_at        │ timestamp      │ When record was created           │
└────────────────────┴────────────────┴───────────────────────────────────┘

IMMUTABILITY RULES:
- Once created, only category_id_auto and category_reason may be updated
  (for re-categorization with improved models)
- All other fields are IMMUTABLE
- User changes go to TransactionOverride

INDEXES:
- PRIMARY KEY (id)
- UNIQUE INDEX (fingerprint)
- INDEX (account_id, posted_date)
- INDEX (merchant_id)
- INDEX (import_job_id)
```

### 2.8 TransactionOverride (mutable enrichment layer)

**Resolves:** PR2-H-02

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      TRANSACTION_OVERRIDE                                │
├─────────────────────────────────────────────────────────────────────────┤
│ Field              │ Type           │ Description                       │
├────────────────────┼────────────────┼───────────────────────────────────┤
│ transaction_id     │ UUID           │ FK to ImportedTransaction (PK)    │
│ category_id        │ UUID (null)    │ User-selected category            │
│ merchant_override  │ string (null)  │ User-corrected merchant name      │
│ notes              │ string (null)  │ User notes                        │
│ tags               │ json           │ Array of tag strings              │
│ is_excluded        │ bool           │ Exclude from budgets/reports      │
│ exclude_reason     │ enum (null)    │ duplicate | refund | internal |   │
│                    │                │ business | other                  │
│ is_reviewed        │ bool           │ User has reviewed this txn        │
│ reviewed_at        │ timestamp(null)│ When reviewed                     │
│ updated_at         │ timestamp      │ Last modification                 │
│ sync_version       │ int            │ For sync conflict resolution      │
└────────────────────┴────────────────┴───────────────────────────────────┘

CONSTRAINTS:
- transaction_id: PK, FK to ImportedTransaction
- tags: valid JSON array of strings

SYNC BEHAVIOR:
- Last-write-wins based on updated_at
- sync_version increments on each change

INDEXES:
- PRIMARY KEY (transaction_id)
- INDEX (updated_at) -- for sync
```

---

## 3. Enhanced Existing Entities

### 3.1 Transaction View (Computed)

The `Transaction` from the original spec becomes a **computed view**, not a stored table:

```sql
CREATE VIEW transaction_view AS
SELECT
    it.id,
    it.source_file_id,
    it.source_row_ref,
    it.account_id,
    it.posted_date,
    it.transaction_date,
    it.description_raw,
    COALESCE(tor.merchant_override, m.display_name, it.merchant_normalized) AS merchant_normalized,
    it.amount_minor_units,
    it.currency_code,
    COALESCE(tor.category_id, it.category_id_auto) AS category_id,
    CASE
        WHEN tor.category_id IS NOT NULL THEN 1.0
        ELSE it.category_confidence
    END AS category_confidence,
    CASE
        WHEN tor.category_id IS NOT NULL THEN '{"source": "user_override"}'
        ELSE it.category_reason
    END AS category_reason,
    COALESCE(tor.tags, '[]') AS tags,
    COALESCE(tor.notes, '') AS notes,
    EXISTS(SELECT 1 FROM transfer t WHERE t.from_transaction_id = it.id OR t.to_transaction_id = it.id) AS is_transfer,
    COALESCE(tor.is_excluded, false) AS is_excluded,
    COALESCE(tor.is_reviewed, false) AS is_reviewed,
    it.fingerprint AS hash_fingerprint,
    it.imported_at
FROM imported_transaction it
LEFT JOIN transaction_override tor ON it.id = tor.transaction_id
LEFT JOIN merchant m ON it.merchant_id = m.id;
```

---

## 4. Import Idempotency Specification

**Resolves:** PR2-M-01

### 4.1 Fingerprint Algorithm

```yaml
fingerprint_computation:
  algorithm: SHA-256
  inputs:
    - merchant_normalized (lowercase, trimmed)
    - posted_date (ISO 8601 date only)
    - amount_minor_units (absolute value)
    - currency_code
    - account_id (if known)

  formula: |
    fingerprint = SHA256(
      normalize(merchant) + "|" +
      posted_date + "|" +
      abs(amount_minor_units) + "|" +
      currency_code + "|" +
      (account_id ?? "")
    )

  collision_handling:
    same_fingerprint_same_file: ALLOW_IMPORT (distinct by source_row_ref)
    same_fingerprint_different_file: IMPORT_AND_FLAG_FOR_REVIEW (duplicate_candidate)
    same_fingerprint_different_amount_sign: LIKELY_DIFFERENT (refund?)
```

### 4.2 Uniqueness Constraints

**Design note:** Fingerprints are a *dedupe signal*, not a primary key. A bank statement can legitimately contain multiple rows that produce the same fingerprint (e.g., two identical same-day purchases). To avoid silent data loss, the system imports all rows (keyed by `source_row_ref` within a `source_file`) and records potential cross-file duplicates in `duplicate_candidate` for later user review.

```sql
-- Enforce per-file idempotency using a stable per-row reference.
-- Fingerprint collisions are expected and should NOT block inserts.
CREATE UNIQUE INDEX idx_imported_txn_file_rowref
    ON imported_transaction(source_file_id, source_row_ref);

-- Cross-file duplicates tracked separately
CREATE TABLE duplicate_candidate (
    transaction_id_a TEXT NOT NULL REFERENCES imported_transaction(id),
    transaction_id_b TEXT NOT NULL REFERENCES imported_transaction(id),
    fingerprint TEXT NOT NULL,
    score REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING', -- PENDING | CONFIRMED | DISMISSED
    created_at INTEGER NOT NULL,
    reviewed_at INTEGER,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    PRIMARY KEY(transaction_id_a, transaction_id_b),
    CHECK (transaction_id_a < transaction_id_b),
    CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED'))
);
```

### 4.3 Re-Import Decision Tree

```
┌─────────────────────────────────────────────────────────────────────────┐
│                        RE-IMPORT DECISION TREE                           │
└─────────────────────────────────────────────────────────────────────────┘

File imported
    │
    ▼
Check content_hash in SourceFile
    │
    ├── EXACT MATCH found
    │       │
    │       ▼
    │   Return: "File already imported"
    │   Action: SKIP (show existing ImportJob)
    │
    └── NO MATCH found
            │
            ▼
        Parse file, generate fingerprints
            │
            ▼
        For each transaction:
            │
            ├── Fingerprint exists in same account?
            │       │
            │       ├── YES, within ±2 days
            │       │       │
            │       │       ▼
            │       │   Mark as DUPLICATE
            │       │   Link to existing transaction
            │       │   Increment transactions_dupe count
            │       │
            │       └── YES, but date difference > 2 days
            │               │
            │               ▼
            │           Create duplicate_candidate record
            │           Import as NEW (let user resolve)
            │
            └── NO fingerprint match
                    │
                    ▼
                Import as NEW transaction
                Increment transactions_new count
```

---

## 5. Category Explanation Schema

**Resolves:** PR2-M-03

### 5.1 Schema Definition

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "CategoryExplanation",
  "description": "Structured explanation for category assignment",
  "type": "object",
  "properties": {
    "schema_version": {
      "type": "integer",
      "const": 1
    },
    "source": {
      "type": "string",
      "enum": ["user_override", "rule", "merchant_prior", "ml_model", "default"]
    },
    "confidence": {
      "type": "number",
      "minimum": 0,
      "maximum": 1
    },
    "contributors": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "type": {
            "type": "string",
            "enum": ["rule_match", "merchant_prior", "token_match", "amount_pattern", "history_similarity", "model_prediction"]
          },
          "weight": {
            "type": "number",
            "minimum": 0,
            "maximum": 1
          },
          "details": {
            "type": "object"
          }
        },
        "required": ["type", "weight"]
      }
    },
    "alternatives": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "category_id": { "type": "string", "format": "uuid" },
          "confidence": { "type": "number" }
        }
      },
      "maxItems": 3
    }
  },
  "required": ["schema_version", "source", "confidence"]
}
```

### 5.2 Example Explanations

```json
// Rule match
{
  "schema_version": 1,
  "source": "rule",
  "confidence": 1.0,
  "contributors": [
    {
      "type": "rule_match",
      "weight": 1.0,
      "details": {
        "rule_id": "550e8400-e29b-41d4-a716-446655440000",
        "rule_name": "Netflix = Entertainment",
        "match_type": "merchant_contains",
        "pattern": "NETFLIX"
      }
    }
  ]
}

// ML model with alternatives
{
  "schema_version": 1,
  "source": "ml_model",
  "confidence": 0.72,
  "contributors": [
    {
      "type": "model_prediction",
      "weight": 0.5,
      "details": {
        "model_version": "categorizer-v2.3.0",
        "top_tokens": ["AMZN", "MKTPLACE"]
      }
    },
    {
      "type": "merchant_prior",
      "weight": 0.3,
      "details": {
        "merchant_id": "...",
        "prior_category": "Shopping",
        "observations": 15
      }
    },
    {
      "type": "amount_pattern",
      "weight": 0.2,
      "details": {
        "amount_bucket": "10-50",
        "typical_for_category": true
      }
    }
  ],
  "alternatives": [
    {"category_id": "...", "confidence": 0.72},
    {"category_id": "...", "confidence": 0.18},
    {"category_id": "...", "confidence": 0.06}
  ]
}
```

---

## 6. Export Safety Specification

**Resolves:** PR2-M-04

### 6.1 CSV/Excel Injection Prevention

```yaml
export_sanitization:
  description: "Prevent formula injection when exports are opened in spreadsheets"

  dangerous_prefixes:
    - "="   # Excel formula
    - "+"   # Excel formula (less common)
    - "-"   # Excel formula (less common)
    - "@"   # Excel formula
    - "\t"  # Tab (can trigger macros)
    - "\r"  # Carriage return
    - "\n"  # Newline

  sanitization_rules:
    - field: "description_raw"
      action: PREFIX_WITH_QUOTE

    - field: "merchant_normalized"
      action: PREFIX_WITH_QUOTE

    - field: "notes"
      action: PREFIX_WITH_QUOTE

    - field: "category_name"
      action: PREFIX_WITH_QUOTE

  implementation: |
    fun sanitizeForExport(value: String): String {
        val dangerous = listOf('=', '+', '-', '@', '\t', '\r', '\n')
        return if (value.isNotEmpty() && value[0] in dangerous) {
            "'" + value  // Prefix with single quote
        } else {
            value
        }
    }
```

### 6.2 Export Formats

```yaml
supported_formats:
  csv:
    encoding: UTF-8 with BOM
    delimiter: comma (configurable)
    quote_all: true
    sanitize: true

  json:
    encoding: UTF-8
    format: array of objects
    sanitize: false (JSON doesn't execute)

  ofx:
    version: 2.2
    sanitize: true (embedded in XML)
```

---

## 7. Storage Management Specification

**Resolves:** PR2-M-06

### 7.1 Storage Budget System

```yaml
storage_settings:
  default_budget_mb: 5000  # 5GB default
  warning_threshold_percent: 80
  critical_threshold_percent: 95

  retention_policies:
    source_files:
      options:
        - keep_forever
        - delete_after_successful_import
        - delete_after_days: 90
      default: keep_forever

    receipt_images:
      options:
        - keep_forever
        - compress_after_days: 30
        - delete_after_days: 365
      default: keep_forever

    import_job_logs:
      retention_days: 90

    debug_bundles:
      retention_days: 30
```

### 7.2 Storage Entities

```sql
CREATE TABLE storage_usage (
    id INTEGER PRIMARY KEY,
    measured_at TIMESTAMP NOT NULL,
    total_bytes INTEGER NOT NULL,
    database_bytes INTEGER NOT NULL,
    attachments_bytes INTEGER NOT NULL,
    models_bytes INTEGER NOT NULL,
    cache_bytes INTEGER NOT NULL
);

CREATE TABLE storage_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### 7.3 Cleanup Operations

```yaml
cleanup_operations:
  clear_cache:
    description: "Remove temporary and cached files"
    recoverable: true

  compress_old_attachments:
    description: "Compress attachments older than threshold"
    recoverable: false
    preserves_data: true

  delete_source_files:
    description: "Remove original imported files (keeps parsed data)"
    recoverable: false
    requires_confirmation: true

  crypto_erase:
    description: "Irreversibly delete all data"
    recoverable: false
    requires_double_confirmation: true
```

---

## Summary of New Entities

| Entity | Purpose | Resolves |
|--------|---------|----------|
| Account | User's financial accounts | 6.3 item 1 |
| SourceFile | Imported file tracking with hashes | PR2-H-04, 6.3 item 3 |
| ImportJob | Import pipeline state and resume | 6.3 item 4 |
| Merchant | Normalized merchant registry | 6.3 item 2 |
| MerchantAlias | Raw-to-normalized mappings | 6.3 item 2 |
| Transfer | Linked transfer transactions | PR2-M-02 |
| ImportedTransaction | Immutable import layer | PR2-H-02 |
| TransactionOverride | Mutable user enrichments | PR2-H-02 |

---

## Entity Relationship Diagram

```
┌─────────────┐       ┌─────────────┐       ┌─────────────────────┐
│   Account   │◄──────│ SourceFile  │◄──────│     ImportJob       │
└─────────────┘       └─────────────┘       └─────────────────────┘
      │                     │                        │
      │                     │                        │
      ▼                     ▼                        │
┌─────────────────────────────────────────────┐     │
│           ImportedTransaction               │◄────┘
│  (immutable import layer)                   │
└─────────────────────────────────────────────┘
      │                     │
      │                     │
      ▼                     ▼
┌─────────────────┐  ┌─────────────────┐
│TransactionOverride│  │    Merchant     │
│(mutable overlays)  │  └─────────────────┘
└─────────────────┘          │
      │                      │
      ▼                      ▼
┌─────────────────┐  ┌─────────────────┐
│    Category     │  │  MerchantAlias  │
└─────────────────┘  └─────────────────┘

┌─────────────────────────────────────────────┐
│               Transfer                       │
│  from_transaction_id ◄─────┬─────► to_transaction_id
│  from_account_id     ◄─────┴─────► to_account_id
└─────────────────────────────────────────────┘
```

---

*This addendum is required reading for implementation. All entities defined here are part of the MVP data model.*
