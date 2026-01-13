# Services Layer

This package contains **cross-cutting services** used across the application.

## Responsibilities

- **Import Services**: PDF/CSV parsing and normalization
- **Categorization Services**: ML-based transaction categorization
- **OCR Services**: Receipt text extraction
- **Export Services**: Report generation and data export

## Guidelines

- Services are stateless and injectable
- Heavy processing should support cancellation via coroutines
- Platform-specific implementations use `expect`/`actual`
- Services should be testable with mock data sources

## Package Structure (Planned)

```
services/
├── import/           # PDF and CSV import pipelines
├── categorization/   # ML categorization engine
├── ocr/              # Receipt OCR processing
├── export/           # Report and data export
└── sync/             # Future: sync services
```

See ADR-001 for architectural decisions.
