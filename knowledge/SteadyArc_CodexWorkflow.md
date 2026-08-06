# Steady Arc Codex Workflow

## Purpose

This document defines how Codex should bootstrap or update Steady Arc when the human supplies a released Steady Arc knowledge ZIP. It complements the general workflow and bootstrap specification; it does not grant implementation authority beyond the human's request or an active `.steadyarc/handoff.md`.

This is an agent adapter. It may add Codex-specific entry, execution, and sandbox guidance, but it does not redefine Engineering Arcs, evidence standards, project memory, or ownership semantics from the agent-neutral core.

## Inputs

Codex needs:

- the target repository;
- the released Steady Arc knowledge ZIP;
- the human's requested operation: inspect, bootstrap, or update;
- any repository-specific constraints or existing agent instructions.

If the requested operation is unclear, perform package and repository inspection plus a dry run only. Do not write files.

## Authority and precedence

1. Existing repository instructions and project-owned documentation remain authoritative within their scope.
2. Populated `.steadyarc/` files are project-owned continuity state.
3. The release ZIP is authoritative only for the released Steady Arc knowledge, manifest, updater, and managed artifacts it actually contains.
4. Conversation history provides intent, not repository state.
5. An active `.steadyarc/handoff.md` defines delegated ownership and scope. It does not authorize unrelated bootstrap, update, or product changes.

Never merge Codex-specific instructions into `.steadyarc/`, and never replace another agent's workflow files unless the human explicitly approves that exact change.

## Repository bridge

- `AGENTS.md` is the repository-native Codex entry point. It should route Codex first to current authority and state, then to this adapter only when Codex-specific operating guidance is needed.
- `.steadyarc/hello-codex.md` is an optional Codex bridge installed by this adapter. It explains coexistence and delegation without becoming project memory or a second Codex instruction system.
- Neither bridge may duplicate the core workflow or replace populated repository instructions.

## Package inspection

Before running or extracting anything:

1. list the archive contents without mutating the target repository;
2. identify the release version marker;
3. locate `BootstrapSteadyArc.ps1`, the bootstrap/update executable, the managed-tool manifest, templates, and knowledge documents;
4. verify that manifest-declared sources exist in the archive;
5. verify available hashes or package-integrity metadata when the release provides them;
6. report missing or inconsistent release components as a packaging defect and stop before writes.

Do not reconstruct missing tools from prose and do not copy every archive file into the target repository.

## Target-repository inspection

Inspect:

- repository root and version-control status;
- existing `AGENTS.md`, `CODEX.md`, or other agent instructions;
- build system, repository-local launchers, and test commands;
- existing `.steadyarc/` state;
- existing managed Steady Arc tools and their local modifications;
- path, language, and platform constraints relevant to the packaged payload helper.

If the working tree is already dirty, distinguish pre-existing changes from prospective Steady Arc changes and preserve them.

## Dry-run first

Use the release's repository-owned updater and run its dry-run operation before installation or update. The dry run must provide:

- compatibility findings;
- source and destination paths;
- absent, unchanged, locally modified, and unsupported classifications;
- files requiring explicit approval;
- planned `.steadyarc/` initialization, if any;
- validation and rollback expectations.

The dry run is inspection evidence only. It does not count as installation, update, build validation, or runtime smoke validation.

When the executable cannot run, use the manifest-derived manual placement plan from the same archive. Keep the same classifications, approval boundaries, and rollback requirements. Do not invent an ad hoc copy sequence.

## Bootstrap

Bootstrap means adding Steady Arc support where it is absent.

1. run the approved install operation after reviewing the dry run;
2. create only missing `.steadyarc/` files from packaged templates;
3. install compatible managed tools only at absent destinations;
4. adapt project-agnostic tools only when the package or bootstrap specification explicitly permits adaptation;
5. preserve existing project documentation and agent instructions;
6. run the target repository's supported build/tests;
7. smoke-test installed workflow tooling where the environment permits;
8. record bootstrap friction and assumptions in the required pilot-feedback section;
9. initialize or update `.steadyarc/handoff.md` only when the human delegates ownership or a return path must be recorded.

