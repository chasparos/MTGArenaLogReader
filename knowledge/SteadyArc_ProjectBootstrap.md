# Steady Arc Project Bootstrap

## Bootstrap goal

Add Steady Arc support without taking ownership of the project's existing documentation, implementation workflow, or priorities.


## Assessment mode (release archive unavailable)

When the verified `steady-arc-knowledge-<version>.zip` is not yet attached, enter assessment mode rather than pausing with no defined behavior:

**Permitted in assessment mode:**
- Inspect the target repository structure, version-control state, build system, test framework, and existing agent instructions.
- Assess compatibility, identify prerequisites, and produce a staged migration plan.
- Document friction, prerequisite gaps, and environment constraints locally (see `knowledge/SteadyArc_AssistantSandboxProfile.md`).
- Report status as "blocked on managed artifact delivery" with a concrete description of what is missing.

**Prohibited in assessment mode:**
- No managed-artifact placement, even partial.
- No reconstruction of managed scripts, Java sources, manifests, or binaries from prose.
- No project-memory initialization that would be overwritten by a later compliant install.

When the archive arrives, transition to the full procedure below. Make the assessment findings available as the basis for the first dry-run compatibility review.

## Procedure

**Required release input:** Before implementing bootstrap for a repository that does not already contain Steady Arc, require the verified `steady-arc-knowledge-<version>.zip` release archive as a conversation attachment. When it is absent, request it from the human and pause implementation (enter assessment mode above). The workflow Knowledge files describe the contract but do not supply the released managed artifact bytes. Do not reconstruct managed scripts, Java sources, manifests, or binaries from prose.

1. Inspect repository structure, version-control state, build system, test framework, and existing agent instructions.
2. Treat existing project and agent documentation as authoritative within its own scope.
3. Look for `.steadyarc/` and the canonical support files.
4. Create only missing Steady Arc files. Never overwrite populated files without explicit human approval.
5. Prefer the release's repository-owned bootstrap/update executable. Run its dry-run operation first, review the compatibility and destination plan, then use install or update as appropriate.
6. When the executable cannot be run, follow its manifest-derived manual placement plan using the same packaged artifacts and destination rules. Do not reconstruct tools from prose.
7. Treat populated `.steadyarc/` files as project-owned state. Create only missing initial files; never replace roadmap, notes, deferred issues, or handoff state during a tool update.
8. Update managed tooling only when the destination is absent or still matches the previously installed managed version. Report local modifications and require an explicit per-file human decision before replacement.
9. Require rollback-safe operation: preserve every destination that may change before the first write and restore all touched files if installation or validation fails.
10. Copy or adapt the project-agnostic `PatchSequence.ps1` when a patch-based workflow is desired.
11. Determine whether the project can run through a committed wrapper or equivalent repository-local build launcher. Do not require a global Maven installation for routine repository work.
12. For Maven projects, verify all Maven Wrapper files are present and committed:
   - `mvnw`
   - `mvnw.cmd`
   - `.mvn/wrapper/maven-wrapper.properties`
13. When the wrapper is missing, stop and clearly instruct the human to generate it with a working local Maven installation, normally with `mvn wrapper:wrapper`, and commit the resulting wrapper files. Sandboxed assistants may not be able to perform this step.
   Also verify that `mvnw` is Unix-executable and uses Unix-compatible line endings unless the project records an explicit Unix-build exception in `.steadyarc/engineering-notes.md`. A Windows-only PowerShell workflow is not by itself such an exception.

**Maven Wrapper prerequisite decision table:**

| Condition | Required action |
|---|---|
| Wrapper script (`mvnw` / `mvnw.cmd`) missing | Stop. Instruct human to run `mvn wrapper:wrapper` and commit. |
| Wrapper properties (`.mvn/wrapper/maven-wrapper.properties`) missing | Stop. Human must regenerate wrapper or add the missing file and commit. |
| Both wrapper script and properties missing | Stop. Human must generate the full wrapper set and commit. |
| Wrapper present; JDK version mismatch in sandbox | Proceed with bootstrap. Report runtime smoke as `not performed — JDK version gap`. Recommend CI with correct JDK to close the gap. |
| Documentation-only or static-evidence stage | Proceed. Mark build/test and runtime smoke as `not performed` with a concrete follow-up command. |

