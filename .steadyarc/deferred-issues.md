# Steady Arc Deferred Issues

## Deferred items

### SA-MTGA-DEF-001 — Upstream feedback channel for Copilot sessions

- **Summary:** Establish an explicit, low-friction path in SteadyArcWorkflow for assistants that cannot write to multiple repositories in one session to emit "feedback from copilot" in a structured format.
- **Why deferred:** Stage 1 in this repository focuses on local structure initialization only.
- **Suggested upstream addition:** A dedicated template section under bootstrap feedback for "cross-repository tooling constraints observed in sandboxed agents."

### SA-MTGA-DEF-002 — Bootstrapping package integration details

- **Summary:** Introduce Steady Arc bootstrapping package artifacts (`RunWidget.ps1`, helper class/package, and any managed metadata) in a way that fits MTGArenaLogReader build and repository conventions.
- **Why deferred:** Planned for Stage 2 to keep Stage 1 documentation-only.
- **Prerequisite:** Confirm target paths and minimal `pom.xml` integration surface before adding tooling artifacts.
