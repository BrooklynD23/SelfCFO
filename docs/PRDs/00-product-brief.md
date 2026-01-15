# Product Brief - LedgerLens

## Product Name
**LedgerLens**

## One-Line Description
Local-first finance intake that parses messy bank statements and receipts, auto-categorizes transactions, learns from corrections, and supports item-level bill splitting.

## Platforms
- Android APK
- Desktop local app (Windows first, then macOS, then Linux)

## Core Problems

1. **Inconsistent Bank Exports** - Bank exports are inconsistent, hard to parse, and often PDF-only.

2. **Tedious Categorization** - Categorization is tedious and requires constant manual fixes.

3. **Painful Group Expenses** - Group expenses require item-level assignment and tax allocation, which current apps make painful.

4. **Automation Trust Gap** - Users want "hands-off" budgeting, but full automation requires expensive integrations and high trust.

## Strategy

Phase-gated capability ladder:

1. Local ingestion + parsing + categorization + learning loop
2. Local budgeting and insights + optional cloud sync
3. Read-only bank API aggregation
4. Assisted actions (draft bill pay/transfers)
5. Fully automated actions only after safety, permissions, and compliance gates

## Key Principles

1. **Local-first by default** - No account/PII required to get value.

2. **Deterministic fallbacks** - Rules + templates + human-in-the-loop, not "LLM hallucination."

3. **Auditability** - Every categorization and split has an explanation trail.

4. **Progressive trust** - Write-access automation is opt-in and gated.
