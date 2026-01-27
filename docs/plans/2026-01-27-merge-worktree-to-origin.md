# Merge Worktree to Origin (2026-01-27)

## Goal

Sync the current worktree branch (`sprint04/integration`) to the remote (`origin/sprint04/integration`).

## What was merged

- Local branch: `sprint04/integration`
- Remote branch: `origin/sprint04/integration`
- Local commits created during this sync:
  - `5bf6fd4` - `feat(receipts): enhance split sheet selection and tip`
  - `6d51ad5` - `docs: add merge-to-origin conflict plan`
  - `84da751` - `style: fix ktlint in fake review queue repo`
  - `928b7a5` - `fix: remove duplicate android AES-GCM implementation`

## Merge conflict report

No merge conflicts occurred during:

- `git fetch origin`
- Fast-forward push of local commits to `origin/sprint04/integration` (no remote divergence at time of merge)

If conflicts occur in a future sync, capture them here as:

- File path
- Conflict type (`content`, `add/add`, `modify/delete`, etc.)
- Chosen resolution (ours/theirs/manual) and rationale

## Conflict resolution plan (if a future sync is not fast-forward)

1. **Confirm divergence**
   - `git fetch origin`
   - `git rev-list --left-right --count origin/sprint04/integration...HEAD`
2. **Choose strategy**
   - Prefer **rebase** if the branch is personal/linear-history friendly.
   - Prefer **merge** if the branch is shared and rebasing would disrupt collaborators.
3. **Start integration**
   - Rebase: `git rebase origin/sprint04/integration`
   - Merge: `git merge origin/sprint04/integration`
4. **Resolve conflicts**
   - `git status` to list conflicted files
   - For each file:
     - Open conflict markers, pick correct changes, remove markers
     - `git add <file>`
   - Continue:
     - Rebase: `git rebase --continue`
     - Merge: `git commit` (after staging)
5. **Verify**
   - Run the narrowest checks first (e.g., `:shared:check`), then broader builds if needed.
6. **Push**
   - Rebased history: `git push --force-with-lease`
   - Merge commit: `git push`

