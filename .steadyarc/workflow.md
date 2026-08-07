# Repository Workflow Reference

The normative workflow is `knowledge/SteadyArc_Workflow.md`.

This repository follows that specification against itself. Current state is stored in:

- `.steadyarc/roadmap.md`
- `.steadyarc/deferred-issues.md`
- `.steadyarc/engineering-notes.md`
- `.steadyarc/design-notes.md`
- `.steadyarc/user-preferences.md`
- `.steadyarc/handoff.md`

Codex-specific entry and bridge guidance remains in `AGENTS.md`, `knowledge/SteadyArc_CodexWorkflow.md`, and the optional `.steadyarc/hello-codex.md`; those adapter artifacts are not canonical project memory.

## 10. Automation scripts are first-class code

Repository automation deserves the same design discipline as production code.

- Separate discovery, validation, environment initialization, execution, and user interaction.
- Prefer small functions with explicit inputs, outputs, and failure contracts.
- Keep resolvers free of side effects; return structured results that identify both the value and its source.
- Apply environment changes in one explicit initialization step.
- Keep orchestration thin so each responsibility can be tested independently.
- Treat shell parsing, quoting, working-directory behavior, and environment inheritance as correctness concerns rather than incidental scripting details.
