# Steady Arc Roadmap

## Active Engineering Arc

Adopt Steady Arc workflow structure in MTGArenaLogReader without disrupting existing product architecture, tests, or contributor flow.

## Ordered items

- [x] Stage 1 — Initialize project-memory files and Codex/Copilot entry point.
- [x] Stage 2 — Add bootstrapping package artifacts (launcher/tooling scaffolding) in a repository-compatible layout.
- [ ] Stage 3 — Integrate and validate bootstrap workflow steps against repository-local build/test commands.
- [ ] Stage 4 — Refine based on friction found during real Copilot/Codex usage and upstream feedback.

## Current item

**Stage 3 — Integrate and validate bootstrap workflow steps against repository-local build/test commands**

### Completion criteria

- Validate `mvnw` / `mvnw.cmd` end-to-end using the restored `.mvn/wrapper/maven-wrapper.properties`.
- Run `./mvnw test` (or equivalent) and capture pass/fail evidence in a continuity note.
- Verify `RunWidget.ps1` launches the application without errors in a compatible environment.
- Document any remaining environment gaps (JDK version, wrapper download, etc.) in `docs/steadyarc-copilot-feedback.md`.
- Mark wrapper/build/test validation complete in engineering notes.

### Notes

- Stage 2 completed: `RunWidget.ps1`, `app.tools.steadyarc.BootstrapInfo`, and `.mvn/wrapper/maven-wrapper.properties` added.
- `BootstrapInfo` is tooling-only; it has no startup path dependency.
- Stage 3 focuses on live build/test feedback and closing the validation gap documented in Stage 1/2 feedback.
