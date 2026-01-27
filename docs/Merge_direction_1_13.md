# Sprint 01 Parallel Branch Merge Directions

> **Context:** On 2026-01-13, Sprint 01 was completed using 5 parallel Windsurf agents working on independent git worktrees. Each agent implemented a specific feature without dependencies on other agents' work. This document provides the structured merge process to consolidate all branches into `main`.

**Branches to Merge:**
| Branch | Feature | Status |
|--------|---------|--------|
| `feat/sprint01-tests` | Integration tests for encryption, money, data layer | ✅ Complete |
| `feat/csv-import` | CSV parsing with auto-detection | ✅ Complete |
| `feat/pdf-import` | PDF parsing with PDFBox/ML Kit | ✅ Complete |
| `feat/normalization` | Merchant normalization, fingerprinting, dedup | ✅ Complete |
| `feat/keymanager-impl` | KeyManager with key hierarchy | ✅ Complete |

**Agent:** Use this document sequentially. Do not skip steps. Create backup before starting.

---

## Pre-Merge Checklist

### 1. Environment Validation
```powershell
# Verify you're on main branch
git branch --show-current  # Expected: main

# Ensure main is up-to-date
git fetch origin
git status  # Should show "Your branch is up to date" or ahead

# Verify all worktrees exist
git worktree list
```

**Expected Output:**
```
C:/Users/DangT/Documents/GitHub/SelfCFO                 [main]
C:/Users/DangT/Documents/GitHub/SelfCFO-csv-import      [feat/csv-import]
C:/Users/DangT/Documents/GitHub/SelfCFO-pdf-import      [feat/pdf-import]
C:/Users/DangT/Documents/GitHub/SelfCFO-normalization   [feat/normalization]
C:/Users/DangT/Documents/GitHub/SelfCFO-keymanager      [feat/keymanager-impl]
C:/Users/DangT/Documents/GitHub/SelfCFO-tests           [feat/sprint01-tests]
```

### 2. Branch Completion Verification

| Branch | Verify Command | Expected Files |
|--------|---------------|----------------|
| `feat/csv-import` | `git log feat/csv-import -1 --oneline` | `CsvParser.kt`, `CsvAutoDetector.kt`, `ColumnMapper.kt`, `CsvParserImpl.kt` |
| `feat/pdf-import` | `git log feat/pdf-import -1 --oneline` | `PdfParser.kt`, `ParsedTransaction.kt`, `StatementTemplate.kt`, `PdfParserDesktop.kt` |
| `feat/normalization` | `git log feat/normalization -1 --oneline` | `MerchantNormalizer.kt`, `TransactionFingerprint.kt`, `DuplicateDetector.kt` |
| `feat/keymanager-impl` | `git log feat/keymanager-impl -1 --oneline` | `KeyManagerImpl.kt`, `MnemonicGenerator.kt`, `KeyWrapper.kt` |
| `feat/sprint01-tests` | `git log feat/sprint01-tests -1 --oneline` | `*Test.kt` files in `commonTest/`, `jvmTest/` |

---

## Merge Order (Critical)

**Merge in this exact order to minimize conflicts:**

```
1. feat/sprint01-tests      (Tests only - no impl conflicts)
2. feat/csv-import          (New package: com.ledgerlens.import)
3. feat/pdf-import          (Same package, different files)
4. feat/normalization       (Same package, different files)
5. feat/keymanager-impl     (Different package: com.ledgerlens.security)
```

**Rationale:**
- Tests first: They only add files, never modify existing code
- Import modules share `import/` package but create different files
- KeyManager is isolated in `security/` package

---

## Step-by-Step Merge Process

### Step 1: Create Backup Tag

```powershell
cd C:\Users\DangT\Documents\GitHub\SelfCFO
git tag backup/pre-merge-$(Get-Date -Format "yyyy-MM-dd-HHmm")
```

### Step 2: Merge Tests Branch

```powershell
git merge feat/sprint01-tests --no-ff -m "test(sprint01): Add comprehensive tests for encryption, money, and data layer"
```

**⚠️ IF CONFLICT:**
- Test files should NEVER conflict (they only add new files)
- If conflict in `build.gradle.kts`: Accept BOTH changes (test dependencies)
- Resolution: `git add <file> && git commit`

**✅ VALIDATION:**
```powershell
Test-Path shared/src/commonTest/kotlin/com/ledgerlens/security/FileEncryptionTest.kt
Test-Path shared/src/commonTest/kotlin/com/ledgerlens/domain/MoneyEdgeCasesTest.kt
```

### Step 3: Merge CSV Import

```powershell
git merge feat/csv-import --no-ff -m "feat(import): CSV import pipeline with auto-detection"
```

