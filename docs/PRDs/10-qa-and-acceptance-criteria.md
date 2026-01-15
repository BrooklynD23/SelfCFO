# QA and Acceptance Criteria - LedgerLens

## Import Acceptance

| Scenario | Criteria |
|----------|----------|
| Supported CSV | App imports with correct dates and amounts for ≥ 99% rows. |
| Text-based PDF statement | App extracts ≥ 98% transactions with correct signed amounts. |
| Scanned PDF with legible text | App extracts ≥ 90% transactions; remainder routed to review with page references. |

---

## Categorization Acceptance

| Scenario | Criteria |
|----------|----------|
| Existing merchant prior or rule | Category matches expected behavior 100%. |
| New merchants | Model assigns category with confidence; low confidence items appear in review inbox. |
| User correction | Immediately affects future transactions for the same merchant (merchant prior learning). |

---

## Receipt Split Acceptance

| Scenario | Criteria |
|----------|----------|
| Totals match | Computed participant totals sum to receipt total within rounding tolerance. |
| Tax allocation | Defaults to proportional by pre-tax allocations. |
| Multiple payers | Produce correct net settlement amounts. |

---

## Regression Suite

- Corpus of PDFs/CSVs/receipts with golden outputs.
- Snapshot tests for parsing and categorization.
- Fuzz tests for PDF parsing safety and resource bounds.

---

## Supportability

**"Export debug bundle" (local-only):**
- Parsing logs without raw statement text
- Anonymized structure traces
- Model version and config

**Requirements:**
- User-controlled
- Redaction-safe
