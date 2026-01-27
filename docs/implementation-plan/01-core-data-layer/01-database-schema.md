# 01: Database Schema

## Overview

Implement all database entities using SQLDelight, following the data model from [02a-data-model-addendum.md](../../PRDs/02a-data-model-addendum.md).

---

## Implementation Steps

### Step 1: Create SQLDelight Directory Structure

```
shared/src/commonMain/sqldelight/
├── com/ledgerlens/db/
│   ├── Account.sq
│   ├── SourceFile.sq
│   ├── ImportJob.sq
│   ├── Merchant.sq
│   ├── MerchantAlias.sq
│   ├── ImportedTransaction.sq
│   ├── TransactionOverride.sq
│   ├── Transfer.sq
│   ├── Category.sq
│   ├── Rule.sq
│   ├── CorrectionEvent.sq
│   ├── Receipt.sq
│   ├── ReceiptItem.sq
│   ├── Participant.sq
│   ├── ItemAllocation.sq
│   ├── Settlement.sq
│   └── Views.sq
└── migrations/
    └── 1.sqm
```

### Step 2: Core Entity Definitions

**Account.sq:**
```sql
CREATE TABLE account (
    id TEXT NOT NULL PRIMARY KEY,
    display_name TEXT NOT NULL,
    institution_name TEXT,
    account_type TEXT NOT NULL DEFAULT 'checking',
    currency_code TEXT NOT NULL DEFAULT 'USD',
    last_four TEXT,
    is_active INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    color TEXT,
    icon TEXT,
    notes TEXT,
    CHECK (account_type IN ('checking', 'savings', 'credit', 'investment', 'cash', 'other')),
    CHECK (last_four IS NULL OR length(last_four) = 4)
);

CREATE INDEX idx_account_active_name ON account(is_active, display_name);

insert:
INSERT INTO account(id, display_name, institution_name, account_type, currency_code, last_four, is_active, created_at, color, icon, notes)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);

selectAll:
SELECT * FROM account WHERE is_active = 1 ORDER BY display_name;

selectById:
SELECT * FROM account WHERE id = ?;
```

**SourceFile.sq:**
```sql
CREATE TABLE source_file (
    id TEXT NOT NULL PRIMARY KEY,
    content_hash TEXT NOT NULL UNIQUE,
    original_filename TEXT NOT NULL,
    mime_type TEXT NOT NULL,
    size_bytes INTEGER NOT NULL,
    file_type TEXT NOT NULL,
    imported_at INTEGER NOT NULL,
    encryption_key_id TEXT NOT NULL,
    storage_path TEXT NOT NULL,
    page_count INTEGER,
    parse_status TEXT NOT NULL DEFAULT 'pending',
    parse_error TEXT,
    retention_policy TEXT NOT NULL DEFAULT 'keep',
    deleted_at INTEGER,
    account_id TEXT REFERENCES account(id),
    CHECK (file_type IN ('pdf_statement', 'csv_statement', 'receipt_image', 'receipt_pdf', 'email_pdf', 'other')),
    CHECK (parse_status IN ('pending', 'parsing', 'complete', 'failed', 'needs_review'))
);

CREATE INDEX idx_source_file_hash ON source_file(content_hash);
CREATE INDEX idx_source_file_type_status ON source_file(file_type, parse_status);
CREATE INDEX idx_source_file_account ON source_file(account_id);

selectByHash:
SELECT * FROM source_file WHERE content_hash = ?;

insert:
INSERT INTO source_file(id, content_hash, original_filename, mime_type, size_bytes, file_type, imported_at, encryption_key_id, storage_path, page_count, parse_status, parse_error, retention_policy, account_id)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

**ImportJob.sq:**
```sql
CREATE TABLE import_job (
    id TEXT NOT NULL PRIMARY KEY,
    source_file_id TEXT NOT NULL REFERENCES source_file(id),
    status TEXT NOT NULL DEFAULT 'queued',
    started_at INTEGER,
    completed_at INTEGER,
    progress_percent INTEGER NOT NULL DEFAULT 0,
    current_stage TEXT,
    transactions_found INTEGER NOT NULL DEFAULT 0,
    transactions_new INTEGER NOT NULL DEFAULT 0,
    transactions_dupe INTEGER NOT NULL DEFAULT 0,
    transactions_error INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    error_details TEXT,
    checkpoint_data BLOB,
    CHECK (status IN ('queued', 'extracting', 'parsing', 'normalizing', 'categorizing', 'deduping', 'complete', 'failed', 'cancelled')),
    CHECK (progress_percent >= 0 AND progress_percent <= 100)
);

