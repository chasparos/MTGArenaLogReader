# Steady Arc Deferred Issues

## Closed items

### SA-MTGA-DEF-001 — Upstream feedback channel for Copilot sessions

- **Summary:** Establish an explicit, low-friction path in SteadyArcWorkflow for assistants that cannot write to multiple repositories in one session to emit "feedback from copilot" in a structured format.
- **Why deferred:** Stage 1 in this repository focuses on local structure initialization only.
- **Suggested upstream addition:** A dedicated template section under bootstrap feedback for "cross-repository tooling constraints observed in sandboxed agents."
- **Local evidence artifact:** `docs/steadyarc-copilot-feedback.md`.
- **Status:** Closed — `docs/steadyarc-copilot-feedback.md` has been populated with observed constraints, structured findings, and an evidence table across Stages 1–4. Ready for upstream transfer by the human repository owner.

### SA-MTGA-DEF-002 — Bootstrapping package integration details

- **Summary:** Introduce Steady Arc bootstrapping package artifacts (`RunWidget.ps1`, helper class/package, and any managed metadata) in a way that fits MTGArenaLogReader build and repository conventions.
- **Why deferred:** Planned for Stage 2 to keep Stage 1 documentation-only.
- **Prerequisite:** Confirm target paths and minimal `pom.xml` integration surface before adding tooling artifacts.
- **Status:** Closed — artifacts delivered in Stage 2 (`RunWidget.ps1`, `BootstrapInfo.java`, `maven-wrapper.properties`). `.gitignore` corrected in Stage 3. CI workflow added in Stage 4.
