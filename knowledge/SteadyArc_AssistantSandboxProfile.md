# Steady Arc Assistant Sandbox Profile

## Purpose

Document common constraints that sandboxed assistants (GitHub Copilot task agents, Codex sessions, and similar environments) encounter when applying Steady Arc to a project, together with the recommended fallback operation and standard reporting language for each constraint. Consistent reporting language lets humans compare evidence across sessions and projects.

This document does not replace the bootstrap procedure. It supplements it for constrained environments.

---

## Constraint catalogue

### 1. Single-repository write scope

**Constraint:** The assistant can inspect external repositories but can only edit and commit to the currently checked-out repository. Direct patches to the upstream `SteadyArcWorkflow` repository are not possible in the same session.

**Recommended fallback:**
- Record upstream workflow feedback (friction, defects, protocol improvements) in a local file, for example `docs/steadyarc-copilot-feedback.md`, or in `.steadyarc/deferred-issues.md` with a clear `upstream-transfer` tag.
- Mark the file as intended for later human-side transfer.
- Reference `knowledge/SteadyArc_AssistantSandboxProfile.md` in the feedback document so the scope of the constraint is clear to a reviewer.

**Standard reporting language:**
> "Direct upstream changes to `SteadyArcWorkflow` are not possible from this session. Findings have been recorded locally in `[path]` for human-side transfer."

---

### 2. Release archive not supplied

**Constraint:** The verified `steady-arc-knowledge-<version>.zip` release archive was not attached to the conversation. Managed-artifact installation cannot proceed.

**Recommended fallback:**
- Enter assessment mode (see `knowledge/SteadyArc_ProjectBootstrap.md`): assess compatibility, identify prerequisites, and produce a staged migration plan without placing any managed artifacts.
- Report status as "blocked on managed artifact delivery."
- Do not reconstruct managed scripts, Java sources, manifests, or binaries from prose.

**Standard reporting language:**
> "Bootstrap is blocked: the verified release archive (`steady-arc-knowledge-<version>.zip`) was not supplied. Assessment completed. Managed artifact placement is deferred until the archive is attached."

---

### 3. JDK version mismatch

**Constraint:** The sandbox JDK version does not match the project's `maven.compiler.release` target. `./mvnw test` fails at compilation; Maven Wrapper download and execution succeed.

**Recommended fallback:**
- Confirm that the wrapper downloads and runs successfully and record that result.
- Report runtime smoke as `not performed — JDK version gap` with the sandbox JDK version and the required version.
- Recommend adding a CI workflow that uses the correct JDK to close the gap automatically.
- Do not block the bootstrap arc on this gap; the build wiring is correct even if the sandbox cannot execute it.

**Standard reporting language:**
> "Build pipeline validation: Maven Wrapper downloaded and ran (PASS). `./mvnw test` failed — `release version N not supported` (sandbox JDK: X.Y.Z; project target: N). Runtime smoke: not performed — JDK version gap. Recommended resolution: CI with JDK N."

---

### 4. Restricted push path

**Constraint:** The assistant cannot `git push` directly. Changes must be pushed through the provided progress-reporting tool (for example, `engine-tools-report_progress`).

**Recommended fallback:**
- Use `engine-tools-report_progress` (or the equivalent tool in the active environment) for all commit and push operations.
- Never attempt `git push` directly; it will fail silently or with an error that wastes session budget.

**Standard reporting language:**
> "Changes committed and pushed via `engine-tools-report_progress`. Direct `git push` is not available in this environment."

---

### 5. No file attachments available

**Constraint:** The environment does not support file attachments in the current interaction mode, preventing delivery of the release ZIP or snapshot payload.

**Recommended fallback:**
- Clearly state what artifact is required and which bootstrap phase is blocked.
- Proceed with assessment mode for everything that does not require the artifact.
- Document the gap in the handoff's `Cross-repository constraints observed` field so the next session or human knows what to supply.

**Standard reporting language:**
> "File attachment is not available in this session. Bootstrap is in assessment mode. The following artifact is required to proceed: `[artifact name]`. Please attach it in the next session or supply the download URL."

---

## Upstream feedback packet format

When local capture is the correct fallback for upstream workflow feedback, structure the packet so it can be transferred to `SteadyArcWorkflow` without reinterpretation:

```markdown
## Observed constraint
[Constraint name from this catalogue or a new precise description]

## Evidence
[What was tried, what failed, exact error or gap]

## Suggested change
[Specific file and section in SteadyArcWorkflow, and proposed text or table row]

## Reusable vs. project-specific
[Confirm whether the finding applies to Steady Arc generally or is specific to this project]
```

Record one packet entry per distinct finding. A complete feedback document may contain multiple entries plus a summary status table.