CREATE INDEX idx_import_job_source ON import_job(source_file_id);
CREATE INDEX idx_import_job_status ON import_job(status, started_at);

selectById:
SELECT * FROM import_job WHERE id = ?;

updateProgress:
UPDATE import_job SET progress_percent = ?, current_stage = ? WHERE id = ?;
```

### Step 3: Merchant Entities

**Merchant.sq:**
```sql
CREATE TABLE merchant (
    id TEXT NOT NULL PRIMARY KEY,
    canonical_name TEXT NOT NULL UNIQUE,
    display_name TEXT,
    category_id TEXT REFERENCES category(id),
    logo_url TEXT,
    merchant_type TEXT,
    is_subscription INTEGER NOT NULL DEFAULT 0,
    typical_amount_min INTEGER,
    typical_amount_max INTEGER,
    transaction_count INTEGER NOT NULL DEFAULT 0,
    first_seen_at INTEGER NOT NULL,
    last_seen_at INTEGER NOT NULL,
    CHECK (merchant_type IS NULL OR merchant_type IN ('retail', 'restaurant', 'utility', 'subscription', 'transfer', 'other'))
);

CREATE INDEX idx_merchant_canonical ON merchant(canonical_name);
CREATE INDEX idx_merchant_category ON merchant(category_id);
CREATE INDEX idx_merchant_subscription ON merchant(is_subscription);

selectByCanonicalName:
SELECT * FROM merchant WHERE canonical_name = ?;

upsert:
INSERT OR REPLACE INTO merchant(id, canonical_name, display_name, category_id, merchant_type, is_subscription, typical_amount_min, typical_amount_max, transaction_count, first_seen_at, last_seen_at)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

**MerchantAlias.sq:**
```sql
CREATE TABLE merchant_alias (
    id TEXT NOT NULL PRIMARY KEY,
    merchant_id TEXT NOT NULL REFERENCES merchant(id),
    alias_pattern TEXT NOT NULL,
    match_type TEXT NOT NULL DEFAULT 'contains',
    source TEXT NOT NULL DEFAULT 'parser',
    created_at INTEGER NOT NULL,
    CHECK (match_type IN ('exact', 'contains', 'regex')),
    CHECK (source IN ('parser', 'user', 'learned'))
);

CREATE INDEX idx_merchant_alias_merchant ON merchant_alias(merchant_id);
CREATE INDEX idx_merchant_alias_pattern ON merchant_alias(alias_pattern);

selectByPattern:
SELECT ma.*, m.canonical_name, m.category_id
FROM merchant_alias ma
JOIN merchant m ON ma.merchant_id = m.id
WHERE ma.alias_pattern = ? AND ma.match_type = 'exact'
UNION
SELECT ma.*, m.canonical_name, m.category_id
FROM merchant_alias ma
JOIN merchant m ON ma.merchant_id = m.id
WHERE ? LIKE '%' || ma.alias_pattern || '%' AND ma.match_type = 'contains';
```

### Step 4: Transaction Entities

**ImportedTransaction.sq:**

Design note: `fingerprint` is a dedupe signal and can legitimately collide (e.g., two identical purchases). We avoid enforcing fingerprint uniqueness and instead (a) ensure per-file row idempotency with `UNIQUE(source_file_id, source_row_ref)` and (b) record cross-file fingerprint matches into `duplicate_candidate` for later review.
```sql
CREATE TABLE imported_transaction (
    id TEXT NOT NULL PRIMARY KEY,
    import_job_id TEXT NOT NULL REFERENCES import_job(id),
    source_file_id TEXT NOT NULL REFERENCES source_file(id),
    source_row_ref TEXT NOT NULL,
    fingerprint TEXT NOT NULL,
    account_id TEXT REFERENCES account(id),
    posted_date INTEGER NOT NULL,
    transaction_date INTEGER,
    description_raw TEXT NOT NULL,
    merchant_normalized TEXT NOT NULL,
    merchant_id TEXT REFERENCES merchant(id),
    amount_minor_units INTEGER NOT NULL,
    currency_code TEXT NOT NULL DEFAULT 'USD',
    balance_after INTEGER,
    category_id_auto TEXT REFERENCES category(id),
    category_confidence REAL,
    category_reason TEXT,
    parse_warnings TEXT,
    imported_at INTEGER NOT NULL,
    UNIQUE(source_file_id, source_row_ref)
);

CREATE INDEX idx_imported_txn_fingerprint ON imported_transaction(fingerprint);
CREATE INDEX idx_imported_txn_account_date ON imported_transaction(account_id, posted_date);
CREATE INDEX idx_imported_txn_merchant ON imported_transaction(merchant_id);
CREATE INDEX idx_imported_txn_import_job ON imported_transaction(import_job_id);

selectByFingerprint:
SELECT * FROM imported_transaction WHERE fingerprint = ?;

selectMatchRefsByFingerprint:
SELECT id, source_file_id FROM imported_transaction WHERE fingerprint = ?;

selectByDateRange:
SELECT * FROM imported_transaction
WHERE account_id = ? AND posted_date BETWEEN ? AND ?
ORDER BY posted_date DESC;
```

