# ADR-NNNN: [Decision Title]

<!--
Architecture Decision Record
Filename: docs/adr/NNNN-[kebab-title].md
Owner: Architect (/architect)
Handoff to: Builder (/builder), Security Auditor (/security-auditor)
Related Skills: designing-systems, designing-apis, domain-driven-design, cloud-native-patterns, writing-adrs

Format: Based on MADR (Markdown Any Decision Records) - https://adr.github.io/madr/
Best Practices:
- Write ADRs BEFORE committing to implementation
- One decision per ADR (not groups of decisions)
- Quantify consequences where possible (latency, cost, throughput)
- Keep Technical Details and Implementation Steps in plan.template.md — not here
-->

## Metadata

**Status:** Proposed \| Accepted \| Deprecated \| Superseded · **Date:** YYYY-MM-DD · **Deciders:** [names] · **Tags:** [tags]  
**Related PRD:** [Link or N/A] · **Supersedes:** [ADR-NNNN or N/A] · **Superseded By:** [ADR-NNNN or N/A]

**Tech Strategy:** ✅ Follows Golden Path OR ⚠️ Deviation — see Rationale

---

## Context

[What is the problem or situation that motivates this decision? Include technical constraints, business goals, and current gaps. Be specific — link to entities, services, or files where relevant.]

---

## Decision Drivers

- [Driver 1: e.g., must not block streaming response]
- [Driver 2: e.g., backward compatible with existing API]
- [Driver 3: e.g., configurable without redeploy]
- [Driver 4: e.g., team has limited Redis ops experience]

---

## Considered Options

### Option 1: [Name]

[Brief description]

| Pros | Cons |
|------|------|
| [Pro 1] | [Con 1] |
| [Pro 2] | [Con 2] |

### Option 2: [Name]

[Brief description]

| Pros | Cons |
|------|------|
| [Pro 1] | [Con 1] |
| [Pro 2] | [Con 2] |

### Option 3: [Name] *(if applicable)*

[Brief description]

| Pros | Cons |
|------|------|
| [Pro 1] | [Con 1] |

---

## Decision Outcome

**Chosen Option:** Option N — [Name]

**Rationale:** [Why this option over the others. Reference the Decision Drivers. Be explicit about trade-offs accepted.]

### Quantified Impact *(where applicable)*

| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Latency (p99) | [X] ms | [Y] ms | |
| Cost | $[X]/mo | $[Y]/mo | |
| Throughput | [X] req/s | [Y] req/s | |

---

## Consequences

**Positive:**
- [Consequence 1]
- [Consequence 2]

**Negative:**
- [Consequence 1]
- [Consequence 2]

**Risks:**
- [Risk 1 and mitigation]

---

## Validation

- [ ] Performance benchmarks meet requirements (if applicable)
- [ ] Security review completed (if applicable)
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: `docs/plans/plan-[topic].md`

---

## Links

- [Related PRD](../prd/PRD-[feature].md)
- [Implementation Plan](../plans/plan-[topic].md)
- [Related ADR](./NNNN-[topic].md)

---

## Changelog

| Date | Author | Change |
|------|--------|--------|
| YYYY-MM-DD | [Name] | Initial draft |
