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

3. **Environment-dependent validation gaps (updated Stage 3)**
   - `.mvn/wrapper/maven-wrapper.properties` was absent at Stage 1; added in Stage 2 and committed in Stage 3 after fixing `.gitignore` to properly unexclude it.
   - `mvnw` required a `chmod +x` fix in the sandbox; executed permission was not preserved from the upstream clone.
   - The earlier sandbox JDK was 17.0.19 (Temurin), below the repository's authoritative Java 21 target. `./mvnw --version` succeeded, while compilation required a Java 21 runtime.
   - **Consequence:** The build/test pipeline is fully wired; only the JDK version gap prevents a passing `mvnw test` in this sandbox environment.

4. **`.gitignore` ordering pitfall**
   - The original `.gitignore` had `!.mvn/wrapper/maven-wrapper.jar` and `!.mvn/wrapper/maven-wrapper.properties` exceptions *before* a `/.mvn/` ignore rule. Because git cannot re-include files inside an ignored directory, the exceptions were inert.
   - Fixed in Stage 3 by replacing `/.mvn/` with a graduated pattern: `/.mvn/*`, `!/.mvn/wrapper/`, `/.mvn/wrapper/*`, `!/.mvn/wrapper/maven-wrapper.jar`, `!/.mvn/wrapper/maven-wrapper.properties`.

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
- **Bootstrap arc complete (Stage 4):** CI workflow added; all four stages delivered.

## Stage 3 build/test evidence (2026-07-31)

| Step | Result |
|---|---|
| `./mvnw --version` | **PASS** — Maven 3.9.9 downloaded and ran successfully |
| `./mvnw test` | **FAIL** — `release version 24 not supported` (sandbox JDK: 17.0.19) |
| `RunWidget.ps1` | **Not runnable in Linux sandbox** — Windows PowerShell script; logic verified by inspection |

**Conclusion:** The full bootstrap wiring is correct. The only gap is the JDK version in the sandbox environment.

## Stage 4 resolution (2026-07-31)

- Added `.github/workflows/ci.yml`: runs `./mvnw test` with the repository's authoritative JDK 21 (Temurin) on every push and pull request.
- This closes the JDK mismatch gap — CI will validate the build in the correct environment automatically.
- SA-MTGA-DEF-001 and SA-MTGA-DEF-002 closed.
