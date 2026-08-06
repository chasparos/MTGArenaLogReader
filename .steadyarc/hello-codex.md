# Hello there, Codex

This repository uses **Steady Arc** alongside any Codex-specific workflow.

Steady Arc normally maintains:

- the active Engineering Arc and ordered roadmap;
- engineering continuity and architecture context;
- investigation and validation strategy;
- regression planning;
- deferred issues;
- explicit handoff state.

Codex normally performs implementation, refactoring, code generation, and repository-local execution. These are defaults, not hard boundaries.

## First-look protocol

Before changing the repository:

1. Read `.steadyarc/roadmap.md`, `.steadyarc/engineering-notes.md`, `.steadyarc/deferred-issues.md`, and `.steadyarc/handoff.md`, plus any Codex-specific instructions.
2. Inspect the checked-out repository and current validation evidence rather than relying on conversation summaries.
3. Tell the human your understanding of the project, active arc, delegated task, constraints, and expected validation.
4. Ask only about material ambiguity that could change implementation direction; otherwise state assumptions and proceed only when implementation has been delegated.
5. Treat an initial "do you understand this project?" request as review and clarification, not permission to modify files.

## Continuing from the latest artifacts

When the human supplies `latest snapshot.zip`, `latest test results.log`, and `latest snapshot manifest.json`, treat them as one continuation payload. Verify the manifest hashes, inspect the snapshot's handoff and roadmap, reconcile the recorded commit, dirty state, and validation, then state the current owner and safe next action.

"Look this over; if it seems right, let us continue" authorizes inspection and continuation planning. Continue implementation only when the active handoff names you as receiver and covers the work, or when the human adds an explicit delegation.

## Documentation map

- `.steadyarc/handoff.md` — ownership transfer, delegated scope, blocking questions, return evidence, and next owner.
- `.steadyarc/roadmap.md` — ordered active work and completion state; not a work diary.
- `.steadyarc/engineering-notes.md` — durable decisions, invariants, terminology, and architecture facts.
- `.steadyarc/deferred-issues.md` — unrelated findings and possible later work.
- Commits, tests, code comments, and implementation documentation — normal work detail and rationale local to the changed code.

Do not duplicate the same narrative across all Steady Arc files. Put each fact in the narrowest durable location and link or summarize it from the handoff when needed.

## Upstream workflow feedback

When working in a project that is not `SteadyArcWorkflow` itself, you may discover friction, defects, or protocol improvements in the Steady Arc workflow. Sandboxed assistants typically cannot write directly to the upstream `SteadyArcWorkflow` repository in the same session. In that case:

- Record all findings locally in a dedicated feedback document (for example, `docs/steadyarc-copilot-feedback.md`) or in `.steadyarc/deferred-issues.md` with a clear `upstream-transfer` tag.
- Mark the file as intended for later human-side transfer to `SteadyArcWorkflow`.
- Do not omit findings or wait until you have upstream write access; local capture is the correct fallback.

The human will transfer validated findings to the upstream repository at a suitable handoff point.

## Cooperation contract

- Do not overwrite or merge Steady Arc files into Codex-owned workflow files.
- Do not silently reprioritize the active roadmap or promote deferred work.
- Do not assume ownership of a Steady Arc task merely because you can execute it.
- Explicit human delegation is a valid handoff and permits you to perform any requested work.
- The human does not need to switch agents to request adjustments outside default ownership.
- When delegated work affects ownership or continuity, read and update `.steadyarc/handoff.md`.
- Preserve a clean return path to the previous or default owner.
- When architectural ambiguity blocks safe implementation, record it in the handoff rather than inventing project direction.

The newest repository snapshot and current checked-out state are authoritative. Conversation summaries are supporting context only.
