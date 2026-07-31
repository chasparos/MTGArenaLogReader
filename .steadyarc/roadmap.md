# Steady Arc Roadmap

## Active Engineering Arc

Adopt Steady Arc workflow structure in MTGArenaLogReader without disrupting existing product architecture, tests, or contributor flow.

## Ordered items

- [x] Stage 1 — Initialize project-memory files and Codex/Copilot entry point.
- [ ] Stage 2 — Add bootstrapping package artifacts (launcher/tooling scaffolding) in a repository-compatible layout.
- [ ] Stage 3 — Integrate and validate bootstrap workflow steps against repository-local build/test commands.
- [ ] Stage 4 — Refine based on friction found during real Copilot/Codex usage and upstream feedback.

## Current item

**Stage 2 — Add bootstrapping package artifacts in repository-compatible layout**

### Completion criteria

- Add staged bootstrap artifacts with minimal disruption:
  - `RunWidget.ps1` (repository-local launcher),
  - payload helper Java package under a dedicated tooling namespace.
- Add or restore required Maven wrapper metadata so repository-local launcher prerequisites are explicit and actionable.
- Preserve runtime application behavior and keep bootstrap additions isolated to tooling/documentation paths.
- Capture and keep local upstream feedback notes in `docs/steadyarc-copilot-feedback.md`.

### Notes

- Stage 1 completed with `.steadyarc` initialization and `AGENTS.md`.
- Stage 2 focuses on introducing tooling artifacts and prerequisite hygiene before deeper workflow integration.
