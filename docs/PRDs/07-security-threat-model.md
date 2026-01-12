# Security Threat Model and Controls - LedgerLens

## Threats

| Threat | Description |
|--------|-------------|
| Local device compromise | Attacker gains access to device storage |
| Malicious PDF payloads | Exploits via crafted PDF files |
| Data exfiltration via logs/telemetry | Sensitive data leaking through logging |
| Model inversion risks | Training on sensitive text could leak info |
| Cloud account takeover | In sync phases, account compromise risk |

---

## Controls (MVP)

### PDF Security

- Sandbox PDF parsing
- Limit file sizes
- Disable script execution paths
- Deterministic parsers with bounded resource usage to resist zip bombs / huge PDFs

### Data Protection

- Validate and sanitize extracted text
- No raw statement contents in logs
- Encrypted local storage
- Optional app lock (PIN/biometric)

---

## Controls (Later Cloud)

### Encryption

- End-to-end encryption option for synced ledger

### Server Security

- Server-side rate limits and anomaly detection

### Privacy-Preserving Training

- Strict PII minimization for training:
  - Feature hashing
  - Token redaction
  - Merchant normalization locally before upload

### Audit

- Audit logs for all automated actions
