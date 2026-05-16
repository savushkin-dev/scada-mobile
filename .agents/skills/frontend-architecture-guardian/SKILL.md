---
name: frontend-architecture-guardian
description: 'Strict frontend architecture skill. Use when: designing or implementing frontend features, refactors, UI composition, state management, styling, API integration, performance, accessibility, or architecture reviews. Auto-load for frontend work.'
argument-hint: 'Describe the frontend task and key constraints'
user-invocable: true
disable-model-invocation: false
---

# Frontend Architecture Guardian

## Purpose
- Enforce a consistent, scalable, and maintainable frontend architecture.
- Prevent architectural degradation while evolving the system.

## Mandatory Workflow
1. Analyze the existing frontend codebase to infer architecture, conventions, and recurring patterns.
2. Build an internal model of current responsibilities and boundaries; align all decisions to it.
3. If current solutions are coherent and modern, follow and strengthen them.
4. If architectural contradictions or weaknesses exist, explicitly record the problems, define the target state, and propose an evolutionary path without breaking the system.
5. Never copy existing practices blindly when they reduce scalability, maintainability, or readability.
6. Introduce missing conventions based on modern best practices when none exist.
7. Reject any change that degrades architecture, even if it speeds short-term delivery.

## Architecture and Boundaries
- Define explicit layers and responsibility boundaries and keep them stable.
- Cross-layer shortcuts, hidden dependencies, and implicit coupling are forbidden.
- Data access, state orchestration, and presentation must be separated by clear boundaries.

## Components and Composition
- Components are compositional units with a precise responsibility.
- Universal or generic components without a concrete use case are forbidden.
- Composition must be preferred over inheritance and overlarge component scopes.
- Reuse is allowed only when there is real, demonstrated generalization.

## State Management
- State must live as close as possible to its point of use.
- Lifting state without a concrete coordination need is forbidden.
- Derived state must be computed, not stored.
- Side effects must be explicit and isolated; no side effects during render.

## Styling and Visual System
- Follow a single styling system; mixing competing approaches is forbidden.
- Global styles must be strictly controlled to avoid cross-component leaks.
- Design tokens and shared primitives must be consistent and centrally governed.

## API Integration and Data Contracts
- Treat API boundaries as stable contracts; internal UI models must not leak outside.
- All external data must be validated and mapped to domain-specific UI models.
- Error and loading states are mandatory for all remote data paths.

## Error Handling
- Errors must be surfaced deterministically in the UI with user-meaningful states.
- Silent failures and hidden error suppression are forbidden.
- Recovery paths must be explicit and consistent with the UI architecture.

## Performance
- Performance is a mandatory requirement, not an optimization phase.
- Avoid unnecessary re-renders and uncontrolled state fan-out.
- Lazy loading and code splitting must follow user flows and interaction paths.

## Accessibility
- Accessibility is mandatory; semantic structure, keyboard navigation, and focus management are required.
- Visual cues must not be the sole carrier of meaning.

## Naming and Conventions
- Names must reflect domain intent and follow existing conventions.
- Abstract or context-free names are forbidden.
- Consistency across components, state, and styling is mandatory.

## Team and Process Rules
- Maintain explicit ownership of responsibilities between components and layers.
- Duplication of logic across components is forbidden; shared logic must be extracted only when justified.
- Any deviation from established conventions must be justified and documented.
- UI tests are not mandatory and must not be required for frontend changes.

## Client Logging Rules
- Logging must be centralized and abstracted away from direct console usage.
- Using `console.log` outside the infrastructure layer is forbidden.
- Logs must have levels (debug, info, warn, error) and a single unified format.
- Logging must be configurable to avoid production noise.
- Errors must be logged with sufficient diagnostic context.

## Analytics Rules
- Analytics must be isolated from business logic and invoked through a dedicated layer.
- Event names must be meaningful, stable, and predictable.
- Duplicate events and chaotic data emission are forbidden.
- All user actions that influence product metrics must be tracked.
