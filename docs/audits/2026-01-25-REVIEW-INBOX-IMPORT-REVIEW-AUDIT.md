# Review Inbox / Import Review Audit (2026-01-25)

## Summary

When an import indicates items “need review”, navigating to **Review** showed a **“Coming Soon”** placeholder instead of reviewable items.

This audit documents:

- Why the placeholder appeared
- Why no import/OCR review items were shown
- What was fixed and what remains for production testing

---

## Symptom

- User triggers navigation to the `Review` screen (e.g. from Dashboard/Import Result).
- UI shows placeholder: **`"<Screen Title> Screen (Coming Soon)"`**.

---

## Root cause: Review route not registered

The app shell (`LedgerLensAppWithNavHost`) renders:

- A registered composable from the screen registry, **or**
- `PlaceholderScreen(screen)` if the screen is not registered.

On Desktop, `Screen.Review` existed as a route (`Screen.kt`), but **was not registered** in `desktop/src/main/kotlin/com/ledgerlens/desktop/Main.kt`.

---

## Fix applied (Desktop)

`Screen.Review` is now registered in the desktop `screenRegistry` to render:

- `ReviewInboxScreen` when no item is selected
- `ReviewDetailScreen` when an item is selected

This removes the “Coming Soon” placeholder for Review on Desktop.

---

## Why you still won’t see import/OCR “items to review” (current repo behavior)

### 1) Review Inbox currently reads an in-memory queue

`ReviewViewModel` loads items from:

- `ReviewQueueManager.pendingItems`

Current implementation of `ReviewQueueManager`:

- Is **in-memory** (`mutableMapOf`)
- Has **no persistence** (no SQLDelight table)
- Requires **explicit enqueue** calls to populate

If nothing enqueues items, the Review Inbox will be empty.

### 2) Import flow does not produce review queue items

`ImportViewModel` currently simulates import progress and a result:

- No real CSV/PDF parsing
- No error capture / no OCR linkage
- No enqueueing into `ReviewQueueManager`

So “needs review” counts in `ImportResult` are not backed by real stored items.

### 3) OCR review is modeled in schema but not surfaced

Schema supports:

- `receipt.parse_status` including `needs_review`
- `receipt.ocr_raw_text` / `receipt.ocr_confidence`
- `source_file.parse_status` including `needs_review`

But there is currently **no UI/query** path that:

- selects receipts/files where `parse_status = 'needs_review'`, and
- presents them in a Review screen for OCR verification.

---

## Production testing gaps (related to this issue)

- **Android app entrypoint** currently renders a placeholder `App()` (`Text("LedgerLens")`) and does not mount the actual navigation/UI scaffold.
- **Review queue persistence** is not implemented (no `ReviewQueue.sq`); Review Inbox items are not durable across app restarts.
- **Import services** are not implemented; import is simulated in `ImportViewModel`.
- **Desktop OCR** is a stub (`ReceiptOcrDesktop` indicates Tesseract integration TODO).

---

## Recommended next steps (to support OCR/import review workflow)

1. **Persist review queue**:
   - Add SQLDelight table/queries (e.g. `ReviewQueue.sq`)
   - Implement a repository/service to load/enqueue/resolve review items

2. **Populate review queue from pipelines**:
   - Categorization: enqueue low-confidence/uncategorized/duplicate candidates
   - Import: enqueue parse errors and “manual review required” items
   - OCR: write `parse_status = 'needs_review'` when extraction is incomplete/low confidence, and surface these in Review UI

3. **Wire platform entrypoints**:
   - Android: mount the real app shell (`LedgerLensAppWithNavHost` + screen registry) and DI startup

