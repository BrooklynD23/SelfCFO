# High-Level Architecture Design - LedgerLens

## Overview

LedgerLens is a local-first personal finance application designed with a modular, layered architecture that supports offline operation while providing a clear migration path to cloud-enabled features.

---

## System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              PRESENTATION LAYER                              │
├─────────────────────────────────┬───────────────────────────────────────────┤
│         Desktop UI              │              Mobile UI                     │
│    (Compose Desktop)            │        (Jetpack Compose)                   │
│  ┌─────────────────────────┐    │    ┌─────────────────────────┐            │
│  │ • Dashboard             │    │    │ • Dashboard             │            │
│  │ • Transactions List     │    │    │ • Transactions List     │            │
│  │ • Import Screen         │    │    │ • Camera Capture        │            │
│  │ • Receipt Split View    │    │    │ • Receipt Split View    │            │
│  │ • Rules Editor          │    │    │ • Rules Editor          │            │
│  │ • Settings              │    │    │ • Settings              │            │
│  └─────────────────────────┘    │    └─────────────────────────┘            │
└─────────────────────────────────┴───────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           APPLICATION LAYER                                  │
│                        (Platform-Specific Bindings)                          │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │   ViewModel  │  │   ViewModel  │  │   ViewModel  │  │   ViewModel  │     │
│  │ Transactions │  │    Import    │  │   Receipts   │  │    Budget    │     │
│  └──────────────┘  └──────────────┘  └──────────────┘  └──────────────┘     │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         SHARED CORE / FINANCE ENGINE                         │
│                    (Kotlin Multiplatform or Rust + Bindings)                 │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         SERVICE LAYER                                │    │
│  ├──────────────┬──────────────┬──────────────┬──────────────┬─────────┤    │
│  │   Import     │ Categorize   │   Receipt    │    Rules     │  Sync   │    │
│  │   Service    │   Service    │   Service    │   Service    │ Service │    │
│  │              │              │              │              │(Phase 2)│    │
│  │ • PDF Parse  │ • ML Model   │ • OCR        │ • Matching   │         │    │
│  │ • CSV Parse  │ • Merchant   │ • Item Parse │ • Priority   │         │    │
│  │ • Normalize  │   Prior      │ • Split Calc │ • CRUD       │         │    │
│  │ • Dedupe     │ • Learning   │ • Settlement │              │         │    │
│  └──────────────┴──────────────┴──────────────┴──────────────┴─────────┘    │
│                                                                              │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │                         DOMAIN LAYER                                 │    │
│  ├──────────────────────────────────────────────────────────────────────┤    │
│  │  Entities: Transaction, Category, Rule, Receipt, ReceiptItem,       │    │
│  │            Participant, ItemAllocation, Settlement, CorrectionEvent │    │
│  │                                                                      │    │
│  │  Use Cases: ImportStatement, CategorizeTransaction, SplitReceipt,   │    │
│  │             CreateRule, ExportLedger, ComputeSettlement             │    │
│  └──────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           DATA / PERSISTENCE LAYER                           │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────────────────┐  ┌──────────────────────┐  ┌──────────────────┐   │
│  │     SQLite DB        │  │   Encrypted File     │  │   ML Model       │   │
│  │                      │  │      Storage         │  │    Bundles       │   │
│  │ • Transactions       │  │                      │  │                  │   │
│  │ • Categories         │  │ • PDF Attachments    │  │ • TFLite Models  │   │
│  │ • Rules              │  │ • Receipt Images     │  │ • Merchant Prior │   │
│  │ • Receipts           │  │ • Source Files       │  │   Tables         │   │
│  │ • Participants       │  │                      │  │                  │   │
│  │ • Settlements        │  │                      │  │                  │   │
│  │ • Correction Events  │  │                      │  │                  │   │
│  └──────────────────────┘  └──────────────────────┘  └──────────────────┘   │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
                                    │
                                    ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                        PLATFORM INFRASTRUCTURE LAYER                         │
