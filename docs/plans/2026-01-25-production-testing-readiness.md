# Production Testing Readiness Checklist (2026-01-25)

## Goal

Make the app runnable end-to-end on **Desktop** and **Android** with production-like behavior for:

- Import → results → review
- Receipt OCR → needs-review workflow
- Persistence across app restarts

This is a **pragmatic integration checklist** (not unit-test completeness).

---

## P0 — Must-have blockers (cannot do meaningful production testing without these)

### 1) Android must mount the real app UI (currently shows placeholder)

- **Current state**: `android/src/main/kotlin/com/ledgerlens/android/MainActivity.kt` calls `App()` from `shared/src/commonMain/kotlin/com/ledgerlens/App.kt`, which just renders `Text("LedgerLens")`.
- **Target**: Android should initialize DI and render the real app shell (navigation + screen registry), similar to `desktop/Main.kt`.

### 2) Import must stop being simulated

- **Current state**: `shared/src/commonMain/kotlin/com/ledgerlens/ui/viewmodels/import/ImportViewModel.kt` uses `simulateImportProgress()` (“actual parsing not yet implemented”).
- **Update (2026-01-27)**: Core CSV import is implemented (`ImportService.importCsv`) with SHA-256 file-hash idempotency, immutable `imported_transaction` persistence, categorization, and enqueue to the persistent review queue.
- **Target**: Wire Import UI to call the core import service (CSV now; PDF later) and reflect real outcomes:
  - transactions imported
  - duplicates found/skipped
  - parse errors recorded
  - “needs review” items backed by persisted entities (not hardcoded counts)

### 3) Review Inbox must have persisted data

- **Current state**: `ReviewViewModel` reads from in-memory `ReviewQueueManager` (so the inbox will be empty after restart unless explicitly enqueued).
- **Update (2026-01-27)**: Persistence exists via `ReviewQueue.sq` + `SqlDelightReviewQueueRepository` and core import enqueues into `review_queue`.
- **Target**: Wire Review UI/ViewModel to load and resolve items via `ReviewQueueRepository` (and extend to OCR/source-file `parse_status = 'needs_review'` items).

---

## P1 — Strongly recommended for production-like testing

### 4) Desktop OCR must not be a stub

- **Current state**: `shared/src/desktopMain/kotlin/com/ledgerlens/ocr/ReceiptOcrDesktop.kt` is “Tesseract (Stub)” with TODOs.
- **Target**: Integrate OCR engine (e.g., Tesseract4J) and store:
  - `receipt.ocr_raw_text`
  - `receipt.ocr_confidence`
  - `receipt.parse_status` = `needs_review` when extraction is incomplete/low confidence

### 5) Encryption wiring must match documented intent

- **Update (2026-01-27)**: `KeyManagerImpl` exists and is bound in DI in the core worktree. Remaining work is validating platform integration end-to-end (Android/Desktop) and aligning UI entrypoints.

---

## P2 — Workflow polish / correctness checks

### 6) OCR “needs review” must be visible in UI

- **Current state**: schema supports `parse_status = 'needs_review'` (`Receipt.sq`, `SourceFile.sq`), but there’s no Review UI/query for those.
- **Target**:
  - query receipts/source files needing review
  - add a UI entry (either in Review inbox or Receipts screen) to inspect OCR output and confirm/correct parsed fields

### 7) End-to-end smoke test scripts

- Add a short “smoke test” doc/commands:
  - Desktop: import a known CSV, verify transactions created, verify Review has items
  - Android: same flow on emulator/device

---

## References

- `docs/audits/2026-01-25-REVIEW-INBOX-IMPORT-REVIEW-AUDIT.md`
- `IMPLEMENTATION_STATUS.md` (Known Issues / Notes)

