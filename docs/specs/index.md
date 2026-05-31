# Feature Specifications

Feature Specs define the exact behavior, API contract, and acceptance criteria for each feature.

## What is a Spec?

A Spec is the bridge between an ADR (architectural decision) and a Plan (task breakdown).
A developer can read a Spec and implement immediately — no ambiguity, no guesswork.

## Flow

```
PRD → ADR → Spec → Plan → Implementation
```

## How to create a Spec

```
/spec docs/prd/PRD-{slug}.md docs/adr/NNNN-{slug}.md
```

Claude reads the PRD and ADR, asks clarifying questions one at a time (business rules, error cases, edge cases, performance targets), then writes the Spec.

## Template

See: [Spec Template](../claude/templates/artifacts/spec.template.md)

## Specs

| Feature | File | Status |
|---------|------|--------|
| Watch History | [spec-watch-history.md](spec-watch-history.md) | 📋 Draft |