**⚠️ POTENTIAL CONFLICTS:**

| File | Conflict Type | Resolution |
|------|--------------|------------|
| `shared/build.gradle.kts` | Dependency additions | Keep BOTH sets of dependencies |
| `gradle/libs.versions.toml` | New library entries | Keep BOTH entries |

**Resolution Pattern:**
```kotlin
// Accept BOTH - dependencies are additive
implementation(libs.existing.dep)
implementation(libs.new.csv.dep)  // From feat/csv-import
```

**✅ VALIDATION:**
```powershell
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/CsvParser.kt
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/CsvAutoDetector.kt
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/ColumnMapper.kt
```

### Step 4: Merge PDF Import

```powershell
git merge feat/pdf-import --no-ff -m "feat(import): PDF import pipeline with text extraction"
```

**⚠️ LIKELY CONFLICTS:**

| File | Conflict Type | Resolution |
|------|--------------|------------|
| `ParsedTransaction.kt` | Both branches may define this | Use feat/pdf-import version (more complete) |
| `shared/build.gradle.kts` | PDFBox dependency | Add to desktopMain dependencies |
| `gradle/libs.versions.toml` | PDFBox version | Add new entry |

**Resolution Pattern for ParsedTransaction.kt:**
```kotlin
// If BOTH branches created this file, compare and keep the MORE COMPLETE version
// PDF branch should have: rowRef, postedDate, transactionDate, descriptionRaw, amount, balance, confidence
// CSV branch may have simpler version

// KEEP the version with MORE fields, ensure both parsers can use it
```

**✅ VALIDATION:**
```powershell
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/PdfParser.kt
Test-Path shared/src/desktopMain/kotlin/com/ledgerlens/import/PdfParserDesktop.kt
```

### Step 5: Merge Normalization

```powershell
git merge feat/normalization --no-ff -m "feat(import): Normalization and deduplication system"
```

**⚠️ POTENTIAL CONFLICTS:**

| File | Conflict Type | Resolution |
|------|--------------|------------|
| Any shared data class | Field additions | Merge fields from both |
| `Sha256.kt` | expect/actual | Ensure all platforms have implementations |

**✅ VALIDATION:**
```powershell
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/MerchantNormalizer.kt
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/TransactionFingerprint.kt
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/import/DuplicateDetector.kt
```

### Step 6: Merge KeyManager

```powershell
git merge feat/keymanager-impl --no-ff -m "feat(security): KeyManager implementation with key hierarchy"
```

**⚠️ POTENTIAL CONFLICTS:**

| File | Conflict Type | Resolution |
|------|--------------|------------|
| `KeyManager.kt` | Interface changes | DO NOT MODIFY interface, only add impl |
| `shared/build.gradle.kts` | Dependencies | Keep all |

**✅ VALIDATION:**
```powershell
Test-Path shared/src/commonMain/kotlin/com/ledgerlens/security/KeyManagerImpl.kt
Test-Path shared/src/jvmMain/kotlin/com/ledgerlens/security/KeyWrapper.kt
```

---

## Post-Merge Validation

### 1. Compile Check

```powershell
./gradlew :shared:compileKotlinDesktop :shared:compileDebugKotlinAndroid --no-daemon
```

**Expected:** BUILD SUCCESSFUL

**If fails:** Check error output for:
- Missing imports → Add import statements
- Duplicate class → One branch overwrote another, restore from backup
- Unresolved reference → Dependency not merged correctly

### 2. Test Suite

```powershell
./gradlew :shared:desktopTest --no-daemon
```

**Expected:** All tests pass

**If fails:**
```powershell
# Run specific failing test
./gradlew :shared:desktopTest --tests "com.ledgerlens.import.CsvParserTest" --info
```

### 3. File Inventory Check

```powershell
# Count files in import package
(Get-ChildItem -Path shared/src/commonMain/kotlin/com/ledgerlens/import -Filter *.kt).Count
# Expected: 10+ files

# Count test files
(Get-ChildItem -Path shared/src/commonTest/kotlin -Recurse -Filter *Test.kt).Count
# Expected: 8+ files
```

---

## Conflict Resolution Patterns

### Pattern A: Both Add to Same File (build.gradle.kts)

```kotlin
<<<<<<< HEAD
implementation(libs.existing.dep)
=======
implementation(libs.new.dep)
>>>>>>> feat/branch

// RESOLUTION: Keep BOTH
implementation(libs.existing.dep)
implementation(libs.new.dep)
```

### Pattern B: Same Class, Different Implementations

