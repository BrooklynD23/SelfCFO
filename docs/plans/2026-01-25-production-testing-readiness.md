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
- **Target**: Implement real CSV/PDF import services and have Import UI reflect real outcomes:
  - transactions imported
  - duplicates found/skipped
  - parse errors recorded
  - “needs review” items backed by persisted entities (not hardcoded counts)

### 3) Review Inbox must have persisted data

- **Current state**: `shared/src/commonMain/kotlin/com/ledgerlens/categorization/pipeline/ReviewQueueManager.kt` is in-memory only; nothing enqueues import/OCR review items in production flow.
- **Target**: Add SQLDelight table + repository/service:
  - `shared/src/commonMain/sqldelight/com/ledgerlens/db/ReviewQueue.sq` (or equivalent)
  - enqueue from categorization/import/OCR pipelines
  - load/resolve items via ViewModel

---

## P1 — Strongly recommended for production-like testing

### 4) Desktop OCR must not be a stub

- **Current state**: `shared/src/desktopMain/kotlin/com/ledgerlens/ocr/ReceiptOcrDesktop.kt` is “Tesseract (Stub)” with TODOs.
- **Target**: Integrate OCR engine (e.g., Tesseract4J) and store:
  - `receipt.ocr_raw_text`
  - `receipt.ocr_confidence`
  - `receipt.parse_status` = `needs_review` when extraction is incomplete/low confidence

### 5) Encryption wiring must match documented intent

- **Current state**: DI provides `StubKeyManager` (`shared/src/commonMain/kotlin/com/ledgerlens/data/di/AppModule.kt`).
- **Target**: Replace with a real `KeyManager` implementation (or clearly mark encryption as “development mode only” and gate production testing expectations accordingly).

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

