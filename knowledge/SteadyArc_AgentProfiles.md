# Steady Arc Agent Capability Profiles

## Purpose

Cooperating agents differ in git access, commit automation, sandboxing, and cross-session memory. This document defines agent-neutral **capability profiles** — categorized by what an agent can actually do in its environment, not by vendor or product name — and the operating guidance each profile needs to follow `knowledge/SteadyArc_Workflow.md` safely.

A concrete agent adapter (for example `knowledge/SteadyArc_CodexWorkflow.md` or `knowledge/CustomGPT_SystemPrompt.md`) should state which profile it matches and add only the detail specific to that tool surface. This document does not redefine Engineering Arcs, evidence standards, or ownership semantics; it only tells each profile how to satisfy them given its constraints.

## Profile: Local relay agent

An agent with a working checkout of the current branch, able to edit files directly, but denied direct execution of some commands (most commonly build/test suites) by its own sandbox. A human-operated relay runs the denied commands and returns their output. The agent does not commit or push automatically; the human controls when and how changes land.

Characteristics:

- direct file access to the working tree, current branch;
- no autonomous commit or push;
- sandbox-denied commands (tests, some tooling) run through a human-operated relay;
- the human is present and can follow along interactively.

Operating guidance:

- Because the human controls commit boundaries, expect fewer, larger commits than the patch-exchange or remote profiles produce; this is a property of the environment, not a workflow violation. Still keep `.steadyarc/roadmap.md`, `.steadyarc/handoff.md`, and `.steadyarc/engineering-notes.md` precisely current at each meaningful stopping point, since the human is relying on those files (not commit granularity) to follow progress.
- Never claim relay-executed output was produced by the agent's own sandbox; state that a command ran through the relay.
- Because commits are batched by the human, keep the roadmap and handoff updates synchronized with what has actually been applied to the working tree at the time of writing, not with what is merely staged or planned.
- Follow §5 (failure investigation) using the relay to reproduce and verify; do not substitute static evidence when the relay can provide runtime evidence.

## Profile: Patch-exchange agent

An agent with no direct repository access. It receives an uploaded continuation payload (snapshot archive, test log, and manifest per §3.1) and returns a unified patch plus the exact application command for the human to run.

Characteristics:

- no git access and no ability to execute anything in the target repository;
- work is scoped entirely to what the uploaded artifacts contain;
- the human applies the patch out-of-band, typically with `PatchSequence.ps1` or its bash counterpart.

Operating guidance:

- Always follow the artifact-first continuation procedure in §3.1 before proposing any change: verify manifest hashes, inspect `.steadyarc/handoff.md` and `.steadyarc/roadmap.md` from the snapshot, and reconcile the manifest's commit/branch/test state with the handoff before acting.
- Produce small, single-roadmap-item patches using Git-native binary-safe diff generation (`NewPatch.ps1` or its bash counterpart), consistent with "complete exactly one roadmap item per patch."
- Always deliver the patch-delivery contract in §6, including the exact application command, since the human cannot infer it from a running session.
- Patch applicability (`git apply --check`) is the only validation axis this profile can usually assert directly; project and runtime validation are the human's responsibility unless the human supplies their own evidence back.

## Profile: Remote background agent

An agent running on infrastructure it does not share with the human's own machine, with full git access to a clone and the ability to run tests remotely, but little or no persistent memory between sessions or tasks.

Characteristics:

- full git access: can commit, push, and run its own test suite;
- stringent, deliberate git usage since it cannot rely on informal working-tree state surviving between sessions;
- little to no cross-session context; each task effectively starts fresh.

Operating guidance:

- Because there is no persistent memory, read `.steadyarc/handoff.md`, `.steadyarc/roadmap.md`, `.steadyarc/engineering-notes.md`, and `.steadyarc/deferred-issues.md` in full at the start of every task rather than relying on a prior session's inferred understanding.
- Be conservative about inferring ownership or scope from conversational prose alone; prefer an explicit handoff amendment over an assumption, and stop for a blocking question rather than guessing when authority is ambiguous.
- Because context is limited, keep changes narrowly scoped to what the current handoff and active roadmap item actually authorize; avoid opportunistic unrelated changes that a lower-context session cannot evaluate for side effects.
- Since this profile runs tests remotely and commits directly, it is the profile best positioned to keep commit history and roadmap state tightly synchronized — use that advantage rather than batching unrelated work into one commit.

## Choosing and combining profiles

A single agent may not fit a profile exactly; use the closest match and note material differences in the concrete adapter document. Profiles are about operating guidance, not permission — the ownership, scope, and evidence rules in `knowledge/SteadyArc_Workflow.md` apply identically regardless of profile.
