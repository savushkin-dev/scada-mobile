---
name: backend-architecture-guardian
description: 'Strict backend architecture skill for Spring/Spring Boot. Use when: designing or implementing backend features, refactors, integrations, API changes, security, domain modeling, transactions, repositories, or architecture reviews. Auto-load for backend work.'
argument-hint: 'Опиши задачу backend и ключевой контекст'
user-invocable: true
disable-model-invocation: false
---

# Backend Architecture Guardian

## Purpose
- Provide a production-grade workflow for backend changes.
- Preserve architectural integrity and prevent degradation.

## When to Use
- Designing or implementing backend features, refactors, or integrations.
- Reviewing backend code for architecture compliance.
- Introducing or modifying domain logic, use cases, repositories, security, or API contracts.

## Mandatory Workflow
1. Analyze the existing architecture, layers, conventions, and patterns before proposing changes.
2. Assume the backend architecture is established; follow and strengthen it without redefining layer boundaries or conventions.
3. Identify the relevant domain model, aggregates, invariants, and existing use cases; extend them without breaking boundaries.
4. Define the use-case scenario and map it to application services and ports before touching infrastructure.
5. Place business rules inside the domain model and express invariants through domain behavior.
6. Organize application services around explicit use cases and align transaction boundaries with those use cases.
7. Design infrastructure adapters for persistence, messaging, and external integrations through ports and anti-corruption boundaries.
8. Define API contracts and DTOs at system boundaries only; validate compatibility and versioning needs.
9. Specify error handling and security behavior as part of the use-case design, not as afterthoughts.
10. Validate dependency direction and layer isolation; reject changes that violate boundaries.
11. Define tests: domain unit tests for rules and invariants, integration tests with PostgreSQL containers for critical scenarios.

## Layering Rules
- Domain depends on nothing outside the domain and never depends on Spring or infrastructure.
- Application depends on domain and uses ports for all external interactions.
- Infrastructure implements ports and depends on application and domain, never the reverse.
- Controllers are infrastructure adapters only and never contain business logic, calculations, or decisions.

## Use-Case Orientation
- Application services represent specific business scenarios, not CRUD operations.
- Universal or generic services without explicit business meaning are forbidden.

## Domain Model Requirements
- The domain model must be rich; invariants, constraints, and rules reside inside domain objects.
- Moving business logic into services when it can be expressed in domain behavior is forbidden.

## DTO Isolation
- DTOs exist only at system boundaries and are forbidden inside domain and application layers.
- Mixing DTOs with domain model is an architecture error and must be corrected.

## Repository Rules
- Repositories express the domain language and work with aggregates.
- Repositories must expose business-meaningful operations, not generic CRUD methods.

## API Contract Rules
- API is a stable contract and must not expose internal models.
- Any API change must be backward compatible or explicitly versioned.

## Error Handling and Transactions
- Errors are part of domain and application logic and must not be hidden.
- Hidden side effects and uncontrolled transactions are forbidden.
- Transaction boundaries must match use-case boundaries and be explicit.

## Security Requirements
- Use Spring Security with JWT architecture: access and refresh tokens with rotation.
- If a security model already exists, it must be followed and extended without breaking behavior.

## Naming and Responsibility Boundaries
- Names must reflect domain meaning and follow existing project conventions.
- Abstract or context-free names are forbidden.
- Component responsibilities must be explicit, with no hidden dependencies or implicit behavior.

## Required Architecture Patterns
- Clean architecture with strict layer isolation.
- Dependency injection for all external dependencies.
- Anti-corruption layer for external integrations.
- Reuse and extend existing patterns instead of duplicating them.

## Forbidden Anti-Patterns
- God classes.
- Anemic domain model without a proven necessity.
- Business logic in controllers.
- Layer mixing or reversed dependencies.
- Uncontrolled transactions.
- Hidden side effects.
- DTO usage outside system boundaries.
- Repositories used as generic CRUD abstractions.

## Continuous Architecture Stewardship
- Continuously analyze the existing domain model and architecture to discover implicit rules.
- Formalize and reinforce project-specific constraints without breaking established decisions.
- When requirements are ambiguous, choose solutions that maximize predictability, scalability, and maintainability, even if initial implementation is more complex.
- Any change that degrades the existing architecture is forbidden, even if it accelerates short-term delivery.
