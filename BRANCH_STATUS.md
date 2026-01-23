# Branch Status Report

**Generated:** 2026-01-23  
**Repo:** `SelfCFO` / LedgerLens  
**Current branch:** `sprint04/integration`

This file summarizes **all local and origin-tracking branches** currently present in this repository, with recommended next actions (merge / keep / delete).

---

## Executive summary

- **Primary integration branch:** `sprint04/integration`
  - Ahead of `origin/main`: **30** commits (`origin/main...sprint04/integration` = `0 30`)
  - Ahead of `origin/sprint04/integration`: **9** commits (`origin/sprint04/integration...sprint04/integration` = `0 9`)
- **Mainline branch:** `main` (tracks `origin/main`)
  - Currently far behind `sprint04/integration` and does **not** include Sprint 04/05/06 work yet.

---

## Recommended next actions (high-signal)

- **Create/Update a PR:** Merge `sprint04/integration` → `main` (once CI passes and local Gradle verification is run).
- **Push pending commits:** `sprint04/integration` is ahead of `origin/sprint04/integration` by 9 commits (push pending).
- **After merge to `main`:**
  - Delete old Sprint 04 integration branches (`sprint04/*`) that are already incorporated.
  - Delete stale historical feature branches (`feat/*`) once confirmed redundant.
- **Missing/blocked verification:** Local test execution is blocked until a JDK is installed and `JAVA_HOME` is set.

---

## Local branches

### A) Merged into `sprint04/integration` (work already incorporated)

These branches are **fully contained** in `sprint04/integration` (via commit ancestry). After `sprint04/integration` is merged to `main`, these are generally **safe to delete** locally:

- `sprint04/navigation`
- `sprint04/design-system`
- `sprint04/import-review`
- `sprint04/ui-base`
- `integration/sprint02-sprint03`
- `feat/categorization-pipeline`
- `feat/category-explanation`
- `feat/csv-import`
- `feat/keymanager-impl`
- `feat/normalization`
- `feat/participant-management`
- `feat/receipt-ocr`
- `feat/rules-engine`
- `agent2-ml-categorization-preserved`

Notes:
- `main` is also listed by Git as “merged into” `sprint04/integration` because `sprint04/integration` was built from it.

### B) NOT merged into `sprint04/integration` (requires review)

These branches are **not** contained in `sprint04/integration`. They are all **very far behind** `sprint04/integration` but have a small number of unique commits (right-hand count).

Divergence format: `commits_only_in_sprint04/integration | commits_only_in_branch`

| Branch | Divergence vs `sprint04/integration` | Unique commits not in `sprint04/integration` | Recommended action |
|---|---:|---|---|
| `feat/category-hierarchy` | `44 | 2` | `a32221d`, `d92ea78` | Likely redundant; verify with `git diff sprint04/integration...feat/category-hierarchy` then delete |
| `feat/correction-learning` | `44 | 1` | `bd272c2` | Likely redundant; verify diff then delete |
| `feat/merchant-prior` | `44 | 1` | `6ccfe96` | Likely redundant; verify diff then delete |
| `feat/ml-categorization` | `44 | 1` | `27aa2e6` | Likely redundant; verify diff then delete |
| `feat/item-extraction` | `32 | 3` | `694b24c`, `c1d2277`, `96e0bb6` | Verify diff; cherry-pick only if truly missing |
| `feat/pdf-import` | `33 | 2` | `5f635ea`, `cd23f88` | Verify diff; cherry-pick only if truly missing |
| `feat/sprint01-tests` | `33 | 4` | `331f65d`, `8f3263e`, `5f635ea`, `cd23f88` | Likely superseded by later test suites; verify before delete |
| `sprint04/dashboard-transactions` | `20 | 2` | `adb30e5`, `a2c5f64` | Should be redundant (Sprint 04 integrated); verify diff then delete |
| `sprint04/receipts-settings` | `20 | 1` | `81b37e1` | Should be redundant (Sprint 04 integrated); verify diff then delete |
| `test/sprint01-integration` | `33 | 6` | `75c6c84`, `0e9f1bf`, `331f65d`, `8f3263e`, `5f635ea`, `cd23f88` | Historical integration branch; verify diff then delete |

### C) Merged into `main` (safe to delete locally)

These branches are reported as merged into `main` locally:

- `feat/category-explanation`
- `feat/csv-import`
- `feat/keymanager-impl`
- `feat/normalization`
- `feat/participant-management`
- `feat/rules-engine`

---

## Origin-tracking (remote) branches

### A) Key remote branches

- `origin/main`: current upstream mainline
- `origin/sprint04/integration`: exists but is **behind local** `sprint04/integration` (local ahead by 9 commits)
- `origin/sprint04/import-review`: historical branch (appears superseded by `sprint04/integration`)

### B) Dependabot branches (review PR status, then delete)

The following `origin/dependabot/*` branches exist and are **not merged** into `origin/main`:

- `origin/dependabot/github_actions/actions/checkout-6`
- `origin/dependabot/github_actions/actions/download-artifact-7`
- `origin/dependabot/github_actions/actions/setup-java-5`
- `origin/dependabot/github_actions/actions/upload-artifact-6`
- `origin/dependabot/github_actions/gradle/gradle-build-action-3`
- `origin/dependabot/gradle/androidx-lifecycle-2.10.0`
- `origin/dependabot/gradle/coroutines-1.10.2`
- `origin/dependabot/gradle/io.mockk-mockk-1.14.7`
- `origin/dependabot/gradle/org.jetbrains.compose-1.10.0`
- `origin/dependabot/gradle/sqldelight-2.2.1`

Recommended action:
- If PRs are merged/closed, these remote branches are typically **safe to delete**.

---

## Missing features / follow-ups

- **CI/verification gap:** local Gradle tests could not be executed without a JDK (`JAVA_HOME` missing). Run:
  - `./gradlew :shared:check`
- **Merge pending:** `sprint04/integration` needs to be merged into `main` for the new Sprint 04/05/06 work to land.
- **Desktop encryption:** SQLCipher on Desktop is currently deferred (no stable JDBC SQLCipher dependency in use).
- **Services layer:** `shared/src/commonMain/kotlin/com/ledgerlens/services/` is currently a placeholder (README-only); implementation still pending if needed.