```kotlin
<<<<<<< HEAD
data class ParsedTransaction(
    val date: LocalDate,
    val amount: Money
)
=======
data class ParsedTransaction(
    val rowRef: String,
    val postedDate: LocalDate,
    val amount: Money,
    val confidence: Float
)
>>>>>>> feat/pdf-import

// RESOLUTION: Merge ALL fields
data class ParsedTransaction(
    val rowRef: String,           // From pdf-import
    val postedDate: LocalDate,    // From pdf-import (renamed from date)
    val transactionDate: LocalDate? = null,
    val amount: Money,            // Common
    val confidence: Float = 1.0f  // From pdf-import, with default
)
```

### Pattern C: Import Statement Conflicts

```kotlin
<<<<<<< HEAD
import com.ledgerlens.domain.Money
=======
import com.ledgerlens.domain.Money
import com.ledgerlens.import.ParsedTransaction
>>>>>>> feat/branch

// RESOLUTION: Keep ALL imports (deduplicated)
import com.ledgerlens.domain.Money
import com.ledgerlens.import.ParsedTransaction
```

---

## Rollback Procedures

### Rollback Single Merge

```powershell
# If last merge failed
git reset --hard HEAD~1
```

### Rollback to Pre-Merge State

```powershell
# Find backup tag
git tag -l "backup/pre-merge-*"

# Reset to backup
git reset --hard backup/pre-merge-2026-01-13-1200
```

### Abort In-Progress Merge

```powershell
git merge --abort
```

---

## Cleanup After Successful Merge

### Remove Worktrees

```powershell
git worktree remove ../SelfCFO-csv-import --force
git worktree remove ../SelfCFO-pdf-import --force
git worktree remove ../SelfCFO-normalization --force
git worktree remove ../SelfCFO-keymanager --force
git worktree remove ../SelfCFO-tests --force
```

### Delete Feature Branches

```powershell
git branch -d feat/csv-import
git branch -d feat/pdf-import
git branch -d feat/normalization
git branch -d feat/keymanager-impl
git branch -d feat/sprint01-tests
```

### Create Sprint Completion Tag

```powershell
git tag sprint01-complete -m "Sprint 01 Core Data Layer Complete"
```

### Update Documentation

```powershell
# Update IMPLEMENTATION_STATUS.md
# Mark Sprint 01 as complete
# Update last modified date
git add IMPLEMENTATION_STATUS.md docs/
git commit -m "docs: Mark Sprint 01 as complete"
```

---

## Emergency Recovery

| Issue | Action |
|-------|--------|
| Merge destroys work | `git reflog` to find commit, `git reset --hard <sha>` |
| Can't resolve conflict | `git checkout --theirs <file>` or `git checkout --ours <file>` |
| Build broken after merge | Check `git diff HEAD~1 shared/build.gradle.kts` |
| Tests fail after merge | Run individual test class to isolate |

---

## Final Checklist

- [x] All 5 branches merged
- [ ] `./gradlew :shared:compileKotlinDesktop` passes
- [ ] `./gradlew :shared:desktopTest` passes
- [ ] Worktrees removed
- [ ] Feature branches deleted
- [ ] `sprint01-complete` tag created
- [ ] `IMPLEMENTATION_STATUS.md` updated
- [ ] Pushed to remote: `git push origin main --tags`

---

*Created: 2026-01-13*
*For: LedgerLens Sprint 01 Parallel Branch Merge*
*Author: Cascade AI Agent*

---

## To-Do / Progress Log

**Last Updated:** 2026-01-13 12:10 PM

### Completed
- [x] Created backup tag: `backup/pre-merge-2026-01-13-1210`
- [x] Created test integration branch: `test/sprint01-integration`
- [x] Merged `feat/sprint01-tests` - SUCCESS (6,379 lines added, 38 files)
- [x] Merged `feat/csv-import` - Already up to date (was in sprint01-tests)
- [x] Merged `feat/pdf-import` - Already up to date (was in sprint01-tests)
- [x] Merged `feat/normalization` - Already up to date (was in sprint01-tests)
- [x] Merged `feat/keymanager-impl` - Already up to date (was in sprint01-tests)
- [x] File inventory validated: 13 import files, 10 test files

### Pending
- [ ] Build validation (JAVA_HOME not set in current session)
- [ ] Run test suite
- [ ] Fast-forward main to test/sprint01-integration
- [ ] Remove worktrees
- [ ] Delete feature branches
- [ ] Create `sprint01-complete` tag
- [ ] Update `IMPLEMENTATION_STATUS.md`
- [ ] Push to remote

### Notes
- All feature branches were already consolidated into `feat/sprint01-tests`
- Test integration branch `test/sprint01-integration` contains all merged code
- Main branch is safe and untouched - ready for fast-forward when validation passes
