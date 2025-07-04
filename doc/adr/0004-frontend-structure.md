# Use Vanilla JS + Bootstrap for Current Frontend with Planned Migration to React

## Status
Accepted

## Date
2025-07-03

## Context
The frontend is responsible for:
- Displaying video content, stats (views, likes)
- Allowing users to comment, like, and interact anonymously
- Providing responsive UI on mobile/desktop

We needed a lightweight setup for early-stage development, while keeping flexibility for future migration.

## Decision
We use:
- **Vanilla JavaScript** for DOM manipulation and logic
- **Bootstrap** for CSS and responsive design
- A basic modular structure (HTML + JS files per feature)

In the future, we plan to migrate to **React** for:
- Better component reuse and state management
- Richer interaction (e.g. comment threads, optimistic UI)
- Easier integration with design systems and CI tooling

## Consequences
- Current stack is lightweight, fast to prototype
- No build step or SPA framework needed now
- Later migration to React will require code rewrite
- No advanced state management (limited by plain JS)

## Related
- Task: Implement like/comment/view UI in Vanilla JS
- Planned: Migrate to React with component architecture (Q4 2025)
