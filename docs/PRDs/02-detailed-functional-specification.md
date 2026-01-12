# Detailed Functional Specification - LedgerLens

## 2.1 Canonical Data Model

### Entities

#### Transaction
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| source_type | enum | pdf_statement \| csv \| manual \| receipt_posted |
| source_file_id | FK | Reference to source file |
| source_row_ref | string | Page+line or csv row index |
| account_name | string | Account identifier |
| posted_date | date | Date transaction posted |
| transaction_date | date (nullable) | Original transaction date |
| description_raw | string | Raw description from source |
| merchant_normalized | string | Normalized merchant name |
| amount | decimal (signed) | Transaction amount |
| currency | string | ISO currency code |
| category_id | FK | Reference to category |
| category_confidence | float (0–1) | Confidence score |
| category_reason | text | Structured explanation |
| labels/tags | array | User-defined tags |
| is_transfer | bool | Transfer flag |
| counterparty | string (nullable) | Transfer counterparty |
| notes | string (nullable) | User notes |
| hash_fingerprint | string | Deduplication hash |

#### Category
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| name | string | Category name |
| parent_id | FK (nullable) | Parent category for hierarchy |
| system_default | bool | System-provided category |
| user_custom | bool | User-created category |

#### Rule
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| rule_type | enum | merchant_contains \| merchant_equals \| description_regex \| amount_range \| mcc \| account |
| match_expression | string | Pattern to match |
| target_category_id | FK | Category to assign |
| priority | int | Rule priority order |
| created_by_user | bool | User-created flag |
| enabled | bool | Active status |

#### Correction Event
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| transaction_id | FK | Reference to transaction |
| previous_category_id | FK | Original category |
| new_category_id | FK | Corrected category |
| timestamp | datetime | When correction occurred |
| features_snapshot | object | Merchant, tokens, amount bucket |

#### Receipt
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| merchant | string | Merchant name |
| date | date | Receipt date |
| subtotal | decimal | Pre-tax subtotal |
| tax_total | decimal | Tax amount |
| tip_total | decimal | Tip amount |
| fees_total | decimal | Additional fees |
| total | decimal | Grand total |
| currency | string | ISO currency code |
| attachment_file_id | FK | Source file reference |
| status | enum | extracted \| needs_review \| finalized |

#### Receipt Item
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| receipt_id | FK | Parent receipt |
| name_raw | string | Raw item name |
| name_normalized | string | Normalized name |
| quantity | decimal | Item quantity |
| unit_price | decimal | Price per unit |
| line_total | decimal | Total for line |
| category_hint | string (optional) | Suggested category |

#### Participant
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| display_name | string | Display name |
| contact_handle | string (optional) | Contact info |

#### Item Allocation
| Field | Type | Description |
|-------|------|-------------|
| id | UUID | Unique identifier |
| receipt_item_id | FK | Reference to item |
| participant_id | FK | Reference to participant |
| share_type | enum | percent \| quantity \| fixed_amount |
| share_value | decimal | Share value |
| computed_amount | decimal | Calculated amount |

#### Settlement
| Field | Type | Description |
|-------|------|-------------|
| receipt_id | FK | Reference to receipt |
| payer_id | FK | Who paid |
| payee_id | FK | Who owes |
| amount | decimal | Amount owed |

---

## 2.2 Import Pipeline Requirements

### PDF Ingestion

1. **Detect whether PDF is text-based:**
   - If text extraction yields stable lines and numeric columns, parse directly.
   - Else render pages to images and OCR.

2. Support multi-page statements.

3. Handle rotated pages, faint scans, skew.

### CSV Ingestion

1. Auto-detect delimiter, encoding, header row.

2. **Map columns using heuristics:**
   - Date columns, amount, description, balance (optional).

3. Handle separate debit/credit columns.

4. Preserve unknown columns as metadata for debugging.

### Normalization

1. Convert to canonical transaction format.

2. Standardize date formats, currency symbols, thousands separators.

3. **Normalize sign conventions:**
   - Debits negative, credits positive.

4. **Merchant normalization:**
   - Remove common noise tokens (city/state codes, terminal ids).
   - Maintain mapping table from raw description to normalized merchant.

### Deduplication

1. **Fingerprint strategy:**
   - `hash(normalized merchant + posted_date ± window + absolute amount + last4 account optional)`

2. **UI for duplicates:**
   - Show likely duplicates and allow merge/ignore.

### Review Workflow

1. **"Needs review" inbox:**
   - Low confidence category
   - Parse anomalies (totals mismatch, missing dates)
   - Duplicate candidates

2. **Bulk actions:**
   - Assign category to multiple transactions
   - Create rule from selection

---

## 2.3 Categorization Requirements

### Output Requirements

- `category_id`
- Confidence score
- **Explanation components:**
  - Rule matched
  - Merchant prior
  - Text token match
  - Amount pattern
  - User-history similarity

### Learning Loop Requirements

**When user changes a category:**
1. Create correction event
2. Update merchant-category preference table immediately (fast local learning)
3. Enqueue example for periodic model update (local or later cloud)

### Cold Start

- System model provides initial guesses.
- Merchant prior table starts empty; fills from usage.
- Default category set provided and editable.

### Edge Cases

1. **Transfers between accounts:**
   - Identify by keywords and matched amounts/dates
   - Allow user confirmation and rule creation

2. **Refunds/chargebacks:**
   - Link to original when possible by merchant+amount proximity

3. **Pending vs posted:**
   - Store posted as authoritative; pending optional later

---

## 2.4 Receipt Parsing and Split Requirements

### Input Types

- Receipt image (camera capture)
- Receipt PDF
- Screenshot/email PDF
- "Receipt-style list" text import (copy/paste)

### Extraction

**Identify:**
- Merchant
- Date/time
- Line items with amounts
- Subtotal, tax, tip, fees, total

**Validate totals:**
- If sum(items) ≠ subtotal within tolerance, flag `needs_review`.

### Item Assignment UX Requirements

1. **Participants:**
   - Create/select participants per group

2. **Assigning:**
   - Tap item → assign to one or more participants
   - **Quick modes:**
     - "All shared evenly"
     - "Split selected evenly"
     - "Repeat last allocations"

3. **Fractional assignment:**
   - Percent, quantity, fixed amount

### Tax and Fee Allocation Rules

- **Default:** Allocate tax proportionally to each person's pre-tax allocated subtotal.
- **Tip:** Allocate proportionally to allocated subtotal or custom.
- **Fees:** Allocate evenly or proportionally; configurable per receipt.

### Settlement Computation

**Inputs:**
- Payer(s) and how much each paid

**Output:**
- Minimal set of transfers (optional optimization) or direct owes-to payer

Store computed settlement and allow export/share.

### Posting to Ledger

**Optional:**
- Create one transaction per participant (personal ledger)
- Or store receipt split separately and create a single "group expense" transaction with allocations
