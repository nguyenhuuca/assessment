# Use Relational Database (PostgreSQL) for Core Data

## Status
Accepted

## Date
2025-07-03

## Context
The system needs to store structured data including videos, comments, view stats, and user interactions. This data has clear relationships (e.g., one-to-many, parent-child), which makes relational modeling suitable.

## Decision
We use PostgreSQL as the relational database due to:
- ACID compliance
- Rich SQL support
- Native JSON support (for flexibility in some columns)
- Strong community and cloud-managed options

## Consequences
- Well-supported schema evolution via migration tools
- Easy to model relations like `video -> comments -> replies`
- Complex reporting and joins are efficient
- Requires designing migrations carefully as schema evolves

## Related
- Task: Design video_source and video_comments table
- Migration tool: Liquibase for schema management
