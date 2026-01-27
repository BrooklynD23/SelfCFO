# Branch Cleanup Tasks (target: `sprint04/integration`)

These tasks help safely delete redundant branches whose work is already incorporated into `sprint04/integration`.

> Note: the branch name in this repo is `sprint04/integration` (not `sprint4-integration`).

---

## 0) Prep (do once)

- [ ] Ensure you have no uncommitted changes: `git status`
- [ ] Fetch + prune remote refs: `git fetch --all --prune`
- [ ] Confirm target exists locally and on origin:
  - `git show-ref --verify --quiet refs/heads/sprint04/integration`
  - `git show-ref --verify --quiet refs/remotes/origin/sprint04/integration`

---

## 1) Generate a fresh branch report (repeatable)

- [ ] Run the repo script (report-only by default):
  - `powershell -ExecutionPolicy Bypass -File scripts/branch-cleanup.ps1 -TargetBranch sprint04/integration -ReportPath BRANCH_CLEANUP_REPORT.md`
- [ ] Review `BRANCH_CLEANUP_REPORT.md` and confirm the "safe delete" lists match expectations.

---

## 2) Review *unmerged* local branches (manual decision)

For each branch listed below:

- [ ] Inspect unique commits: `git log --oneline sprint04/integration..BRANCH`
- [ ] Inspect diff vs target: `git diff --stat sprint04/integration...BRANCH`
- [ ] Decide one:
  - **Cherry-pick** missing commits, then delete the branch, or
  - **Delete** if confirmed redundant/outdated

Branches previously *not* merged into `sprint04/integration` (now merged locally):

- [x] `core/review-queue` (checked out in worktree `C:/worktrees/SelfCFO-core`; cannot delete until worktree switches branches or is removed)
- [x] `feat/category-hierarchy`
- [x] `feat/correction-learning`
- [x] `feat/item-extraction`
- [x] `feat/merchant-prior`
- [x] `feat/ml-categorization`
- [x] `feat/pdf-import`
- [x] `feat/sprint01-tests`
- [x] `sprint04/dashboard-transactions`
- [x] `sprint04/receipts-settings`
- [x] `test/sprint01-integration`

---

## 3) Delete merged local branches (safe list)

These branches are currently reported as merged into `sprint04/integration` and are generally safe to delete locally:

- [ ] `agent2-ml-categorization-preserved`
- [ ] `feat/categorization-pipeline`
- [ ] `feat/category-explanation`
- [ ] `feat/csv-import`
- [ ] `feat/keymanager-impl`
- [ ] `feat/normalization`
- [ ] `feat/participant-management`
- [ ] `feat/receipt-ocr`
- [ ] `feat/rules-engine`
- [ ] `integration/sprint02-sprint03`
- [ ] `sprint04/design-system`
- [ ] `sprint04/import-review`
- [ ] `sprint04/navigation`
- [ ] `sprint04/ui-base`

Commands:

- [ ] Delete locally (safe): `git branch -d BRANCH`
- [ ] If Git refuses due to "not fully merged" and you're *sure*: `git branch -D BRANCH`

---

## 4) Delete remote branches on `origin`

### A) Merged into `origin/sprint04/integration` (safe list)

- [ ] `agent2-ml-categorization-preserved`
- [ ] `sprint04/import-review`

### B) Not merged into `origin/sprint04/integration` (review PR status, then decide)

These branches are not contained in the current integration branch. Delete them only after confirming the associated PR is merged/closed (or you decide they're obsolete):

- [ ] `dependabot/github_actions/actions/checkout-6`
- [ ] `dependabot/github_actions/actions/download-artifact-7`
- [ ] `dependabot/github_actions/actions/setup-java-5`
- [ ] `dependabot/github_actions/actions/upload-artifact-6`
- [ ] `dependabot/github_actions/gradle/gradle-build-action-3`
- [ ] `dependabot/gradle/androidx-lifecycle-2.10.0`
- [ ] `dependabot/gradle/coroutines-1.10.2`
- [ ] `dependabot/gradle/io.mockk-mockk-1.14.7`
- [ ] `dependabot/gradle/org.jetbrains.compose-1.10.0`
- [ ] `dependabot/gradle/sqldelight-2.2.1`
- [ ] `feat/sprint01-tests`
- [ ] `test/sprint01-integration`

Commands:

- [ ] Delete on origin: `git push origin --delete BRANCH`
- [ ] Or run the script in apply mode (remote only): `powershell -ExecutionPolicy Bypass -File scripts/branch-cleanup.ps1 -Scope remote -TargetBranch sprint04/integration -Apply`

---

## 5) Post-cleanup

- [ ] Prune stale remote-tracking branches: `git fetch --prune`
- [ ] Verify branch list is clean: `git branch` and `git branch -r`