Keep the three validation axes independent: managed-artifact placement status, build/test status, and runtime smoke status are each reported separately as passed, failed, or not performed.

**Maven Wrapper `.gitignore` ordering rule:** A top-level `/.mvn/` ignore rule makes any `!.mvn/wrapper/…` negation exception inert — git cannot re-include files inside an ignored directory. Use a graduated pattern instead:

```
/.mvn/*
!/.mvn/wrapper/
/.mvn/wrapper/*
!/.mvn/wrapper/maven-wrapper.jar
!/.mvn/wrapper/maven-wrapper.properties
```

Verify this pattern is in place when checking wrapper tracking. If a project's `.gitignore` uses `/.mvn/` before the exceptions, fix it to the graduated form as part of the bootstrap patch.
14. For a Maven/Java project, bootstrap the payload helper and `RunWidget.ps1` by default unless the human explicitly opts out or a documented repository constraint makes the helper incompatible. Do not omit it merely because the target has no pre-existing development-tools package.
15. Use the canonical payload artifacts shipped in the attached distribution under `bootstrap/payload-helper/`: `src/main/java/devtools/ChatGptPayloadButton.java` and `RunWidget.ps1`. Copy or adapt them into a project-compatible location; do not recreate the helper from prose.
16. If the target build cannot accommodate those artifacts without an unrelated architectural change, stop and report the incompatibility clearly. Do not silently omit either payload artifact; record the evidence and obtain an explicit human decision before proceeding without them.
17. Integrate the helper with the target's repository-local Maven build so `RunWidget.ps1` can compile and launch `devtools.ChatGptPayloadButton`. Keep any required `pom.xml` change narrowly scoped and explain it in the bootstrap patch.
18. Include the project-agnostic `RunWidget.ps1` launcher that resolves the repository root and starts the widget through repository-local build tooling. The launcher must validate or deduce `JAVA_HOME`, export it only to the wrapper process environment, and never assume a globally installed Maven.
19. Require a JDK, not merely a JRE: both `bin\java.exe` and `bin\javac.exe` must exist. Resolve Java in this order: valid existing `JAVA_HOME`, IntelliJ project SDK metadata, `java.exe` on `PATH`, then common JDK installation locations. If no usable JDK is found, stop with concrete current-session and persistent setup instructions.
20. Explain that `RunWidget.ps1` repairs Java only for its own process. Direct use of `mvnw.cmd`, `PatchSequence.ps1`, or release helpers still inherits the caller's `JAVA_HOME` and may require the human to configure the shell first.
21. Verify that any widget action which executes clipboard content requires an explicit click, reads the clipboard only at that moment, uses the repository root as its working directory, and never runs automatically. PatchSequence commands may use captured output; ordinary commands should remain visible in a separate terminal.
22. Keep execution-policy relaxation process-scoped. A widget-created terminal may use `-ExecutionPolicy Bypass`, but bootstrap tooling must not change CurrentUser or LocalMachine policy.
23. Create missing canonical `.steadyarc/` project-memory files without modifying agent-owned instructions. Install an agent-specific entry point or bridge only when the selected adapter requires it.
24. Infer the current arc only from repository evidence and explicit human intent. Ask concise questions when that is not possible.
25. Record bootstrap work as its own bounded roadmap item.
26. Report bootstrap validation as three independent statuses: patch applicability, project build/tests, and runtime smoke validation. Mark each passed, failed, or not performed; include the command or evidence and the reason for any gap. Never promote patch applicability or static inspection into project or runtime validation. When PowerShell cannot run, report parser checks, source-contract tests, packaging assertions, byte-preservation inspection, or historical evidence for an unchanged script hash only as static evidence. Keep runtime smoke validation `not performed` and provide the repository-owned human-side smoke command when acceptance requires it.
27. When the release includes `BootstrapSteadyArc.ps1`, run its dry-run operation against the target before manual placement. Use install or update only after reviewing its compatibility and drift classifications.
28. Treat `.steadyarc/managed-tools.properties` as updater state. Do not use it to replace populated project-memory files.
29. Deliver bootstrap changes as exactly one unified patch. Verify `git apply --check` when possible and provide the repository-supported patch command. When `PatchSequence.ps1` is present, use it rather than restating its internal Git and build steps.
30. Include a bootstrap feedback report covering repository evidence, files changed, existing workflows deliberately left untouched, the three independent validation statuses, expected test-count change, snapshot-manifest evidence when available, friction, ambiguity, interference risks, missing prerequisites, assumptions, and evidence-backed protocol improvements.
31. Distinguish reusable Steady Arc defects from constraints unique to the pilot repository. Do not hide pain points or perform unrelated product work during the bootstrap arc.

