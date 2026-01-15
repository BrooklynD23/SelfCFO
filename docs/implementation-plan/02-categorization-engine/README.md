# Sprint 02: Categorization Engine & Rules System

## Sprint Goal

Implement ML-based transaction categorization with merchant priors, user rules, and a learning loop that improves over time.

---

## Features Covered

| Feature | Description | Reference |
|---------|-------------|-----------|
| Category Hierarchy | Parent-child category tree | [02-detailed-functional-specification.md](../../PRDs/02-detailed-functional-specification.md) |
| ML Categorization | TFLite model inference | [04-ml-and-ocr-requirements.md](../../PRDs/04-ml-and-ocr-requirements.md) |
| Category Explanation | Structured explanation schema | [02a-data-model-addendum.md §5](../../PRDs/02a-data-model-addendum.md) |
| Merchant Priors | Merchant-to-category learning | [04-ml-and-ocr-requirements.md](../../PRDs/04-ml-and-ocr-requirements.md) |
| Rules Engine | User-defined categorization rules | [02-detailed-functional-specification.md](../../PRDs/02-detailed-functional-specification.md) |
| Correction Learning | Learning from user corrections | [04-ml-and-ocr-requirements.md](../../PRDs/04-ml-and-ocr-requirements.md) |
| Model Bundles | Signed downloadable updates | [ADR-005](../../PRDs/13-architecture-decision-records.md#adr-005-model-update-mechanism) |

---

## Implementation Plans

| Plan | File | Complexity |
|------|------|------------|
| Category Hierarchy | [01-category-hierarchy.md](./01-category-hierarchy.md) | Low |
| ML Categorization | [02-ml-categorization.md](./02-ml-categorization.md) | High |
| Category Explanation | [03-category-explanation.md](./03-category-explanation.md) | Medium |
| Merchant Prior System | [04-merchant-prior-system.md](./04-merchant-prior-system.md) | Medium |
| Rules Engine | [05-rules-engine.md](./05-rules-engine.md) | Medium |
| Correction Learning | [06-correction-learning.md](./06-correction-learning.md) | Medium |
| Categorization Pipeline | [07-categorization-pipeline.md](./07-categorization-pipeline.md) | Medium |

---

## Q/A and Testing Guidelines

### Testing Requirements

| Test Type | Requirement |
|-----------|-------------|
| Category Tests | Hierarchy traversal, default seeding |
| Model Tests | Inference accuracy on test set |
| Rule Tests | Pattern matching correctness |
| Pipeline Tests | Priority ordering (Rule > Prior > Model) |
| Learning Tests | Corrections update priors immediately |

### Quality Gates

- [ ] Default categories seeded correctly
- [ ] ML model loads and runs inference
- [ ] Rules match patterns correctly
- [ ] Categorization pipeline respects priority
- [ ] Corrections update merchant priors immediately
- [ ] Low confidence routes to review inbox

---

## Code Implementation Cycle

```
1. CATEGORIES  Implement hierarchy per 01-category-hierarchy.md
       ↓
2. MODEL       Integrate TFLite per 02-ml-categorization.md
       ↓
3. EXPLAIN     Add explanation schema per 03-category-explanation.md
       ↓
4. PRIORS      Build merchant prior system per 04-merchant-prior-system.md
       ↓
5. RULES       Implement rules engine per 05-rules-engine.md
       ↓
6. LEARNING    Add correction learning per 06-correction-learning.md
       ↓
7. PIPELINE    Wire up full pipeline per 07-categorization-pipeline.md
       ↓
8. TEST        Verify priority and accuracy
```

---

## Acceptance Criteria Checklist

### Category Hierarchy
- [ ] Default categories seeded
- [ ] Parent-child relationships work
- [ ] User can create custom categories
- [ ] Category CRUD operations

### ML Categorization
- [ ] TFLite model loads on both platforms
- [ ] Feature extraction (tokens, amount bucket)
- [ ] Inference returns top-3 categories with confidence
- [ ] Model bundle verification (signatures)

### Category Explanation
- [ ] Explanation JSON schema implemented
- [ ] Contributors tracked (rule, prior, model)
- [ ] Alternatives included
- [ ] UI can render explanations

### Merchant Prior System
- [ ] Merchant-to-category mapping
- [ ] Recency-weighted updates
- [ ] Lookups are fast (<10ms)

### Rules Engine
- [ ] Rule types: merchant_contains, merchant_equals, description_regex, amount_range
- [ ] Priority-based matching
- [ ] Rule testing on historical data
- [ ] Rule CRUD operations

### Correction Learning
- [ ] CorrectionEvent created on category change
- [ ] Merchant prior updated immediately
- [ ] Auto-rule suggestion generated

### Pipeline
- [ ] Priority: Rule > Merchant Prior > ML Model
- [ ] Low confidence (<0.7) routes to review
- [ ] Batch categorization for imports

---

## Dependencies

**From Sprint 01:**
- Database schema
- Transaction entities
- Merchant normalization

**External:**
- TensorFlow Lite
- Model bundle files

---

## Deliverables

1. Category hierarchy with defaults
2. TFLite integration with inference
3. Explanation schema implementation
4. Merchant prior learning system
5. Rules engine with pattern matching
6. Correction learning loop
7. Full categorization pipeline
8. Accuracy tests

---

*Estimated Complexity: High*
*Sprint Duration: After Sprint 01*
