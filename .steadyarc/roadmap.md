# Steady Arc Roadmap

## Active Engineering Arc

Adopt Steady Arc workflow structure in MTGArenaLogReader without disrupting existing product architecture, tests, or contributor flow.

## Ordered items

- [ ] Stage 1 — Initialize project-memory files and Codex/Copilot entry point.
- [ ] Stage 2 — Add bootstrapping package artifacts (launcher/tooling scaffolding) in a repository-compatible layout.
- [ ] Stage 3 — Integrate and validate bootstrap workflow steps against repository-local build/test commands.
- [ ] Stage 4 — Refine based on friction found during real Copilot/Codex usage and upstream feedback.

## Current item

**Stage 1 — Initialize project-memory files and entry point**

### Completion criteria

- `.steadyarc/handoff.md`, `.steadyarc/roadmap.md`, `.steadyarc/engineering-notes.md`, and `.steadyarc/deferred-issues.md` exist and are non-empty.
- `AGENTS.md` exists and routes an assistant to current state before implementation.
- Existing repository product documentation remains intact and authoritative for runtime behavior.

### Notes

- This stage intentionally avoids runtime code changes.
- Follow-up stages will add the bootstrapping package and validation mechanics.
