# Data Layer

This package contains the **data layer** of the clean architecture.

## Responsibilities

- **Repository Implementations**: Concrete implementations of domain repository interfaces
- **Data Sources**: Local database access, file system operations
- **Mappers**: Transform between data models and domain entities
- **DTOs**: Data transfer objects for storage and serialization

## Guidelines

- Implements interfaces defined in the `domain` layer
- Handles all persistence concerns (SQLDelight, file I/O)
- Uses platform-specific code via `expect`/`actual` when needed
- Encryption and security handled here (SQLCipher integration)

## Package Structure (Planned)

```
data/
├── repository/       # Repository implementations
├── datasource/       # Local and remote data sources
├── mapper/           # Entity <-> DTO mappers
└── dto/              # Data transfer objects
```

See ADR-001 for architectural decisions.
