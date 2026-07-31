# Steady Arc Roadmap

## Active Engineering Arc

Adopt Steady Arc workflow structure in MTGArenaLogReader without disrupting existing product architecture, tests, or contributor flow.

## Ordered items

- [x] Stage 1 — Initialize project-memory files and Codex/Copilot entry point.
- [x] Stage 2 — Add bootstrapping package artifacts (launcher/tooling scaffolding) in a repository-compatible layout.
- [x] Stage 3 — Integrate and validate bootstrap workflow steps against repository-local build/test commands.
- [x] Stage 4 — Refine based on friction found during real Copilot/Codex usage and upstream feedback.

## Arc complete

All four bootstrap stages delivered. The active engineering arc for Steady Arc adoption is complete.

**Summary of delivered work:**
- Stage 1: `.steadyarc/` continuity files, `AGENTS.md` entry point.
- Stage 2: `RunWidget.ps1`, `BootstrapInfo.java`, `.mvn/wrapper/maven-wrapper.properties`.
- Stage 3: `.gitignore` ordering fixed, `maven-wrapper.properties` correctly committed, `mvnw` permissions corrected, build evidence captured.
- Stage 4: `.github/workflows/ci.yml` (JDK 24 CI), deferred issues closed, feedback document completed, handoff returned.

**Next arc** (if initiated): upstream transfer of `docs/steadyarc-copilot-feedback.md` content to `SteadyArcWorkflow`, or feature/product work as directed by the human repository owner.
