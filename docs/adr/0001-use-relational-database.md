# ADR-0001: Use Relational Database (PostgreSQL) for Core Data

## Metadata

| Field | Value |
|-------|-------|
| **Status** | Accepted |
| **Date** | 2025-07-03 |
| **Deciders** | nguyenhuuca |
| **Related PRD** | N/A |
| **Domain Tags** | data, infrastructure |
| **Supersedes** | N/A |
| **Superseded By** | N/A |

**Tech Strategy Alignment:**
- [x] Decision follows Golden Path in `.claude/rules/tech-strategy.md`

---

## Context
The system needs to persist structured data including videos, comments, view statistics, and user interactions. This data has clear relationships (one-to-many, parent-child hierarchies) and requires consistent, transactional writes. A storage solution that enforces relational integrity and supports safe schema evolution over time is needed.

---

## Decision Drivers
- Data has well-defined relationships between entities (videos, comments, users, stats)
- Transactional consistency is required for writes
- Schema must evolve safely over time without data loss
- Reporting and filtering queries benefit from rich SQL support
- Cloud-managed hosting options are preferred to reduce operational overhead

---

## Considered Options

### Option 1: PostgreSQL
A mature, open-source relational database with full ACID compliance, rich SQL, and native JSON support.

| Pros | Cons |
|------|------|
| ACID compliance ensures data integrity | Requires upfront schema design |
| Rich SQL for complex queries and joins | Schema migrations must be managed carefully |
| Native JSON columns for flexible fields | Vertical scaling limits (mitigated by cloud options) |
| Strong community and cloud-managed options available | |
| Compatible with Liquibase for migration management | |

### Option 2: MongoDB
A document-oriented NoSQL database suited to schema-flexible, unstructured data.

| Pros | Cons |
|------|------|
| Schema-flexible, easy to start | Weak relational integrity across documents |
| Horizontal scaling built-in | Joins are complex and less performant |
| Good for unstructured or variable data | Transactions less mature than PostgreSQL |
| | Overkill for well-structured relational data |

### Option 3: SQLite
An embedded, file-based relational database requiring no server process.

| Pros | Cons |
|------|------|
| Zero operational overhead | Not suitable for multi-user concurrent writes |
| Simple local development setup | Cannot run as a separate networked service |
| | Poor fit for production cloud deployments |
| | Limited concurrency and scalability |

---

## Decision Outcome
**Chosen Option:** Option 1 — PostgreSQL
**Rationale:** The data model is inherently relational and benefits from enforced foreign keys, transactional consistency, and rich SQL. PostgreSQL provides all of these along with native JSON support for flexibility, and integrates cleanly with Spring Boot via JPA and Liquibase. MongoDB adds complexity without benefit for a well-structured domain. SQLite is unsuitable for multi-user production workloads.

### Quantified Impact *(where applicable)*
| Metric | Before | After | Notes |
|--------|--------|-------|-------|
| Schema evolution safety | Manual/ad hoc | Managed via Liquibase | Automated, versioned migrations |
| Query complexity support | N/A | Full SQL joins and aggregations | Enables reporting and filtering |

---

## Consequences
**Positive:**
- Schema evolution is managed safely through Liquibase versioned migrations
- Relational data (video to comments to replies) is modeled naturally with foreign keys
- Complex reporting queries and aggregations are efficient with SQL
- ACID guarantees prevent partial writes and data corruption

**Negative:**
- Schema must be designed upfront; structural changes require migration files
- Horizontal scaling requires additional configuration such as read replicas and connection pooling

**Risks:**
- Poorly written migrations could cause downtime during schema changes on production
- Unbounded JOIN complexity could degrade query performance at scale without indexing discipline

---

## Validation
- [ ] Tech Strategy alignment confirmed
- [ ] Related plan document created: N/A

---

## Links
- [Liquibase migration docs](https://docs.liquibase.com/)
- [PostgreSQL official docs](https://www.postgresql.org/docs/)

---

## Changelog
| Date | Author | Change |
|------|--------|--------|
| 2025-07-03 | nguyenhuuca | Initial draft |
| 2026-05-31 | nguyenhuuca | Restructured to new ADR template |