**DuplicateCandidate.sq:**
```sql
CREATE TABLE duplicate_candidate (
    transaction_id_a TEXT NOT NULL REFERENCES imported_transaction(id),
    transaction_id_b TEXT NOT NULL REFERENCES imported_transaction(id),
    fingerprint TEXT NOT NULL,
    score REAL NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING',
    created_at INTEGER NOT NULL,
    reviewed_at INTEGER,
    metadata_json TEXT NOT NULL DEFAULT '{}',
    PRIMARY KEY(transaction_id_a, transaction_id_b),
    CHECK (transaction_id_a < transaction_id_b),
    CHECK (status IN ('PENDING', 'CONFIRMED', 'DISMISSED'))
);

selectPending:
SELECT * FROM duplicate_candidate
WHERE status = 'PENDING'
ORDER BY score DESC, created_at ASC
LIMIT ?;
```

**TransactionOverride.sq:**
```sql
CREATE TABLE transaction_override (
    transaction_id TEXT NOT NULL PRIMARY KEY REFERENCES imported_transaction(id),
    category_id TEXT REFERENCES category(id),
    merchant_override TEXT,
    notes TEXT,
    tags TEXT NOT NULL DEFAULT '[]',
    is_excluded INTEGER NOT NULL DEFAULT 0,
    exclude_reason TEXT,
    is_reviewed INTEGER NOT NULL DEFAULT 0,
    reviewed_at INTEGER,
    updated_at INTEGER NOT NULL,
    sync_version INTEGER NOT NULL DEFAULT 1,
    CHECK (exclude_reason IS NULL OR exclude_reason IN ('duplicate', 'refund', 'internal', 'business', 'other'))
);

CREATE INDEX idx_transaction_override_updated ON transaction_override(updated_at);

upsert:
INSERT OR REPLACE INTO transaction_override(transaction_id, category_id, merchant_override, notes, tags, is_excluded, exclude_reason, is_reviewed, reviewed_at, updated_at, sync_version)
VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
```

### Step 5: Transfer Entity

**Transfer.sq:**
```sql
CREATE TABLE transfer (
    id TEXT NOT NULL PRIMARY KEY,
    from_transaction_id TEXT REFERENCES imported_transaction(id),
    to_transaction_id TEXT REFERENCES imported_transaction(id),
    from_account_id TEXT NOT NULL REFERENCES account(id),
    to_account_id TEXT NOT NULL REFERENCES account(id),
    amount_minor_units INTEGER NOT NULL,
    currency_code TEXT NOT NULL,
    fx_rate REAL,
    fx_amount_minor INTEGER,
    status TEXT NOT NULL DEFAULT 'suspected',
    match_confidence REAL NOT NULL,
    matched_by TEXT NOT NULL DEFAULT 'auto',
    created_at INTEGER NOT NULL,
    confirmed_at INTEGER,
    notes TEXT,
    CHECK (from_transaction_id IS NOT NULL OR to_transaction_id IS NOT NULL),
    CHECK (from_account_id != to_account_id),
    CHECK (status IN ('suspected', 'confirmed', 'rejected')),
    CHECK (matched_by IN ('auto', 'user', 'rule'))
);

CREATE INDEX idx_transfer_from_txn ON transfer(from_transaction_id);
CREATE INDEX idx_transfer_to_txn ON transfer(to_transaction_id);
CREATE INDEX idx_transfer_accounts ON transfer(from_account_id, to_account_id);
CREATE INDEX idx_transfer_status ON transfer(status);
```

