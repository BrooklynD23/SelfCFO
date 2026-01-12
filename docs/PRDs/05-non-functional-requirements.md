# Non-Functional Requirements - LedgerLens

## Performance

| Platform | Requirement |
|----------|-------------|
| **Desktop import** | 10-page PDF processed within acceptable interactive time on CPU. |
| **Android** | Receipt OCR completes within a few seconds on mid-tier devices for a single image. |
| **Categorization** | Per transaction inference under 10 ms on desktop CPU; under 30 ms on Android. |

---

## Reliability

- **Import pipeline must be resumable after crash:**
  - Staged checkpoints persisted locally.

- **No data loss:**
  - All raw source files stored (encrypted) unless user deletes.

---

## Offline Operation

- All MVP features work without network.
- **Network used only for:**
  - Optional updates
  - Optional cloud sync in later phases

---

## Data Storage

- **Local database:** SQLite with migrations
- Full-text search for merchant/description
- Attachments stored in app-managed encrypted storage

---

## Security

- Encryption at rest for DB and attachments.
- Encryption in transit for any future sync.
- **Secrets management:**
  - Android Keystore
  - Desktop OS keychain where available

---

## Privacy

- **Default:** No account creation, no cloud upload.
- Explicit opt-in for any telemetry and for any data donation.
- **Data deletion:**
  - Delete ledger
  - Delete attachments
  - Delete learned mappings
