# Analytics and Telemetry Specification - LedgerLens

## Telemetry Posture

- **Off by default.**
- **Separate toggles:**
  - Diagnostics (crash reports)
  - Usage analytics (feature events)
  - Data donation (anonymized samples)

---

## Event Taxonomy

| Event | Properties |
|-------|------------|
| `import_started` | type, file_size, pages |
| `import_completed` | type, duration_ms, transactions_count, errors_count |
| `parse_error` | stage, error_code |
| `category_assigned` | source: model\|rule\|merchant_prior, confidence_bucket |
| `category_corrected` | from, to |
| `rule_created` | rule_type |
| `receipt_uploaded` | type |
| `receipt_extracted` | items_count, totals_match |
| `split_finalized` | participants_count, allocations_count |
| `export_used` | format |

---

## KPI Dashboards

| KPI | Description |
|-----|-------------|
| Import success rate by type | Track parsing reliability across formats |
| Average corrections per 100 transactions over time | Measure categorization improvement |
| Receipt extraction totals-match rate | OCR and parsing accuracy |
| Split completion time median | UX efficiency for bill splitting |
