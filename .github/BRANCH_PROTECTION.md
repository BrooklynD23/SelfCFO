# Branch Protection Rules

This document describes the recommended branch protection rules for the LedgerLens repository.

## Main Branch Protection

Configure in GitHub repository settings under **Settings > Branches > Add branch protection rule**:

### Branch name pattern

```
main
```

### Required Settings

| Setting | Value | Description |
|---------|-------|-------------|
| **Require pull request reviews before merging** | Enabled | All changes must be reviewed |
| Required approving reviews | 1 | At least one approval required |
| Dismiss stale approvals | Yes | Re-review required after new commits |
| **Require status checks to pass before merging** | Enabled | CI must pass |
| Require branches to be up to date | Yes | Branch must be current with main |
| **Required status checks** | See below | Specific jobs that must pass |

### Required Status Checks

The following CI jobs must pass before merging:

- `build (ubuntu-latest)` - Linux build and tests
- `build (macos-latest)` - macOS build and tests
- `build (windows-latest)` - Windows build and tests
- `android` - Android build and unit tests
- `lint` - ktlint and detekt code quality checks

### Additional Recommended Settings

| Setting | Value | Description |
|---------|-------|-------------|
| **Require linear history** | Enabled | Prevents merge commits, enforces rebase |
| **Do not allow bypassing the above settings** | Enabled | Applies to admins too |
| **Restrict who can push to matching branches** | Optional | Limit direct push access |

## Setup Instructions

1. Navigate to your GitHub repository
2. Go to **Settings** > **Branches**
3. Click **Add branch protection rule**
4. Enter `main` as the branch name pattern
5. Configure the settings as described above
6. Click **Create** or **Save changes**

## Required Secrets

Before the release workflow can function, configure these repository secrets in **Settings > Secrets and variables > Actions**:

| Secret | Description | How to Generate |
|--------|-------------|-----------------|
| `KEYSTORE_BASE64` | Base64-encoded Android keystore | `base64 -i keystore.jks` |
| `KEYSTORE_PASSWORD` | Password for the keystore | From keystore creation |
| `KEY_ALIAS` | Alias of the signing key | From keystore creation |
| `KEY_PASSWORD` | Password for the signing key | From keystore creation |

## Creating an Android Keystore

Generate a new keystore for signing release builds:

```bash
keytool -genkey -v -keystore ledgerlens-release.jks \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias ledgerlens-key
```

Then encode it for GitHub Secrets:

```bash
base64 -i ledgerlens-release.jks | pbcopy  # macOS
base64 ledgerlens-release.jks | xclip      # Linux
```

Store this output as the `KEYSTORE_BASE64` secret.
