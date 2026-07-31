# Steady Arc Roadmap

## Active Engineering Arc

Adopt Steady Arc workflow structure in MTGArenaLogReader without disrupting existing product architecture, tests, or contributor flow.

## Ordered items

- [x] Stage 1 — Initialize project-memory files and Codex/Copilot entry point.
- [x] Stage 2 — Add bootstrapping package artifacts (launcher/tooling scaffolding) in a repository-compatible layout.
- [x] Stage 3 — Integrate and validate bootstrap workflow steps against repository-local build/test commands.
- [ ] Stage 4 — Refine based on friction found during real Copilot/Codex usage and upstream feedback.

## Current item

**Stage 4 — Refine based on friction found during real Copilot/Codex usage and upstream feedback**

### Completion criteria

- Validate `./mvnw test` passes in a JDK 24-compatible environment (local developer machine or CI runner).
- Review `docs/steadyarc-copilot-feedback.md` and transfer relevant items to upstream `SteadyArcWorkflow` when write access is available.
- Decide whether to add a CI workflow (GitHub Actions) that runs `mvnw test` on push, making build health visible automatically.
- Close or retire SA-MTGA-DEF-001 and SA-MTGA-DEF-002 as their evidence has been captured.
- Complete the Return report in `handoff.md` and transition ownership back to the human repository owner.

### Notes

- Stage 3 completed: `mvnw --version` passed (Maven 3.9.9); `mvnw test` blocked only by sandbox JDK 17 vs required release 24.
- `.gitignore` ordering bug fixed; `maven-wrapper.properties` now correctly tracked.
- `mvnw` executable permission noted as not preserved in clones; may warrant `chmod +x` step in contributor setup notes.
