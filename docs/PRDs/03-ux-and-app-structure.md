# UX and App Structure - LedgerLens

## Navigation

| Screen | Purpose |
|--------|---------|
| Home dashboard | Overview and quick actions |
| Transactions | Full transaction list and management |
| Import | File import and processing |
| Receipts and splits | Receipt management and bill splitting |
| Rules and categories | Category and rule configuration |
| Settings and data control | App preferences and data management |

---

## Core Screens

### Import Screen

- **Drop zone** (desktop) or **file picker** (Android)
- Import type auto-detect and override
- Progress with per-stage visibility:
  - Extract → Parse → Normalize → Categorize → Dedupe

---

### Transactions Screen

**List with:**
- Merchant, date, amount, category badge, confidence indicator

**Features:**
- Bulk select tools
- **Inline correction:**
  - Change category
  - Create rule from correction

---

### Review Inbox

**Grouped by issue:**
- Parsing errors
- Low confidence
- Duplicates

**One-tap resolve patterns:**
- "Always categorize merchant as X"

---

### Receipts and Splits

- Receipt list
- **Receipt detail:**
  - Extracted items (editable)
  - Participant assignments
  - Computed totals and settlements
  - Export/share

---

### Rules and Categories

- Category tree editor
- Rules list with priority
- Rule builder with test preview on historical transactions

---

## Accessibility and Internationalization

- Currency/locale-aware number parsing and display
- Right-to-left layout readiness
- Screen reader labels for core actions
