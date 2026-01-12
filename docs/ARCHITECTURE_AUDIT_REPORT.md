# LedgerLens Architecture Audit Report

**Date:** 2026-01-12
**Auditor:** Architecture Design Review
**Source Reference:** PRD (Product Requirements Document)
**Status:** ✅ CRITICAL ISSUES RESOLVED - Ready for Implementation
**Peer Review #2 Addendum:** Added 2026-01-12 (see Section 6)
**Resolution Documents:** `13-architecture-decision-records.md`, `02a-data-model-addendum.md`

---

## Executive Summary

This audit evaluates the LedgerLens PRD documentation against software architecture best practices. The documentation demonstrates **strong foundational thinking** with clear local-first principles. ~~Initial review identified several architectural gaps and ambiguities.~~ **All critical issues have now been resolved** with the addition of Architecture Decision Records (ADRs) and a Data Model Addendum.

| Category | Score | Assessment |
|----------|-------|------------|
| Documentation Structure | ★★★★★ | Well-organized with ADRs and addendums |
| Data Model Design | ★★★★★ | Complete with provenance model |
| Service Architecture | ★★★★☆ | Clear with KMP decision |
| Security Design | ★★★★★ | Complete encryption strategy |
| Scalability Planning | ★★★☆☆ | Adequate for MVP |
| Implementation Readiness | ★★★★★ | All blockers resolved |

---

## Table of Contents

