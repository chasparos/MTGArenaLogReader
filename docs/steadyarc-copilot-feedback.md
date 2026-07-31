# Steady Arc Feedback from Copilot Sessions

## Purpose

Capture practical friction points observed while applying Steady Arc in `MTGArenaLogReader` from a Copilot task-agent environment, plus suggested improvements for the `SteadyArcWorkflow` repository.

## Observed constraints

1. **Single-repository write scope in session**
   - The assistant can inspect external repositories but can only edit and commit in the currently checked-out repository.
   - Directly applying feedback patches to `SteadyArcWorkflow` from this session is not always possible.

2. **Managed bootstrap artifact availability**
   - Steady Arc bootstrap guidance requires a verified `steady-arc-knowledge-<version>.zip`.
   - In assistant-only sessions, operators may provide repository URLs but not the release archive attachment.

3. **Environment-dependent validation gaps**
   - This repository currently lacks `.mvn/wrapper/maven-wrapper.properties`, so `mvnw` is present but not runnable.
   - Local JDK availability may not satisfy `maven.compiler.release` targets (here: release 24), blocking test execution even for documentation/bootstrap-only iterations.

## Suggested additions to SteadyArcWorkflow

1. **Add an explicit "cross-repo limitation" feedback section**
   - In bootstrap and return templates, include a standard subsection for environments that cannot write to multiple repos in one session.
   - Include a copy/paste-ready "upstream feedback packet" format.

2. **Add a "bootstrap evidence mode" when release ZIP is unavailable**
   - Define an explicit non-install mode that permits:
     - repository compatibility assessment,
     - staged migration planning,
     - clear "blocked on managed artifact delivery" reporting.
   - Prevent ambiguity between planning and compliant installation.

3. **Strengthen wrapper/toolchain prerequisite checks in staged bootstrap guidance**
   - Add a short decision table for:
     - wrapper script exists but wrapper properties missing,
     - wrapper present but JDK release mismatch,
     - documentation-only stage where runtime validation is deferred.
   - Keep reporting split into:
     - managed artifact placement status,
     - project build/test status,
     - runtime smoke status.

4. **Add an optional "assistant sandbox profile" appendix**
   - Document common constraints for sandboxed assistants (no multi-repo edits, limited shell/JDK, restricted push method).
   - Provide recommended fallback operations and expected reporting language.

## Suggested modifications to existing Steady Arc docs

- `knowledge/SteadyArc_ProjectBootstrap.md`
  - Add explicit branch for "release archive not supplied yet" planning mode.
  - Add explicit branch for "wrapper skeleton present but incomplete".

- `templates/handoff.md`
  - Add optional field: **Cross-repository constraints observed**.

- `templates/hello-codex.md` (or equivalent agent entry template)
  - Add concise reminder that upstream workflow feedback may need to be recorded locally when direct upstream edits are not possible.

## Status in MTGArenaLogReader

- This file is intentionally local working feedback for later upstream transfer.
- No upstream SteadyArcWorkflow repository changes were made from this session.
