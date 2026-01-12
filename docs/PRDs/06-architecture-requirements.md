# Architecture Requirements for Local-First then Cloud - LedgerLens

## 6.1 Target Architecture

### Local Core

- **Shared "Finance Engine" library**
  - Parsing, normalization, dedupe, categorization, rules, receipt splitting

- Local DB + encrypted attachments
- UI layer per platform

### Modular Services

| Service | Responsibility |
|---------|----------------|
| Import service | File ingestion and parsing |
| Categorization service | Transaction classification |
| Receipt parsing service | OCR and item extraction |
| Rules service | Rule matching and management |
| Sync service | Placeholder (disabled in MVP) |

---

## 6.2 Recommended Implementation Pattern

### Option A: Preferred for Budget and Reuse

- **Shared core:** Kotlin Multiplatform or Rust core + bindings
- **Android UI:** Kotlin + Jetpack Compose
- **Desktop UI:** Compose Desktop
- **ML inference:** TFLite models shipped with app
- **OCR:**
  - Android: on-device OCR SDK
  - Desktop: PDF text extraction + OCR fallback

### Option B: Alternative

- Desktop local server (Python/FastAPI) + Electron UI
- Android native app calling shared model bundles
- Higher packaging and maintenance cost

---

## 6.3 Cloud Migration Path

### Phase 1: Local Only

- No backend required.
- Update distribution via app store / installer updates.

### Phase 2: Optional Cloud Sync

- **Add user identity:**
  - Email magic link or device keypair

- **Sync service:**
  - Per-user encrypted blobs or record-level sync

- **Conflict resolution:**
  - Last-write-wins for categories and notes
  - Merge for rules using timestamps

### Phase 3: Cloud Compute for Heavy Parsing

- Upload only source files if user opts in.
- **Cloud OCR/parsing:**
  - Better accuracy for complex PDFs
- Return canonical transactions to device.
- Keep raw bank docs optional and encrypt.

### Phase 4: Read-Only Bank Aggregation

- Integrate an aggregator to fetch transactions.
- Token-based auth; never store bank passwords.
- Map aggregator categories to internal categories; still learn user overrides.

### Phase 5: Assisted Automation

- Draft bill payments, suggested transfers.
- Human approval required in-app.

### Phase 6: Fully Automated Agent

**Requires:**
- Verified identity
- Explicit permissions
- Robust anomaly detection
- Audit logs
- Rollback and dispute workflows
- Regulatory/compliance review depending on jurisdictions and money movement scope

---

## 6.4 Cloud API Surface (Draft)

### Auth

| Endpoint | Method |
|----------|--------|
| `/auth/start` | POST |
| `/auth/verify` | POST |
| `/auth/refresh` | POST |

### Sync

| Endpoint | Method |
|----------|--------|
| `/sync/snapshot` | GET |
| `/sync/changes` | POST |
| `/sync/changes?since=cursor` | GET |

### Parse and Categorize as a Service

| Endpoint | Method |
|----------|--------|
| `/parse/statement` | POST |
| `/parse/receipt` | POST |
| `/categorize/batch` | POST |
| `/train/feedback` | POST (opt-in) |

### Observability

| Endpoint | Method |
|----------|--------|
| `/telemetry/events` | POST (opt-in) |
