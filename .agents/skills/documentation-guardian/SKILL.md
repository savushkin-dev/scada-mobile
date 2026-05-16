---
name: documentation-guardian
description: 'Documentation governance skill. Use when: writing, updating, or reviewing project documentation; enforcing single source of truth; maintaining doc structure, links, and style; handling documentation updates after code changes. Auto-load for documentation work.'
argument-hint: 'Опиши задачу по документации и затронутые файлы'
user-invocable: true
disable-model-invocation: false
---

# Documentation Guardian

## Purpose
- Govern documentation changes with a single source of truth.
- Preserve existing documentation structure, conventions, and style.
- Ensure documentation is accurate, scoped, and maintainable.

## When to Use
- Creating or updating documentation.
- Reviewing documentation for accuracy, structure, and consistency.
- Ensuring documentation changes follow established conventions.

## Mandatory Workflow
1. Analyze existing documentation structure, conventions, and style before any change.
2. If the user did not explicitly request documentation changes, do not edit documentation; ask for confirmation.
3. Apply the single source of truth rule: avoid duplication and prefer linking to the canonical document.
4. Identify the exact documentation type for the change: architecture, usage instructions, onboarding, or reference.
5. Update only the sections impacted by the change; do not rewrite documents without necessity.
6. Do not include code examples; if implementation details are required, link to the exact file and line range with a short textual summary.
7. Ensure every document contains a Markdown table of contents.
8. Maintain and validate cross-document links; update links whenever structure or filenames change.
9. If documentation conflicts with code, report the discrepancy and propose the correction before editing.
10. After edits, run documentation linters configured in Visual Studio Code and fix issues that are resolved with minimal effort.

## Single Source of Truth Rules
- Duplicate content across documents is forbidden.
- Canonical documents must be identified dynamically and linked instead of copied.
- Summaries are allowed only when they do not replicate full content.

## Structure and Separation Rules
- Architecture, usage instructions, onboarding, and reference data must be separated.
- Mixing different documentation types in a single document is forbidden.
- Each document must have a clear, stated purpose.

## Language and Style Rules
- Use technical, clear, and living language.
- Avoid filler text, abstraction, and repetition.
- Every paragraph must serve a concrete goal.

## Link and Reference Rules
- Maintain explicit links between related documents.
- Links must remain accurate after any change.
- References to implementation must be links to the exact file and line range with a brief textual description.

## Update Scope Rules
- If code changes require documentation updates, modify only the affected parts.
- Avoid broad rewrites unless the user explicitly requests a full refactor.

## Quality and Compliance Checks
- Verify document structure and table of contents are present and correct.
- Validate link integrity after any changes.
- Ensure compliance with project conventions and current documentation style.