### Step 6: Category and Rule Entities

**Category.sq:**
```sql
CREATE TABLE category (
    id TEXT NOT NULL PRIMARY KEY,
    name TEXT NOT NULL,
    parent_id TEXT REFERENCES category(id),
    system_default INTEGER NOT NULL DEFAULT 0,
    user_custom INTEGER NOT NULL DEFAULT 0,
    icon TEXT,
    color TEXT,
    sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_category_parent ON category(parent_id);

selectAll:
SELECT * FROM category ORDER BY sort_order, name;

selectById:
SELECT * FROM category WHERE id = ?;

-- Seed default categories
insertDefaults:
INSERT OR IGNORE INTO category(id, name, parent_id, system_default, sort_order) VALUES
('income', 'Income', NULL, 1, 0),
('salary', 'Salary', 'income', 1, 1),
('investments', 'Investments', 'income', 1, 2),
('housing', 'Housing', NULL, 1, 10),
('rent', 'Rent/Mortgage', 'housing', 1, 11),
('utilities', 'Utilities', 'housing', 1, 12),
('food', 'Food & Dining', NULL, 1, 20),
('groceries', 'Groceries', 'food', 1, 21),
('restaurants', 'Restaurants', 'food', 1, 22),
('transport', 'Transportation', NULL, 1, 30),
('gas', 'Gas', 'transport', 1, 31),
('parking', 'Parking', 'transport', 1, 32),
('shopping', 'Shopping', NULL, 1, 40),
('entertainment', 'Entertainment', NULL, 1, 50),
('subscriptions', 'Subscriptions', 'entertainment', 1, 51),
('health', 'Health & Medical', NULL, 1, 60),
('transfer', 'Transfers', NULL, 1, 90),
('uncategorized', 'Uncategorized', NULL, 1, 99);
```

**Rule.sq:**
```sql
CREATE TABLE rule (
    id TEXT NOT NULL PRIMARY KEY,
    rule_type TEXT NOT NULL,
    match_expression TEXT NOT NULL,
    target_category_id TEXT NOT NULL REFERENCES category(id),
    priority INTEGER NOT NULL DEFAULT 100,
    created_by_user INTEGER NOT NULL DEFAULT 1,
    enabled INTEGER NOT NULL DEFAULT 1,
    created_at INTEGER NOT NULL,
    CHECK (rule_type IN ('merchant_contains', 'merchant_equals', 'description_regex', 'amount_range', 'mcc', 'account'))
);

CREATE INDEX idx_rule_priority ON rule(enabled, priority);
CREATE INDEX idx_rule_category ON rule(target_category_id);

selectEnabled:
SELECT * FROM rule WHERE enabled = 1 ORDER BY priority ASC;
```

### Step 7: Transaction View

**Views.sq:**
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
    COALESCE(tor.is_excluded, 0) AS is_excluded,
    COALESCE(tor.is_reviewed, 0) AS is_reviewed,
    it.fingerprint AS hash_fingerprint,
    it.imported_at
FROM imported_transaction it
LEFT JOIN transaction_override tor ON it.id = tor.transaction_id
LEFT JOIN merchant m ON it.merchant_id = m.id;

selectAllTransactions:
SELECT * FROM transaction_view ORDER BY posted_date DESC;

selectByAccount:
SELECT * FROM transaction_view WHERE account_id = ? ORDER BY posted_date DESC;
```

### Step 8: Receipt Entities (See Sprint 03)

Create placeholder files for Receipt, ReceiptItem, Participant, ItemAllocation, Settlement - these will be fully implemented in Sprint 03.

---

## Acceptance Criteria

- [ ] All 16+ entities defined in SQLDelight
- [ ] All indexes created for query optimization
- [ ] Default categories seeded
- [ ] transaction_view computes correctly
- [ ] Migrations run successfully
- [ ] Schema generates for both Android and Desktop

---

## Dependencies

- SQLDelight 2.0.1+
- Sprint 00 complete (KMP setup)

---

## Estimated Complexity

**High** - Many entities with complex relationships.

---

## Verification Commands

```bash
# Generate schema
./gradlew :shared:generateCommonMainLedgerLensDatabaseSchema

# Run tests
./gradlew :shared:check
```