Bootstrap must be a separate bounded change from product implementation.

## Update

Update means moving already installed managed Steady Arc tools to the supplied release.

1. run the updater's update dry run;
2. classify each managed destination using the installed-state record and current content;
3. allow unattended replacement only when the destination is absent or still matches the previously installed managed version;
4. require explicit per-file human approval for locally modified managed tools;
5. never replace populated `.steadyarc/roadmap.md`, `engineering-notes.md`, `deferred-issues.md`, or `handoff.md`;
6. preserve or migrate installed-state metadata according to the updater contract;
7. use rollback-safe writes and restore touched files on failure;
8. validate the target repository after the update;
9. report release version, changed files, preserved local modifications, conflicts, and remaining manual steps.

A knowledge ZIP update is not a repository snapshot replacement and is not permission to reprioritize the target project's roadmap.

## Documentation and handoff

Follow `SteadyArc_InformationArchitecture.md` for document ownership and `SteadyArc_Workflow.md` for the handoff lifecycle. Codex-specific behavior is limited to the following:

For bootstrap or update work delegated to Codex:

1. preserve the original handoff delegation;
2. add an activity amendment only when scope, ownership, constraints, authoritative revision, or a blocking question materially changes;
3. do not turn the handoff into a chronological progress log;
4. complete the return report with package version, operation, files changed, validation, conflicts, unresolved issues, and ownership after return;
5. leave the handoff active only while Codex still owns work.

If no active handoff exists, do not create one merely to record routine work. Create or activate it only when the human requests a durable transfer of responsibility.

## Support relay

When the human enables the repository's optional support relay, Codex may invoke only the fixed operations exposed by `InvokeSteadyArcRelay.ps1`. The neutral transport, command-assembly, expiry, ACL, and output contracts are defined in `SteadyArc_Tooling.md`.

Treat the relay as an execution capability, not expanded authority. Do not attempt arbitrary commands, bootstrap mutation, release operations, or `PatchSequence.ps1` through the initial relay. Ask the human to start or restart it when human-session validation is required and the channel is unavailable.

When live-repository work is likely to require human-session validation, check relay availability before the final implementation step or return-document update. If the channel is unavailable or near expiry, ask the human to start it promptly instead of waiting until validation is the only remaining action. Each accepted operation renews the sliding lease, but Codex must not assume a relay is active merely because one was used earlier in the conversation.

## Required delivery

For bootstrap work, deliver exactly one unified patch artifact unless the repository-owned updater itself is the explicitly approved execution path. Keep bootstrap separate from unrelated product changes.

Choose the execution path from authority and repository access:

| Situation | Required behavior |
| --- | --- |
| Snapshot-only session | Inspect and deliver one unified patch; do not claim local installation. |
| Live repository, inspect or unclear operation | Run inspection and updater dry-run only. |
| Live repository, updater execution explicitly approved | Run the approved install or update operation and report repository changes. |
| Locally modified managed destination | Stop before writes and request explicit approval for that artifact ID. |
| Updater cannot execute | Use its manifest-derived manual plan to produce the approved patch or manual placement report. |

For a first bootstrap, distinguish managed-tool installation from project-memory initialization. The updater may install reusable artifacts and missing templates; project-specific roadmap, notes, and handoff content require repository evidence and the human's bootstrap delegation. Neither phase authorizes unrelated product work.

Report independently:

- patch applicability;
- project build/tests;
- runtime smoke validation.

Each status is passed, failed, or not performed. Never infer one from another.

Include:

- release version and package inspected;
- operation performed;
- exact updater or repository-supported command;
- managed files created, updated, preserved, or blocked;
- `.steadyarc/` files deliberately left untouched;
- rollback behavior;
- verification evidence and limitations;
- expected test-discovery change;
- handoff state and next owner;
- pilot feedback distinguishing reusable workflow defects from project-specific constraints.

Never claim checks that were not performed.