## Bootstrap verification suite

A bootstrap should include a small target-native verification suite when the repository already has a compatible test framework. The suite verifies the Steady Arc integration added by the bootstrap; it does not copy this self-hosting repository's tests or attempt to test the target product.

Cover the smallest applicable contracts:

1. **Continuity structure** — required `.steadyarc/` current-state files exist, are non-empty, and retain their distinct roadmap, handoff, engineering-note, and deferred-issue roles.
2. **Managed-tool provenance** — installed managed artifacts and `.steadyarc/managed-tools.properties` agree with the release manifest, while project-owned memory is excluded from managed replacement.
3. **Repository-local launch integration** — the supported wrapper or build launcher can compile the installed payload helper and `RunWidget.ps1` points to repository-local tooling rather than a global build installation.
4. **Execution safety** — when the payload widget or support relay is installed, clipboard execution remains explicit and the relay client exposes only its fixed operation catalogue with no free-form command or argument path.
5. **Packaging or transport** — when the target packages Steady Arc tools, the expected managed artifacts are included from their canonical installed sources rather than recreated copies.

Do not introduce a new test framework solely for Steady Arc verification. When no compatible project test framework exists, use a repository-owned, dependency-free verification script or report the relevant contract as `not performed` with a concrete follow-up. Tests should assert observable structure, integration, and safety boundaries rather than pinning incidental prose or the target project's future roadmap item.

The bootstrap delivery reports which contracts apply, the exact tests or verifier added, and the expected discovered-test-count change. A passing bootstrap suite supports project validation only; runtime smoke behavior remains a separate evidence axis.

## Bootstrap phases

Bootstrap has two review boundaries:

1. **Managed-tool installation** uses the manifest and updater to place compatible reusable artifacts and missing memory templates. This phase is mechanical, drift-aware, and rollback-safe.
2. **Project-memory initialization** replaces no populated state. It fills newly created templates with an evidence-based initial arc, roadmap, durable notes, and any delegated handoff through the reviewed bootstrap patch or another explicitly approved change.

Completing tool installation does not imply that project priorities were initialized correctly. Completing project-memory initialization does not authorize unrelated product implementation. Report and validate the phases separately when they do not occur in one approved patch.
## Initial file purposes

### `roadmap.md`

Contains the active arc, ordered items, completion criteria, and current item. It is not a general backlog.

### `deferred-issues.md`

Contains unrelated findings, future ideas, risks, and possible later arcs. Items here do not become active automatically.

### `engineering-notes.md`

Contains durable invariants, vocabulary, architecture facts, and workflow-specific repository observations.

### `handoff.md`

Records explicit responsibility transfers and return conditions. Preserve the original delegation while delegated work is active, amend it only for material scope or ownership changes, and complete a return report rather than erasing the handoff. Routine implementation detail belongs in commits, tests, code comments, or project documentation; durable decisions belong in `engineering-notes.md`, and unrelated discoveries belong in `deferred-issues.md`.

## Existing workflows

When an assistant already has a workflow:

- keep Steady Arc files separate;
- do not rewrite the other workflow;
- identify overlapping responsibilities;
- use the handoff protocol for deliberate transfers;
- allow explicit cross-role delegation without forcing the human to change agents.
