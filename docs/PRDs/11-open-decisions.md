# Open Decisions - LedgerLens

**Status:** ✅ ALL CRITICAL DECISIONS RESOLVED
**Resolution Date:** 2026-01-12
**Resolution Document:** `13-architecture-decision-records.md`

---

## Decisions That Were Fixed (Now Resolved)

| Decision | Resolution | ADR Reference |
|----------|------------|---------------|
| ✅ Shared core strategy | **Kotlin Multiplatform** | ADR-001 |
| ✅ OCR library choices | **ML Kit (Android), Tesseract (Desktop)** | ADR-002 |
| ✅ Encryption library | **SQLCipher + Envelope Encryption** | ADR-003 |
| ✅ Sync approach | **Record-level with client-side merge** | ADR-004 |
| ✅ Model update mechanism | **Signed downloadable bundles** | ADR-005 |

### Additional Decisions Resolved (from Peer Review)

| Decision | Resolution | ADR Reference |
|----------|------------|---------------|
| ✅ Money type representation | **Integer minor units + scale** | ADR-006 |
| ✅ Encryption boundaries | **Per-feature opt-in model** | ADR-007 |
| ✅ Data provenance model | **Immutable imports + mutable overlays** | `02a-data-model-addendum.md` |

---

## Decisions Deliberately Deferred (No Change)

| Decision | Rationale | Target Phase |
|----------|-----------|--------------|
| Bank aggregator vendor selection | Depends on Phase 4 requirements | Phase 4 |
| Any write-access money movement features | Requires compliance and security review | Phase 5+ |
| LLM usage for "assistant" behavior | Until deterministic baselines are stable | Post-MVP |

---

## Implementation Can Now Proceed

With all critical decisions resolved, implementation agents have clear guidance for:

1. **Project structure** - KMP multiplatform setup
2. **OCR integration** - Platform-specific implementations
3. **Security layer** - Key hierarchy and encryption
4. **Sync architecture** - Record-level with conflict resolution
5. **Model delivery** - Signed bundle verification
6. **Data modeling** - Provenance-aware entity design
7. **Money handling** - Cross-platform consistent arithmetic

See `13-architecture-decision-records.md` and `02a-data-model-addendum.md` for complete specifications.