├─────────────────────────────────┬───────────────────────────────────────────┤
│           Desktop               │                Android                     │
│  ┌─────────────────────────┐    │    ┌─────────────────────────┐            │
│  │ • OS Keychain           │    │    │ • Android Keystore      │            │
│  │ • File System Access    │    │    │ • Scoped Storage        │            │
│  │ • Tesseract OCR         │    │    │ • ML Kit / On-device    │            │
│  │ • PDF Libraries         │    │    │   OCR SDK               │            │
│  └─────────────────────────┘    │    └─────────────────────────┘            │
└─────────────────────────────────┴───────────────────────────────────────────┘
```

---

## Component Descriptions

### 1. Presentation Layer

| Component | Technology | Responsibility |
|-----------|------------|----------------|
| Desktop UI | Compose Desktop | Windows/macOS/Linux native UI |
| Mobile UI | Jetpack Compose | Android native UI |

**Key Screens:**
- Dashboard with spending overview
- Transaction list with search/filter
- Import wizard with progress tracking
- Receipt capture and split interface
- Rules and category management

### 2. Application Layer

Platform-specific ViewModels that:
- Manage UI state
- Coordinate between UI and core services
- Handle platform-specific concerns (permissions, file access)

### 3. Shared Core / Finance Engine

The heart of the application, shared across platforms:

| Service | Responsibilities |
|---------|------------------|
| **Import Service** | PDF parsing, CSV parsing, normalization, deduplication |
| **Categorization Service** | ML inference, merchant prior lookup, rule matching, learning loop |
| **Receipt Service** | OCR coordination, item extraction, split calculation, settlement |
| **Rules Service** | Rule CRUD, priority management, pattern matching |
| **Sync Service** | (Phase 2+) Encrypted sync, conflict resolution |

### 4. Domain Layer

**Core Entities:**
- `Transaction` - Canonical financial transaction
- `Category` - Hierarchical categorization
- `Rule` - User-defined categorization rules
- `Receipt` - Parsed receipt with metadata
- `ReceiptItem` - Individual line items
- `Participant` - People involved in splits
- `ItemAllocation` - How items are divided
- `Settlement` - Who owes whom

### 5. Data / Persistence Layer

| Store | Purpose |
|-------|---------|
| **SQLite** | Structured data with full-text search |
| **Encrypted File Storage** | Source documents and attachments |
| **ML Model Bundles** | TFLite models and learned mappings |

### 6. Platform Infrastructure

Platform-specific implementations for:
- Secure key storage
- File system access
- OCR engines
- PDF rendering

---

## Data Flow Diagrams

### Import Flow

```
┌──────────┐    ┌──────────┐    ┌───────────┐    ┌────────────┐    ┌──────────┐
│  User    │───▶│  Import  │───▶│  Parser   │───▶│ Normalizer │───▶│ Dedupe   │
│  Upload  │    │  Service │    │ (PDF/CSV) │    │            │    │          │
└──────────┘    └──────────┘    └───────────┘    └────────────┘    └──────────┘
                                                                         │
                                                                         ▼
┌──────────┐    ┌──────────┐    ┌───────────┐    ┌────────────┐    ┌──────────┐
│  Review  │◀───│  Inbox   │◀───│ Confidence│◀───│ Categorize │◀───│ Canonical│
│  UI      │    │  Filter  │    │   Check   │    │  Service   │    │  Txns    │
└──────────┘    └──────────┘    └───────────┘    └────────────┘    └──────────┘
```

### Categorization Learning Loop

```
┌─────────────────────────────────────────────────────────────────────────┐
│                                                                          │
│   ┌──────────┐         ┌──────────────┐         ┌──────────────┐        │
│   │   New    │────────▶│   ML Model   │────────▶│  Confidence  │        │
│   │   Txn    │         │  Inference   │         │    Score     │        │
│   └──────────┘         └──────────────┘         └──────────────┘        │
│                               ▲                        │                 │
│                               │                        ▼                 │
│   ┌──────────┐         ┌──────────────┐         ┌──────────────┐        │
│   │  Model   │◀────────│   Periodic   │         │    Rules     │        │
│   │  Update  │         │   Retrain    │         │   Override   │        │
│   └──────────┘         └──────────────┘         └──────────────┘        │
│                               ▲                        │                 │
│                               │                        ▼                 │
│   ┌──────────┐         ┌──────────────┐         ┌──────────────┐        │
│   │Correction│────────▶│   Merchant   │────────▶│    Final     │        │
│   │  Event   │         │    Prior     │         │   Category   │        │
│   └──────────┘         └──────────────┘         └──────────────┘        │
│        ▲                                                                 │
│        │                                                                 │
│   ┌──────────┐                                                          │
│   │   User   │                                                          │
│   │ Feedback │                                                          │
│   └──────────┘                                                          │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

### Receipt Split Flow

