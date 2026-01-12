# ML and OCR Requirements - LedgerLens

## 4.1 OCR Stack

### Android

- **Primary:** On-device OCR using a proven mobile text recognition SDK.
- **Fallback:** Image pre-processing (deskew, contrast) then OCR retry.

### Desktop

- **Primary:** Text extraction from PDFs if embedded text exists.
- **Fallback:** OCR via a local OCR engine on rendered page images.

### OCR Quality Gates

- Confidence thresholds per line and per page.
- **If below threshold:**
  - Route to `needs_review` with highlighted uncertain regions.

---

## 4.2 Statement Parsing Models

### Approach

**Hybrid, not purely generative:**

1. **Layout detection:**
   - Detect table-like regions and column boundaries

2. **Field extraction:**
   - Date, description, amount, balance (optional)

3. **Heuristic templates + learned classifier:**
   - Template inference per statement type
   - Learned model chooses parsing strategy

### Training Data

- Synthetic PDFs produced from known templates.
- User-consented anonymized samples for future improvements (opt-in only).
- **Labeling:**
  - Canonical rows extracted + human-verified fields.

### Evaluation

**Field-level F1:**
- Date accuracy
- Amount accuracy
- Description completeness

**Transaction row recall:**
- Missing/extra transactions

---

## 4.3 Transaction Categorization Model

### Baseline

- **Model:** Lightweight text+metadata classifier (TFLite compatible)
- **Features:**
  - Merchant tokens
  - Description tokens
  - Amount bucket
  - Day-of-week, month
  - Account type (optional)

### Personalization

**Two-tier learning:**

1. **Immediate local learning:**
   - Merchant → category mapping with recency weighting
   - Rules override model

2. **Periodic training:**
   - Batch retrain global model (single GPU) using aggregated non-sensitive features or consented anonymized data
   - Ship model updates with app releases or model bundle downloads (later cloud)

### Confidence Calibration

- Temperature scaling or isotonic regression offline.
- **Target behavior:**
  - Low confidence → routed to review inbox.

---

## 4.4 Receipt Itemization Model

- Primarily deterministic parsing + regex + line grouping.
- Optional later: small sequence labeling model for item/price pairing.
- Totals reconciliation as validator.
