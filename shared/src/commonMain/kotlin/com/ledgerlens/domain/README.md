# Domain Layer

This package contains the **domain layer** of the clean architecture.

## Responsibilities

- **Entities**: Core business objects (Transaction, Category, Receipt, etc.)
- **Use Cases**: Application-specific business rules and orchestration
- **Repository Interfaces**: Abstractions for data access (implemented in `data` layer)
- **Value Objects**: Immutable domain primitives (Money, AccountId, etc.)

## Guidelines

- No dependencies on external frameworks or libraries
- Pure Kotlin code that can run on any platform
- Business logic should be testable without mocking external dependencies
- Use cases should be single-responsibility and composable

## Package Structure (Planned)

```
domain/
├── entity/           # Core business entities
├── usecase/          # Application use cases
├── repository/       # Repository interfaces
└── valueobject/      # Value objects and domain primitives
```

See ADR-001 for architectural decisions.
