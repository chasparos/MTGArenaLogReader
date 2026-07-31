# Steady Arc Engineering Notes

## Durable repository facts

- MTGArenaLogReader is a Maven-based Java desktop project using Swing and JUnit 5.
- Existing architecture documentation in `README.md` and `docs/architecture/` captures product behavior and remains the source of truth for replay/game semantics.
- Current runtime entry point is `app.application.Application`.
- The repository currently has no pre-existing Steady Arc scaffolding; adoption is being introduced in staged bootstrap increments.

## Adoption boundaries

- Steady Arc project-memory files track ownership, planning, and durable workflow context; they do not replace product architecture docs.
- Bootstrap changes should stay rollback-safe and avoid changing replay/deck/draft/coaching behavior unless explicitly requested.
- For this repository, Maven Wrapper (`mvnw`, `mvnw.cmd`) is already present, which supports repository-local build/test workflows required by Steady Arc tooling conventions.
- `.mvn/wrapper/maven-wrapper.properties` was absent at Stage 1 and has been added in Stage 2 (targets Apache Maven 3.9.9).

## Stage 2 artifacts (added 2026-07-31)

- `RunWidget.ps1` — PowerShell launcher at repository root; calls `mvnw.cmd exec:java`. Windows-only; Unix users run `./mvnw exec:java` directly.
- `src/main/java/app/tools/steadyarc/BootstrapInfo.java` — compile-time project identity constants for bootstrap tooling. No startup path dependency; tooling surface only.
- `.mvn/wrapper/maven-wrapper.properties` — restores `mvnw` / `mvnw.cmd` to runnable state.

## Copilot fit notes (initial)

- The assistant environment can inspect external repositories through GitHub APIs, but local edit/commit scope is limited to the checked-out repository.
- Upstream SteadyArcWorkflow improvements therefore may need to be reported as suggestions when direct multi-repository edits are unavailable in-session.
- Local upstream suggestions are recorded in `docs/steadyarc-copilot-feedback.md` for later transfer into `SteadyArcWorkflow`.