```
┌──────────┐    ┌──────────┐    ┌───────────┐    ┌────────────┐
│  Image/  │───▶│   OCR    │───▶│   Item    │───▶│  Validate  │
│   PDF    │    │  Engine  │    │  Parser   │    │   Totals   │
└──────────┘    └──────────┘    └───────────┘    └────────────┘
                                                       │
                                                       ▼
┌──────────┐    ┌──────────┐    ┌───────────┐    ┌────────────┐
│  Export  │◀───│Settlement│◀───│   Tax/    │◀───│   Assign   │
│  Share   │    │  Compute │    │   Fees    │    │   Items    │
└──────────┘    └──────────┘    └───────────┘    └────────────┘
```

---

## Technology Stack Summary

| Layer | Technology |
|-------|------------|
| **Shared Core** | Kotlin Multiplatform (preferred) or Rust |
| **Desktop UI** | Compose Desktop |
| **Android UI** | Jetpack Compose |
| **Database** | SQLite with SQLDelight |
| **ML Inference** | TensorFlow Lite |
| **OCR (Android)** | ML Kit Text Recognition |
| **OCR (Desktop)** | Tesseract or similar |
| **PDF Parsing** | Apache PDFBox (Desktop), iText (Android) |
| **Encryption** | Platform keychain + AES-256 |

---

## Security Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          SECURITY BOUNDARIES                             │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    APPLICATION SANDBOX                           │    │
│  │  ┌─────────────────┐    ┌─────────────────┐                     │    │
│  │  │   User Data     │    │   Source Files  │                     │    │
│  │  │  (Encrypted)    │    │   (Encrypted)   │                     │    │
│  │  └────────┬────────┘    └────────┬────────┘                     │    │
│  │           │                      │                               │    │
│  │           ▼                      ▼                               │    │
│  │  ┌─────────────────────────────────────────┐                    │    │
│  │  │         ENCRYPTION LAYER                 │                    │    │
│  │  │   Key derived from Platform Keystore     │                    │    │
│  │  └─────────────────────────────────────────┘                    │    │
│  │                                                                  │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                    PDF PARSING SANDBOX                           │    │
│  │  • Resource limits (memory, CPU time)                           │    │
│  │  • No script execution                                          │    │
│  │  • Input validation                                             │    │
│  └─────────────────────────────────────────────────────────────────┘    │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Cloud Migration Architecture (Future Phases)

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              CLOUD LAYER                                 │
│                           (Phase 2 onwards)                              │
├─────────────────────────────────────────────────────────────────────────┤
│                                                                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌─────────────┐  │
│  │    Auth      │  │    Sync      │  │   Parse      │  │  Telemetry  │  │
│  │   Service    │  │   Service    │  │   Service    │  │  (opt-in)   │  │
│  │              │  │              │  │              │  │             │  │
│  │ • Magic Link │  │ • E2E Encrypt│  │ • Cloud OCR  │  │ • Crash     │  │
│  │ • Device Key │  │ • Conflict   │  │ • Heavy PDF  │  │   Reports   │  │
│  │              │  │   Resolution │  │   Processing │  │ • Usage     │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  └─────────────┘  │
│                                                                          │
│  ┌──────────────────────────────────────────────────────────────────┐   │
│  │                    BANK AGGREGATION (Phase 4)                     │   │
│  │  • Token-based auth (no passwords stored)                        │   │
│  │  • Read-only transaction fetch                                   │   │
│  │  • Category mapping to internal schema                           │   │
│  └──────────────────────────────────────────────────────────────────┘   │
│                                                                          │
└─────────────────────────────────────────────────────────────────────────┘
                                    │
                                    │ HTTPS + E2E Encryption
                                    ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                           LOCAL APPLICATION                              │
│                    (Sync Service enabled in Phase 2+)                    │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## Key Architectural Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **Shared Core** | Kotlin Multiplatform | Code reuse, type safety, Compose ecosystem |
| **Local-first** | SQLite + encrypted files | Offline operation, user data ownership |
| **ML Inference** | TFLite | Cross-platform, efficient on-device |
| **Sync Strategy** | Record-level with E2E encryption | Granular conflict resolution |
| **OCR** | Platform-native SDKs | Best performance per platform |

---

## Scalability Considerations

- **Data Volume:** SQLite handles millions of transactions efficiently
- **Model Updates:** Downloadable bundles avoid app store delays
- **Multi-device:** CRDT-friendly data structures for conflict-free sync
- **Performance:** Lazy loading, pagination, background processing
