# Codex/Copilot Instructions

This repository uses Steady Arc for workflow continuity.

## Enter the repository

Read in this order and stop when you have enough authority and context for the delegated task:

1. `.steadyarc/handoff.md` — current ownership, delegated authority, constraints, and return condition.
2. The current arc and active item in `.steadyarc/roadmap.md`.
3. Relevant durable facts in `.steadyarc/engineering-notes.md`.
4. `.steadyarc/deferred-issues.md` for unrelated queued discoveries.

Then inspect source, tests, and current repository state before changing behavior.

## Authority boundary

- Treat `Active — review` as inspection-only.
- Treat `Active — implementation` as bounded delegated scope.
- When ownership, constraints, or completion evidence changes materially, update `.steadyarc/handoff.md`.

## Documentation placement

- `handoff.md` — ownership transfer, scope, constraints, return evidence.
- `roadmap.md` — ordered active work and completion state.
- `engineering-notes.md` — durable architecture/workflow facts.
- `deferred-issues.md` — unrelated findings and later opportunities.

Keep routine progress in commits, tests, and code-local documentation rather than duplicating status across all Steady Arc files.