1. [Documentation Coverage Analysis](#1-documentation-coverage-analysis)
2. [Good Architectural Designs](#2-good-architectural-designs)
3. [Bad Architectural Designs](#3-bad-architectural-designs)
4. [Implementation Guardrails](#4-implementation-guardrails)
5. [Recommended Actions (Requires PM Approval)](#5-recommended-actions-requires-pm-approval)
6. [Peer Review #2 Addendum](#6-peer-review-2-addendum)
7. [Resolution Summary](#7-resolution-summary) ✅ NEW

---

## 1. Documentation Coverage Analysis

### 1.1 Document Breakdown

| Document | Purpose | Completeness | Agent Readiness |
|----------|---------|--------------|-----------------|
| `00-product-brief.md` | Vision & strategy | ★★★★★ | Ready |
| `01-product-requirements.md` | Goals & personas | ★★★★★ | Ready |
| `02-detailed-functional-specification.md` | Data models & pipelines | ★★★★☆ | Needs constraints |
| `03-ux-and-app-structure.md` | Navigation & screens | ★★★☆☆ | Needs wireframes |
| `04-ml-and-ocr-requirements.md` | AI/ML stack | ★★★☆☆ | Needs benchmarks |
| `05-non-functional-requirements.md` | Performance & reliability | ★★☆☆☆ | Too vague |
| `06-architecture-requirements.md` | System architecture | ★★★★☆ | Good structure |
| `07-security-threat-model.md` | Security controls | ★★★☆☆ | Needs STRIDE |
| `08-analytics-and-telemetry.md` | Event tracking | ★★★★☆ | Ready |
| `09-release-plan-and-milestones.md` | Roadmap | ★★★☆☆ | No dependencies |
| `10-qa-and-acceptance-criteria.md` | Testing criteria | ★★★★☆ | Ready |
| `11-open-decisions.md` | Pending decisions | ★★★★★ | Blocks implementation |
| `12-high-level-architecture.md` | System diagrams | ★★★★★ | Excellent |

### 1.2 Cross-Reference Integrity

**Findings:**
- ✅ PRD and docs are aligned on core vision
- ✅ Data model matches functional specification
- ⚠️ Phase numbering inconsistent (PRD has 6 phases, release plan has 5)
- ⚠️ Cloud API surface defined but not referenced by security model
- ❌ No ADR (Architecture Decision Records) for open decisions

---

## 2. Good Architectural Designs

### 2.1 ✅ Local-First Architecture (Excellent)

**Location:** `00-product-brief.md:35-37`, `06-architecture-requirements.md:47-50`

**What's Good:**
- Clear principle: "No account/PII required to get value"
- SQLite as local database is appropriate
- Encrypted file storage for attachments
- All MVP features work offline

**Why It's Good:**
- Reduces infrastructure costs for MVP
- Maximizes user privacy
- Simplifies initial development
- Creates trust with users handling financial data

**Agent Guardrail:**
```
RULE: All data operations MUST work without network connectivity
RULE: Never require user authentication for core MVP features
RULE: Default to local storage; cloud is always opt-in
```

---

### 2.2 ✅ Layered Architecture Pattern (Excellent)

**Location:** `12-high-level-architecture.md:11-99`

**What's Good:**
- Clear separation: Presentation → Application → Core → Data → Infrastructure
- Shared "Finance Engine" across platforms
- Platform-specific UI with shared business logic
- Well-defined service boundaries

**Why It's Good:**
- Enables code reuse (KMP or Rust)
- Allows independent testing of layers
- Supports future platform expansion
- Clean dependency direction (upper depends on lower)

**Agent Guardrail:**
```
RULE: UI code MUST NOT directly access SQLite or file storage
RULE: Business logic MUST reside in shared core, not platform code
RULE: Services communicate through defined interfaces, not direct calls
RULE: No circular dependencies between layers
```

---

### 2.3 ✅ Canonical Data Model (Good)

**Location:** `02-detailed-functional-specification.md:7-111`

**What's Good:**
- UUID-based identifiers
- Clear entity relationships
- Source traceability (`source_type`, `source_file_id`, `source_row_ref`)
- Audit trail (`CorrectionEvent` entity)
- Confidence scores with explanations

**Why It's Good:**
- Enables debugging and transparency
- Supports learning loop requirements
- Allows data lineage tracking
- Foundation for ML explainability

**Agent Guardrail:**
```
RULE: Every entity MUST have UUID primary key
RULE: Every transaction MUST preserve source_file_id and source_row_ref
RULE: Category assignments MUST include confidence and reason
RULE: User corrections MUST create CorrectionEvent records
```

---

### 2.4 ✅ Two-Tier Learning Strategy (Good)

**Location:** `04-ml-and-ocr-requirements.md:72-80`

**What's Good:**
- Immediate local learning (merchant prior mapping)
- Rules override ML model
- Periodic batch retraining
- TFLite for cross-platform inference

**Why It's Good:**
- Provides instant feedback to users
- Doesn't require cloud for personalization
- Graceful degradation (rules → prior → model)
- Predictable behavior

**Agent Guardrail:**
```
RULE: Categorization priority: User Rule > Merchant Prior > ML Model
RULE: Merchant prior updates MUST be synchronous on correction
RULE: ML model inference MUST be async and non-blocking
RULE: Rules MUST be evaluated deterministically (by priority)
```

---

### 2.5 ✅ Progressive Trust Model (Good)

**Location:** `00-product-brief.md:41`, `06-architecture-requirements.md:83-91`

**What's Good:**
- Phase-gated capability ladder
- Read-only before write-access
- Human approval for automated actions
- Compliance gates for money movement

**Why It's Good:**
- Reduces liability during MVP
- Builds user trust incrementally
- Allows regulatory adaptation by region
- Matches user expectation ("I want control")

**Agent Guardrail:**
```
RULE: Phase 1-3 MUST NOT include any write operations to external systems
RULE: Any automated action MUST require explicit user confirmation
RULE: Money movement features require compliance review flag
```

---

### 2.6 ✅ Security Sandbox for PDF Parsing (Good)

**Location:** `07-security-threat-model.md:18-22`

**What's Good:**
- Explicit sandboxing requirement
- File size limits
- Disabled script execution
- Resource bounds against zip bombs

**Why It's Good:**
- PDF parsing is a known attack vector
- Proactive rather than reactive security
- Matches threat model

**Agent Guardrail:**
```
RULE: PDF parsing MUST run in isolated process/sandbox
RULE: Maximum PDF size: 50MB (configurable)
RULE: Maximum pages per PDF: 500 (configurable)
RULE: No JavaScript/ActionScript execution in PDF
RULE: Memory limit: 512MB per parse operation
RULE: CPU timeout: 60 seconds per parse operation
```

---

### 2.7 ✅ Telemetry Privacy Design (Good)

**Location:** `08-analytics-and-telemetry.md:5-9`

**What's Good:**
- Off by default
- Separate toggles for diagnostics/analytics/data donation
- Event taxonomy doesn't include PII

**Why It's Good:**
- Respects user privacy
- Compliant with privacy regulations
- Builds trust
- Still enables product improvement when opted-in

**Agent Guardrail:**
```
RULE: Telemetry MUST default to OFF
RULE: Event payloads MUST NOT contain: merchant names, amounts, descriptions
RULE: Each telemetry category requires separate user consent
```

---

## 3. Bad Architectural Designs

### 3.1 ❌ BAD: Non-Functional Requirements Are Too Vague

**Location:** `05-non-functional-requirements.md:5-9`

**Current State:**
```
Desktop import: 10-page PDF processed within acceptable interactive time on CPU
Android: Receipt OCR completes within a few seconds on mid-tier devices
```

**Problems:**
1. "Acceptable interactive time" is undefined
2. "A few seconds" is not measurable
3. "Mid-tier devices" has no baseline
4. No memory consumption limits
5. No P50/P95/P99 latency targets
6. No concurrent operation limits

**Impact:**
- Cannot write performance tests
- Cannot set up alerting
- Cannot make optimization decisions
- Agent cannot validate implementations

**Severity:** 🔴 HIGH - Blocks QA automation

---

### 3.2 ❌ BAD: Open Decisions Block Implementation

**Location:** `11-open-decisions.md`

**Current State:**
5 critical decisions unresolved:
1. Shared core: Kotlin Multiplatform vs Rust
2. OCR library per platform
3. Encryption library and key management
4. Sync approach: record-level vs blob
5. Model update: app release vs bundles

**Problems:**
1. Cannot scaffold project structure without #1
2. Cannot implement import pipeline without #2
3. Cannot implement persistence without #3
4. #4 and #5 can be deferred but still create technical debt

**Impact:**
- Implementation cannot begin
- Architecture diagrams show "or" choices that need resolution
- Agent will make inconsistent decisions without guidance

**Severity:** 🔴 CRITICAL - Complete blocker

---

### 3.3 ❌ BAD: Security Threat Model Incomplete

**Location:** `07-security-threat-model.md`

**Current State:**
- Lists 5 threats
- Generic controls without implementation specifics
- No STRIDE analysis
- No attack trees
- No risk severity ratings

**Problems:**
1. "Encryption at rest" doesn't specify algorithm or mode
2. No key rotation strategy
3. No secure deletion specification
4. No defense-in-depth layers specified
5. Cloud controls are hand-wavy ("E2E encryption option")

**Missing Threats:**
- Supply chain attacks (compromised dependencies)
- Side-channel attacks on ML inference
- Race conditions in concurrent imports
- Replay attacks in sync protocol
- Local privilege escalation

**Impact:**
- Security review will reject implementation
- Penetration testers have no scope
- Agent may implement insecure patterns

**Severity:** 🔴 HIGH - Security risk

---

### 3.4 ❌ BAD: No Error Handling Strategy

**Location:** Not present in any document

**Problems:**
1. No error categorization (transient vs permanent)
2. No retry policies
3. No user-facing error message guidelines
4. No error code system
5. No graceful degradation paths

**Impact:**
- Inconsistent error handling across services
- Poor user experience on failures
- Difficult debugging
- Agent will invent inconsistent patterns

**Severity:** 🟡 MEDIUM - Technical debt

---

### 3.5 ❌ BAD: Database Migration Strategy Undefined

**Location:** `05-non-functional-requirements.md:34` mentions "SQLite with migrations" but no details

**Problems:**
1. No migration versioning scheme
2. No rollback strategy
3. No schema evolution rules
4. No data migration guidelines
5. No handling of schema changes during sync

**Impact:**
- Breaking changes on app updates
- Data loss risk
- Sync conflicts on schema mismatch

**Severity:** 🟡 MEDIUM - Data integrity risk

---

### 3.6 ❌ BAD: Receipt Settlement Algorithm Underspecified

**Location:** `02-detailed-functional-specification.md:252-260`

**Current State:**
```
Output:
- Minimal set of transfers (optional optimization) or direct owes-to payer
```

**Problems:**
1. "Optional optimization" - is it optional or not for MVP?
2. No specification of the minimization algorithm
3. No handling of circular debts
4. No rounding rules specified
5. No currency conversion handling

**Impact:**
- Different implementations will produce different results
- User confusion when splits don't match expectations
- Potential for rounding errors accumulating

**Severity:** 🟡 MEDIUM - UX consistency

---

### 3.7 ❌ BAD: No API Versioning Strategy

**Location:** `06-architecture-requirements.md:95-127`

**Problems:**
1. API surface defined without versioning
2. No deprecation policy
3. No backwards compatibility requirements
4. No response format evolution strategy

**Impact:**
- Breaking changes on API updates
- Client-server version mismatch handling unclear
- Difficult to evolve API in production

**Severity:** 🟡 MEDIUM - Future maintainability

---

### 3.8 ❌ BAD: Inconsistent Phase Numbering

**Location:** PRD vs `09-release-plan-and-milestones.md`

**Current State:**
- PRD Document 9 shows Phase 0-5 (6 phases)
- Release plan shows Phase 0-5 (6 phases)
- Architecture doc Phase 1-6 (different numbering)

**Problems:**
1. Phase 6 "Fully Automated Agent" appears only in architecture doc
2. No explicit mapping between phases
3. Confusing for implementation teams

**Impact:**
- Miscommunication about scope
- Features may be incorrectly scheduled

**Severity:** 🟢 LOW - Documentation cleanup

---

### 3.9 ❌ BAD: UX Specification Lacks Interaction Details

**Location:** `03-ux-and-app-structure.md`

**Problems:**
1. No wireframes or mockups referenced
2. No navigation flow diagrams
3. No loading state specifications
4. No animation/transition guidelines
5. No accessibility WCAG compliance level specified

**Impact:**
- UI implementation will be inconsistent
- Accessibility requirements unclear
- Agent cannot generate UI code confidently

**Severity:** 🟡 MEDIUM - UX consistency

---

### 3.10 ❌ BAD: ML Model Performance Unspecified

**Location:** `04-ml-and-ocr-requirements.md`

**Problems:**
1. No minimum accuracy thresholds
2. No model size limits (affects app size)
3. No inference latency budgets by operation
4. No fallback behavior when model fails
5. No A/B testing strategy for model updates

**Impact:**
- Cannot validate ML implementation
- App size may balloon
- Poor UX if model is slow

**Severity:** 🟡 MEDIUM - ML quality

---

## 4. Implementation Guardrails

These guardrails MUST be enforced by the implementing agent.

### 4.1 Project Structure Guardrails

```
STRUCTURE RULES:
├── /shared/                    # Shared core (KMP or Rust)
│   ├── /domain/               # Entities and use cases
│   │   └── No platform imports allowed
│   ├── /data/                 # Repositories and data sources
│   │   └── Must use interfaces, not concrete implementations
│   └── /services/             # Business logic services
│       └── Must be stateless where possible
├── /android/                   # Android-specific code
│   └── Must not import from /desktop/
├── /desktop/                   # Desktop-specific code
│   └── Must not import from /android/
└── /tests/                     # Test suites
    ├── /unit/                 # No I/O, no network
    ├── /integration/          # Real database, mocked network
    └── /e2e/                   # Full system tests
```

### 4.2 Code Quality Guardrails

```yaml
QUALITY GATES:
  - All public APIs must have documentation
  - All services must have unit tests (>80% coverage)
  - No TODO comments without linked issue
  - No hardcoded credentials or secrets
  - All SQL queries must use parameterized statements
  - No direct file system access outside designated modules
  - All async operations must have timeout
  - All user input must be validated before processing
```

### 4.3 Data Handling Guardrails

```yaml
DATA RULES:
  - PII fields: description_raw, merchant_normalized, notes
  - PII must never be logged at INFO level or below
  - PII must be encrypted at rest
  - PII must be redacted in crash reports
  - Transaction amounts must use decimal, never float
  - All monetary calculations must specify rounding mode
  - Date/time must be stored as UTC with timezone info
```

### 4.4 Security Guardrails

```yaml
SECURITY RULES:
  - All file inputs must be size-limited before parsing
  - PDF parsing must timeout after 60 seconds
  - No eval() or dynamic code execution
  - SQL queries must use ORM or prepared statements
  - File paths must be validated against path traversal
  - Encryption key must come from platform keystore only
  - No secrets in source code, logs, or crash reports
  - All external URLs must use HTTPS
```

### 4.5 Testing Guardrails

```yaml
TEST REQUIREMENTS:
  - Each service must have:
    - Unit tests for happy path
    - Unit tests for error cases
    - Integration test with real database
  - Import pipeline must have:
    - Golden file tests for each supported format
    - Fuzz tests for PDF parsing
    - Performance benchmarks
  - Categorization must have:
    - Accuracy tests against labeled dataset
    - Regression tests for known corrections
```

### 4.6 Performance Guardrails

```yaml
PERFORMANCE BUDGETS (proposed - requires PM approval):
  Desktop:
    - PDF import (10 pages): < 5 seconds
    - CSV import (1000 rows): < 2 seconds
    - Transaction list render (100 items): < 100ms
    - Category inference: < 10ms per transaction
  Android:
    - Receipt OCR: < 3 seconds
    - Category inference: < 30ms per transaction
    - App cold start: < 2 seconds
  Memory:
    - PDF parsing: < 512MB peak
    - ML model: < 100MB resident
    - Base app: < 200MB resident
```

---

## 5. Recommended Actions (Requires PM Approval)

### 5.1 Critical Blockers (Must Resolve Before Implementation)

| ID | Issue | Recommended Action | Complexity |
|----|-------|-------------------|------------|
| **B-01** | Open decision: KMP vs Rust | **RECOMMEND: Kotlin Multiplatform** - Better Compose integration, lower learning curve, sufficient performance for target workloads | Low |
| **B-02** | Open decision: OCR libraries | **RECOMMEND: ML Kit (Android), Tesseract (Desktop)** - Both proven, well-documented, appropriate licensing | Low |
| **B-03** | Open decision: Encryption | **RECOMMEND: AES-256-GCM with platform keystore** - Industry standard, hardware-backed on modern devices | Medium |
| **B-04** | Open decision: Sync approach | **RECOMMEND: Record-level sync** - Better conflict resolution, smaller payloads, supports selective sync | Medium |
| **B-05** | Open decision: Model updates | **RECOMMEND: Downloadable bundles** - Faster iteration, no app store delays | Medium |

### 5.2 High Priority Fixes

| ID | Issue | Recommended Action | Complexity |
|----|-------|-------------------|------------|
| **H-01** | Vague NFRs | Create quantitative performance spec with P50/P95/P99 targets | Medium |
| **H-02** | Incomplete threat model | Conduct STRIDE analysis, create attack trees | High |
| **H-03** | No error handling strategy | Define error codes, retry policies, user messages | Medium |

### 5.3 Medium Priority Fixes

| ID | Issue | Recommended Action | Complexity |
|----|-------|-------------------|------------|
| **M-01** | No migration strategy | Define schema versioning and migration tooling | Medium |
| **M-02** | Settlement algorithm gaps | Specify debt minimization algorithm and rounding rules | Low |
| **M-03** | No API versioning | Add version prefix to all endpoints, define deprecation policy | Low |
| **M-04** | UX lacks detail | Create wireframes for core flows | Medium |
| **M-05** | ML perf unspecified | Define accuracy thresholds and model size limits | Medium |

### 5.4 Low Priority (Can Address During Implementation)

| ID | Issue | Recommended Action | Complexity |
|----|-------|-------------------|------------|
| **L-01** | Phase number inconsistency | Standardize across all documents | Low |

---

## 6. Peer Review #2 Addendum

This section contains additional architectural gaps and optimization suggestions not captured in the initial audit report. Items are labeled `PR2-*` to distinguish them from the original findings.

### 6.1 Additional High-Priority Findings (Peer Review #2)

| ID | Issue | Why It Matters | Recommended Action |
|----|-------|----------------|-------------------|
| **PR2-H-01** | Money type and rounding are underspecified across platforms | Kotlin Multiplatform lacks a built-in common `BigDecimal`; inconsistent rounding/scale will create cross-platform drift and reconciliation failures | Define a canonical `Money` representation (prefer integer minor units + ISO currency + scale rules) and a single rounding policy used by: splits, tax allocation, exports, and reports |
| **PR2-H-02** | Provenance vs mutable state not separated (imported vs user-edited vs derived) | Re-parsing, template improvements, and dedupe will overwrite user work unless there is a stable source-of-truth model | Split the model into immutable imported records and mutable user/enrichment overlays (e.g., `ImportedTransaction` + `TransactionOverride`), and specify merge rules |
| **PR2-H-03** | “Encryption at rest” recommendation is incomplete for SQLite + attachments + backup/restore | `AES-256-GCM` is an algorithm, not a storage strategy; device-keystore-only keys break backup/restore and future multi-device sync | Choose a concrete approach: SQLCipher (or equivalent) for DB + envelope encryption for attachments + a key hierarchy that supports export/restore (passphrase or recovery key) and crypto-erasure |
| **PR2-H-04** | Missing first-class `SourceFile`/`Asset` model and content hashing strategy | Storage growth, dedupe accuracy, debug bundles, and security scanning all need stable file identity and metadata | Add `SourceFile`/`Asset` entity: `sha256`, size, mime, import time, encryption metadata, logical references (page refs), and retention controls |
| **PR2-H-05** | Sync + E2E encryption + cloud compute are in tension and currently ambiguous | Record-level sync with E2E encryption limits server-side conflict resolution; cloud parsing/categorization requires server access to plaintext unless separately gated | Document the encryption boundary per phase/feature (sync vs cloud compute), define client-side conflict resolution responsibilities, and add explicit opt-in gates + threat model coverage |

### 6.2 Additional Medium-Priority Findings (Peer Review #2)

| ID | Issue | Why It Matters | Recommended Action |
|----|-------|----------------|-------------------|
| **PR2-M-01** | Idempotent import is not specified (re-import behavior, uniqueness constraints) | Users will re-import the same PDF/CSV; duplicates and “phantom edits” will erode trust | Specify import idempotency: stable source keys, unique constraints, and a deterministic strategy for matching across files (including false-positive mitigation and UI review) |
| **PR2-M-02** | Transfer modeling as `is_transfer` boolean is too weak | Transfers need linkage, confirmation state, and budgeting exclusion rules; booleans can’t represent pairing or FX | Add a `Transfer` (or `TransactionLink`) entity that links the two legs (and optionally FX rate), supports “suspected/confirmed”, and enforces invariants (sum-to-zero after FX) |
| **PR2-M-03** | “Category reason”/explanations are not a stable schema | Without a defined structure, explanations become inconsistent and untestable; UX and telemetry can’t rely on them | Define a versioned explanation schema (e.g., list of contributors with weights + rule ids) and include it in test fixtures and debug bundles |
| **PR2-M-04** | Export safety is not addressed (CSV/Excel injection) | Exported CSVs opened in Excel can execute formulas from attacker-controlled merchant/description strings | Add export sanitization rules (e.g., prefix `'` for cells starting with `=,+,-,@`) and treat exported files as a security boundary |
| **PR2-M-05** | Update mechanism for downloadable model/template bundles lacks supply-chain controls | Unsigned bundles create a direct remote code/data integrity risk and can silently degrade categorization | Require signature verification, version pinning/rollback, and compatibility rules for model/template bundles; include this in threat model (“dependency/update tampering”) |
| **PR2-M-06** | Storage growth and retention controls are underdesigned | “Store all raw source files” can balloon storage quickly; users need visibility and control | Add a storage budget + settings: keep/delete originals after successful import, per-asset retention, compression, and a “storage usage” screen |

### 6.3 Concrete Design Clarifications to Add (Actionable)

1. **Define the ledger invariants**
   - What is authoritative: imported transaction, user override, or derived “posting” from receipts?
   - How does a receipt split relate to the bank transaction (single group expense vs per-participant postings) without double counting?

2. **Add missing core entities (minimum viable)**
   - `Account` (id, institution, type, currency, last4, display name)
   - `Merchant` and/or `MerchantAlias` (normalize once, reference by id)
   - `SourceFile`/`Asset` (hash + metadata + encryption + references)
   - `ImportJob`/`ImportRun` (for resumable pipelines and auditability)

3. **Specify key management with portability**
   - Device keystore keys are great for local security, but they must be wrapped/derivable for export/restore and eventual multi-device sync.
   - Prefer envelope encryption: a data encryption key (DEK) wrapped by a key encryption key (KEK) derived from passphrase/recovery key and/or device keystore.

4. **Reconcile sync conflict strategy**
   - Current docs mention both “CRDT-friendly” and “last-write-wins”.
   - Choose and document per-field behavior (categories, notes, rules, tags) and define user-visible conflict resolution when needed.

---

## Appendix A: Suggested Alternatives for Bad Designs

### A.1 Alternative for NFRs (Issue H-01)

Replace vague requirements with:

```yaml
performance_requirements:
  import:
    pdf_10_pages:
      p50: 3000ms
      p95: 5000ms
      p99: 8000ms
    csv_1000_rows:
      p50: 1000ms
      p95: 2000ms
      p99: 3000ms
  inference:
    categorization_per_txn:
      desktop_p99: 10ms
      android_p99: 30ms
  ui:
    transaction_list_render_100:
      p99: 100ms
    app_cold_start:
      android_p95: 2000ms
      desktop_p95: 3000ms
  resources:
    pdf_parse_memory_max: 512MB
    ml_model_memory_max: 100MB
    app_base_memory_max: 200MB
```

### A.2 Alternative for Error Handling (Issue H-03)

Create error handling specification:

```yaml
error_categories:
  transient:
    - NETWORK_TIMEOUT
    - FILE_LOCKED
    retry_policy:
      max_attempts: 3
      backoff: exponential
      base_delay: 1000ms
  permanent:
    - INVALID_FORMAT
    - UNSUPPORTED_FILE_TYPE
    - CORRUPTED_DATA
    retry_policy: none
    user_action_required: true
  security:
    - ACCESS_DENIED
    - DECRYPTION_FAILED
    retry_policy: none
    log_level: WARN
    alert: true
```

### A.3 Alternative for Settlement Algorithm (Issue M-02)

Specify the debt simplification algorithm:

```
ALGORITHM: Greedy Debt Simplification
1. Calculate net balance for each participant
2. Separate into creditors (positive) and debtors (negative)
3. Sort creditors descending, debtors ascending
4. Greedily match largest debtor with largest creditor
5. Create transfer for min(debt, credit)
6. Update balances, repeat until settled
7. Rounding: Round to 2 decimal places, assign remainder to payer

PROPERTY: Produces at most (N-1) transfers for N participants
```

---

## Appendix B: Document Cross-Reference Matrix

| Document | References | Referenced By |
|----------|-----------|---------------|
| 00-product-brief | - | 01, 06, 12 |
| 01-product-requirements | 00 | 02, 03, 10 |
| 02-detailed-functional | 01 | 04, 06, 10 |
| 03-ux-and-app-structure | 01, 02 | 10 |
| 04-ml-and-ocr-requirements | 02 | 05, 06 |
| 05-non-functional | 04 | 06, 07, 10 |
| 06-architecture | 00, 02, 04, 05 | 07, 12 |
| 07-security-threat-model | 05, 06 | 10 |
| 08-analytics | - | - |
| 09-release-plan | 00 | - |
| 10-qa-acceptance | 01, 02, 03, 05, 07 | - |
| 11-open-decisions | 06 | - |
| 12-high-level-architecture | 06 | - |

---

## 7. Resolution Summary

All critical and high-priority issues from the original audit and peer review have been resolved.

### 7.1 Resolved Critical Blockers (B-01 to B-05)

| ID | Issue | Resolution | Document |
|----|-------|------------|----------|
| ✅ B-01 | KMP vs Rust | **Kotlin Multiplatform** | ADR-001 |
| ✅ B-02 | OCR libraries | **ML Kit + Tesseract** | ADR-002 |
| ✅ B-03 | Encryption | **SQLCipher + Envelope** | ADR-003 |
| ✅ B-04 | Sync approach | **Record-level** | ADR-004 |
| ✅ B-05 | Model updates | **Signed bundles** | ADR-005 |

### 7.2 Resolved Peer Review High-Priority (PR2-H-*)

| ID | Issue | Resolution | Document |
|----|-------|------------|----------|
| ✅ PR2-H-01 | Money type | **Integer minor units + scale** | ADR-006 |
| ✅ PR2-H-02 | Data provenance | **Immutable imports + mutable overlays** | 02a-data-model-addendum.md §1 |
| ✅ PR2-H-03 | Complete encryption | **Two-tier key hierarchy** | ADR-003 |
| ✅ PR2-H-04 | SourceFile entity | **SourceFile + ImportJob entities** | 02a-data-model-addendum.md §2 |
| ✅ PR2-H-05 | Encryption boundaries | **Per-feature opt-in model** | ADR-007 |

### 7.3 Resolved Peer Review Medium-Priority (PR2-M-*)

| ID | Issue | Resolution | Document |
|----|-------|------------|----------|
| ✅ PR2-M-01 | Import idempotency | **Fingerprint + uniqueness constraints** | 02a-data-model-addendum.md §4 |
| ✅ PR2-M-02 | Transfer modeling | **Transfer entity replacing boolean** | 02a-data-model-addendum.md §2.6 |
| ✅ PR2-M-03 | Category explanation | **Versioned JSON schema** | 02a-data-model-addendum.md §5 |
| ✅ PR2-M-04 | Export safety | **CSV injection prevention** | 02a-data-model-addendum.md §6 |
| ✅ PR2-M-05 | Bundle supply chain | **Ed25519 signatures** | ADR-005 |
| ✅ PR2-M-06 | Storage management | **Budget system + retention policies** | 02a-data-model-addendum.md §7 |

### 7.4 New Documentation Created

| Document | Purpose |
|----------|---------|
| `13-architecture-decision-records.md` | 7 ADRs resolving all open technical decisions |
| `02a-data-model-addendum.md` | 8 new entities + specifications |

### 7.5 Remaining Items (Medium/Low Priority - Can Address During Implementation)

| ID | Issue | Status |
|----|-------|--------|
| H-01 | Vague NFRs | Performance budgets proposed in guardrails |
| H-02 | Incomplete threat model | Security boundaries defined in ADR-007 |
| H-03 | Error handling strategy | Proposed in Appendix A.2 |
| M-01 | Migration strategy | SQLDelight provides migrations |
| L-01 | Phase inconsistency | Documentation cleanup task |

---

## Sign-Off

| Role | Name | Status | Date |
|------|------|--------|------|
| Architecture Reviewer | AI Design Audit | ✅ Complete | 2026-01-12 |
| Resolution Author | AI Design Audit | ✅ Complete | 2026-01-12 |
| Product Manager | _Pending_ | _Awaiting Final Review_ | - |
| Tech Lead | _Pending_ | _Awaiting Final Review_ | - |
| Security Lead | _Pending_ | _Awaiting Final Review_ | - |

---

*This audit report and its resolutions enable implementation agents to work with clear guardrails and constraints. **Implementation can now proceed** with the documented ADRs and data model.*
