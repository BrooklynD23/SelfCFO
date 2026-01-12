# Product Requirements - LedgerLens

## Goals

1. Ingest bank statements (PDF/CSV) from any bank and normalize into a canonical transaction ledger.
2. Categorize transactions with high precision, improving over time from user corrections.
3. Parse receipts/bills into line items, then split costs by participant at item level, allocating tax/fees correctly.
4. Operate fully offline (local machine / device) for MVP.
5. Provide a clean migration path from localhost-only to cloud-enabled sync and compute.

## Non-goals for MVP

- Direct bank login credential storage.
- Bill pay execution, ACH initiation, investment trades.
- Real-time bank transaction streaming.
- Guaranteed-perfect OCR on low-quality scans without user review.

## Primary Personas

| Persona | Description |
|---------|-------------|
| **Solo budgeter** | Wants automated categorization and monthly summaries. |
| **Household manager** | Wants to reconcile, tag, and split shared purchases. |
| **Roommates/group** | Wants receipt splits and settlements with minimal friction. |
| **Power user** | Wants import/export, rules, and audit logs. |

## User Journeys

### Journey A: Bank Statement Import to Categorized Ledger

1. User imports PDF/CSV
2. App detects format, extracts transactions
3. App deduplicates and normalizes
4. App assigns categories with confidence scores and explanations
5. User reviews "low confidence" items, corrects categories
6. App learns and updates future predictions

### Journey B: Receipt to Item-Level Split with Tax Allocation

1. User uploads receipt image/PDF (or a "receipt-style list" exported from email)
2. OCR + parser extracts merchant, date, items, quantities, line prices, tax, tip, fees
3. App prompts to add/select participants
4. User assigns each item (or fractions) to participants
5. App computes per-person subtotal, proportional tax/tip/fees, and net owed to payer(s)
6. App generates settlement summary and export/shareable record

### Journey C: Budget Insights

1. App aggregates by month, category, merchant
2. Shows anomalies, recurring charges, subscription detection
3. User sets lightweight targets
4. App flags deviations and upcoming known bills

## Functional Scope

### Import Sources
- PDF bank statements (text-based and scanned)
- CSV exports (bank-provided variations)
- Optional later: OFX/QIF
- Receipt images/PDFs (for splitting and optional posting as transactions)

### Ledger Features
- Canonical transaction list with search/filter
- Category assignment with hierarchy
- Merchant normalization
- Rules and learning from corrections
- Attachments: link source statement pages and receipt images
- Export: CSV, JSON

### Splitting Features
- Participants list and groups
- Assign items to one or more participants
- Support fractional splits per item (percent or quantity)
- Allocate tax/tip/fees proportionally or custom
- Multiple payers per receipt and partial payments
- Settlement calculation: who owes whom and how much

### Budgeting Features (MVP)
- Monthly spend by category/merchant
- Cashflow view from imported statements
- Recurring transaction detection
- Alerts local-only (notification), based on rules

### Fully Automated Agent Features (Later Phases)
- Read-only bank sync via aggregator
- Bill reminders and "draft payments"
- "Suggested transfers" between checking/savings
- Write-access actions only after permission + security gates

## Success Metrics

| Metric | Description |
|--------|-------------|
| Parsing success rate | % of imports producing a usable ledger without manual re-entry |
| Categorization precision | At top-1 and top-3 |
| Correction reduction | Reduction in user corrections over time per merchant |
| Receipt item extraction accuracy | Items, totals, tax |
| Time-to-finish | For splitting a receipt |
| Reliability | Crash-free sessions, import latency, offline completion rate |

## Constraints

- MVP must run without cloud services.
- ML models must run on consumer hardware (desktop CPU; Android CPU/NPU where available).
- Budget for training: single GPU for periodic model updates, not continuous expensive training.
