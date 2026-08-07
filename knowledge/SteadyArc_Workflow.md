# Steady Arc Workflow

## 1. Purpose

Steady Arc is a project-agnostic engineering governance workflow for humans and cooperating AI assistants. It preserves engineering continuity while supporting implementation, validation, research, and maintenance work.

The workflow governs how work is organized. It does not restrict the type of work performed.

## 2. Engineering Arcs

An Engineering Arc is a bounded objective with explicit completion criteria. Arc types include:

- feature implementation;
- validation and regression hardening;
- refactoring or architectural change;
- research and prototyping;
- performance;
- infrastructure;
- UX and documentation;
- maintenance.

Each arc has:

1. an objective;
2. an ordered roadmap;
3. completion criteria;
4. an evaluation point before another arc begins.

Each arc has a stable **arc identifier** (a short name, e.g. `Workflow Information Architecture 1.0`) that handoffs and roadmap sections reference. An identifier is not reused for a materially different objective once its arc is complete or abandoned.

### 2.1 Parallel arcs

A repository normally advances one arc at a time, but more than one Engineering Arc may be simultaneously active when different agents are genuinely working on non-overlapping concerns.

- A repository has at most one **primary arc**, tracked as `## Current arc` in `.steadyarc/roadmap.md`. The primary arc is the default destination for new work and the one a human should expect to be moving when they check in casually.
- A repository may additionally have zero or more **concurrent arcs**, each tracked in its own subsection under `## Concurrent arcs` in `.steadyarc/roadmap.md`, using the same shape as the primary arc: objective, arc type, completion criteria, ordered items, and active item.
- Every arc, primary or concurrent, declares an explicit **area in scope**: the files, directories, or components it is allowed to change. Areas in scope are evidence, not intent — they must describe what the arc's roadmap items actually touch.
- **Non-interference rule:** two arcs may run concurrently only while their declared areas in scope do not overlap. If two arcs would need to change the same file, directory, or tightly coupled component, they must be sequenced (one paused or merged into the other), not run in parallel. Discovering an unavoidable overlap mid-arc is a blocking question, not a reason to silently proceed in both.
- Opening a concurrent arc requires the same rigor as opening the primary arc: an objective, completion criteria, and an explicit human decision that the work is worth parallelizing. It is not a way to bypass the "complete exactly one roadmap item per patch" rule within a single arc.
- Each concurrently active handoff (see §9.1) names the arc it belongs to. An agent must not act against a concurrent arc's scope using a handoff issued for a different arc.
- When a concurrent arc's completion criteria are met, it is closed the same way as any arc: evaluated, its roadmap subsection marked complete or removed, and its handoff moved to history. Closing one concurrent arc does not require pausing or closing others.
- Arc lifecycle step 10 (§4) may resolve to opening an additional concurrent arc instead of only choosing the next arc in sequence, provided the non-interference rule holds.

### 2.2 Ordered-item format

An arc's `## Ordered items` (primary, concurrent, or retained-context) is a narrative record, not a bare checklist. It has three parts:

1. **Mission** — the one-paragraph objective already required above, stated as what the arc builds or changes, not a task list.
2. **Accepted constraints** (optional, arc-scoped) — durable product, design, or engineering decisions that bind every item in this arc. These are narrower and shorter-lived than `.steadyarc/engineering-notes.md`, which holds cross-arc invariants; when a constraint would still matter after this arc closes, promote it to engineering notes instead of leaving it only here.
3. **Items** — each item has a stable **item ID** (`<ARC-PREFIX>-<NN>`, e.g. `WMP-03`), a short title, a canonical **state**, and a prose body explaining what the item does and why, not only that it is done.

Canonical states, and only these:

- **planned** — not yet started; no date stamp.
- **active** — currently being worked; no date stamp required, though a started-on date may be noted in prose.
- **complete** — all of the item's own criteria are met; requires a mandatory completion-evidence **date stamp** (`YYYY-MM-DD`) naming when that evidence was produced (a commit, a verified build, a recorded observation).
- **implemented; `<X>` deferred** — the core of the item is done but a named remainder `<X>` is intentionally not: also requires a mandatory completion-evidence date stamp, and `<X>` must be concrete enough to become its own future item rather than a vague caveat.

A date stamp is mandatory whenever a state asserts completion evidence (`complete` or `implemented; ... deferred`); it is never fabricated retroactively — an item completed before this rule existed may record `(date not recorded; predates mandatory date-stamp requirement)` instead of an invented date. This format replaces the plain `- [ ]`/`- [x]` checklist as the default ordered-item shape; it does not introduce a separate or competing roadmap protocol, and it applies identically to the primary arc and every concurrent arc in §2.1.

## 3. Core rules

- Treat the current repository snapshot as authoritative.
- When supplied, use the matching snapshot manifest as companion evidence about commit identity, archive boundaries, validation, runtime, integrity, and payload footprint; it does not supersede snapshot contents.
- Treat conversation history as intent and rationale, not repository state.
- Complete exactly one roadmap item per patch unless a tightly coupled prerequisite is unavoidable.
- Follow roadmap order unless the human explicitly reprioritizes it.
- Keep the repository buildable whenever practical.
- Prefer evidence-backed changes over speculative redesign.
- Classify failures as implementation, assertion, fixture, environment, or tooling defects before changing code.
- Fix implementation defects rather than weakening valid tests.
- Record unrelated discoveries in deferred issues instead of expanding current scope.
- Add focused regression coverage for defects and important semantics.
- State the expected test-discovery change when tests are added or removed.
- Use temporary diagnostics only when they improve evidence; remove them after the issue is understood unless the human approves keeping them.

## 3.1 Artifact-first continuation

The normal cross-agent continuation payload is:

- `latest snapshot.zip`;
- `latest test results.log`;
- `latest snapshot manifest.json`.

When the human supplies those files and asks an agent to look them over and continue, the request authorizes inspection and continuation planning. It does not by itself authorize repository changes beyond an active handoff recorded in the snapshot or a new explicit human delegation.

Resume in this order:

1. verify that the snapshot and test-log hashes match the manifest;
2. inspect the repository state in the snapshot, especially `.steadyarc/handoff.md`, `.steadyarc/roadmap.md`, and repository-native agent instructions;
3. reconcile the manifest commit, branch, working-tree status, build command, and test summary with the handoff;
4. report material mismatches instead of silently choosing one source;
5. state the current owner, delegated action, safe next action, and any approval needed before implementation.

The snapshot contents are authoritative project state. The manifest proves which committed snapshot and validation log were transferred and discloses working-tree state that a `git archive` snapshot does not contain. The handoff owns delegated scope and ownership. Neither the manifest nor a conversational phrase silently expands that scope.

A returned payload is self-confirming when:

- the archive and test-log hashes match the manifest;
- the manifest commit identifies the committed snapshot;
- validation passed or its limitations are explicitly accepted;
- `.steadyarc/handoff.md` has `Returned` status, a completed return report, and an owner after return.

Those conditions prove that the finalization which created the payload has already occurred. Historical amendments may describe a pre-finalization command, but the receiving agent must not repeat that command unless the current return report identifies a new uncommitted change or the human explicitly requests another finalization.

## 4. Arc lifecycle

1. Inspect the authoritative repository state.
2. Confirm the current arc, roadmap item, and completion criteria.
3. Investigate the smallest coherent unit of work.
4. Implement one roadmap item.
5. Verify the build and relevant tests.
6. Deliver a reviewable patch and rationale.
7. Update roadmap and deferred issues.
8. Repeat until completion criteria are met.
9. Pause for human evaluation, real-world testing, or architectural review.
10. Select the next arc from evidence rather than momentum alone, or open an additional non-overlapping concurrent arc (§2.1) when parallel specialist work is justified and does not require sequencing.

## 5. Failure investigation

When validation fails:

1. reproduce from the authoritative snapshot;
2. inspect implementation, fixture, assertion, and observed data;
3. add narrow diagnostics when the evidence is insufficient;
4. determine root cause before patching;
5. make the smallest correct change;
6. preserve or strengthen meaningful assertions;
7. verify the full relevant test scope;
8. remove temporary diagnostics that would create noise.

## 5.1 Patch production

When changes already exist in a working tree or paired directories and must be converted into a unified patch, use Git-native binary-safe diff generation whenever Git is available. The repository-provided `NewPatch.ps1` is the preferred producer-side interface. It generates `git diff --binary --full-index` output without normalizing file contents.

`NewPatch.ps1` is not part of the normal human handoff loop where the user uploads the latest snapshot and test log, the agent produces a patch, and the user runs `PatchSequence.ps1`.

- In a real Git working tree, generate from `HEAD` through an isolated temporary worktree so the source index and working tree are not mutated.
- When repository metadata is absent, provide byte-preserved baseline and modified directories; the tool creates a temporary Git repository, commits the baseline, overlays the modified tree, and emits the same binary-safe Git diff.
- Never round-trip repository files through text APIs merely to create a patch. Preserve original bytes, including line endings and binary content.
- Patch production and patch applicability remain separate evidence. A generated patch must still be checked against the authoritative snapshot.


## 5.3 Static evidence for PowerShell tooling

When PowerShell execution is unavailable, use the strongest available static evidence without relabeling it as runtime behavior.

Static evidence may include parser validation, focused source-contract tests, packaging and hash assertions, byte-preservation checks, and clearly identified historical runtime evidence for an unchanged script hash. Each check must state what it proves and what it does not prove.

The runtime-smoke validation status remains `not performed` unless the script's relevant behavior was actually executed. Parser success, source inspection, and passing tests that do not launch the script are not runtime smoke validation. When runtime evidence is required for acceptance, provide the repository-owned human-side smoke command and leave the gap explicit.

## 6. Patch delivery contract

Implementation responses should include:

- a unified patch;
- the exact application command supported by the repository;
- a concise recap;
- root cause or design rationale;
- why the change is correct;
- verification performed and any limitations;
- expected discovered-test change;
- roadmap update;
- the next roadmap item.

The patch must pass `git apply --check` against the authoritative snapshot before delivery whenever the environment permits.

## 7. Validation evidence

Validation is reported as independent statuses. A stronger status must never be inferred from a weaker one.

- **Patch applicability** — whether the unified patch passes `git apply --check` against the authoritative snapshot.
- **Project validation** — whether the repository's supported build and relevant tests completed successfully after the change.
- **Runtime smoke validation** — whether the changed executable behavior was launched and exercised in its intended environment.

Each status is one of **passed**, **failed**, or **not performed**, followed by the command or evidence and any environment limitation. `git apply --check` proves only patch applicability. Compilation or tests prove project validation only within their executed scope. Static inspection, parsing, or compilation of a script is not runtime smoke validation.

## 8. Multi-agent collaboration

Steady Arc assumes multiple specialist agents may participate.

Default responsibilities:

- **Governance agent:** roadmap continuity, architecture, investigation, validation strategy, regression design, and handoff state.
- **Implementation agent:** implementation, refactoring, code generation, and repository-local execution.
- **Human:** priority decisions, acceptance, credentials, external environment work, playtesting, and final authority.

These are defaults, not hard boundaries. An agent may perform work normally owned by another role when explicitly delegated. It must preserve the shared workflow artifacts, avoid silently redefining priorities, and leave a clean handoff.

A request to adjust work owned by another role is valid delegation; it does not require switching agents.

## 9. Handoff protocol

A true handoff is explicit and persisted in `.steadyarc/handoff.md`. The file is the repository-owned coordination record for the delegated interval or intervals; it is not a scratchpad, changelog, or substitute for the roadmap.

Each active handoff record states:

- status and handoff identifier;
- sending, receiving, and return owners;
- the arc identifier (§2.1) it belongs to, and the active roadmap item within that arc;
- authoritative revision or snapshot and build/test state;
- requested action and completion criteria;
- constraints, open questions, and in-scope/out-of-scope areas;
- expected documentation updates;
- return condition.

Before delegated work, the receiving agent reads the roadmap, engineering notes, deferred issues, handoff, and any agent-specific instructions. It confirms its understanding, identifies material ambiguity, and does not implement beyond the delegated action until the human resolves blocking ambiguity.

During delegated work:

- record progress and implementation detail in normal commits, tests, and code comments rather than continuously rewriting the handoff;
- update `.steadyarc/engineering-notes.md` only for durable invariants, architecture decisions, or workflow facts;
- update `.steadyarc/deferred-issues.md` for unrelated discoveries that should not expand the current arc;
- update `.steadyarc/roadmap.md` only when completing or explicitly reprioritizing roadmap work;
- amend `.steadyarc/handoff.md` when ownership, scope, constraints, authoritative revision, or a blocking question materially changes.

On return, preserve the original delegation section and complete the return report with work performed, verification, repository changes, unresolved issues, recommended next action, and ownership after return. Mark the handoff returned or closed; do not erase history merely to reset the template.

Write the returned report for the state in which it will be read. Its recommended next action must describe what follows receipt of the committed payload, not instruct the receiver to rerun the finalization that necessarily preceded that receipt.

An agent must not silently take ownership merely because it can perform the task. Explicit delegation in the conversation or handoff file is sufficient.

### Handoff states

Use one of these status values so humans, agents, and contract checks interpret the lifecycle consistently:

- **Inactive** — no delegated owner or resumable task.
- **Active — review** — inspection and recommendations only.
- **Active — implementation** — the named receiver owns the bounded delegated work.
- **Returned** — the receiver completed the return report and responsibility is back with the return owner.
- **Closed** — no further continuation is expected from this handoff.

A review-to-implementation delegation is recorded as a dated activity amendment and changes the status without rewriting the original delegation. A returned handoff must name the ownership after return and the safe next action. A later handoff gets a new identifier; do not recycle an old closed handoff as current state.

### Ownership-transition invariants

Treat the handoff as an append-preserving ownership-transition record:

- the original `From`, `To`, `Return owner`, delegation, and completion criteria are immutable historical facts;
- status plus the newest dated amendment describe the current phase without rewriting earlier authority;
- a scope, constraint, blocking-question, return-condition, or authoritative-revision change with the same receiver is an amendment;
- a different receiver, a materially different bounded objective, or resumed work after `Closed` requires a new handoff ID;
- an amendment names who made the change, why it changed, authority after the change, and the resulting return condition;
- `Returned` requires a completed return report, explicit ownership after return, and verification evidence or an explicit `not performed`;
- `Closed` is terminal and must not be reused as a convenient blank template;
- parallel independent delegations use separate handoff IDs and non-overlapping scopes.

The active handoff is the newest non-closed record that covers the delegated work. When only one arc is active, keep `.steadyarc/handoff.md` as a single current record with a one-row index (§9.1). When multiple arcs are concurrently active, `.steadyarc/handoff.md` holds the index plus one record per open handoff ID, per §9.1. When a new handoff ID replaces a returned or closed record, preserve the prior file under `.steadyarc/handoff-history/<handoff-id>.md`. Historical records are evidence, not additional current-state candidates. If a repository instead stores prior records in one file, preserve them under clearly dated history and keep exactly the current index and its open records as current state. If authority is ambiguous, stop implementation and ask the human rather than inferring ownership from the most recent prose.

### Provenance in an active handoff

The authoritative revision must identify one commit or snapshot baseline. Record later authoritative-revision, working-tree, or matching-validation changes as activity amendments. Avoid mixing “current checkout” with an older commit identifier without explaining their relationship.

When the working tree is dirty, state whether those changes are authoritative inputs, implementation work in progress, or a known platform artifact. An archive produced from `HEAD` contains committed state only; the manifest must disclose omitted working-tree changes, and the receiving agent must not discard them merely because they are absent from the archive.

### 9.1 Multiple concurrently active handoffs

Parallel arcs (§2.1) require more than one active handoff at the same time. This is the normal shape for a repository running concurrent arcs, not an exceptional case.

- `.steadyarc/handoff.md` opens with an **Active handoffs index**: one row per open handoff listing its handoff ID, arc, receiver, and status. The index is the first thing a picking-up agent reads; it must not assume the file contains exactly one record.
- The index is followed by one full record per listed handoff ID, each using the normal handoff shape (status, delegation, engineering context, activity amendments, return report).
- Every active handoff names the arc it belongs to (§2.1). An agent must confirm its handoff's arc matches the concurrent arc whose scope it is about to touch before acting.
- Two active handoffs must not declare overlapping "areas in scope." If a needed change would cross into another active handoff's declared area, stop and raise it as a blocking question rather than acting across the boundary.
- When a handoff tied to a concurrent arc returns or closes, move only that record to `.steadyarc/handoff-history/<handoff-id>.md` and remove its row from the index. Sibling handoffs and their arcs are unaffected.
- A repository with only one active handoff may keep the index to a single row rather than omitting it, so the index format never needs to change when a second concurrent arc opens.

## 10. Agent coexistence

Steady Arc core support remains separate from agent-specific workflow documentation. Do not overwrite, merge, or reinterpret an agent's native instructions unless the human explicitly asks.

An agent adapter may provide a repository bridge that explains the shared contract, but it must not prescribe another agent's internal workflow or redefine the agent-neutral core.

### 10.1 Capability profiles

Cooperating agents differ in git access, commit automation, execution sandboxing, and cross-session memory. `knowledge/SteadyArc_AgentProfiles.md` defines agent-neutral **capability profiles** (categorized by what an agent can do, not by vendor name) and the operating guidance each profile needs to follow this workflow safely. A concrete agent adapter should state which profile it matches instead of duplicating profile guidance.

## 11. Automation scripts are first-class code

Repository automation deserves the same design discipline as production code.

- Separate discovery, validation, environment initialization, execution, and user interaction.
- Prefer small functions with explicit inputs, outputs, and failure contracts.
- Keep resolvers free of side effects; return structured results that identify both the value and its source.
- Apply environment changes in one explicit initialization step.
- Keep orchestration thin so each responsibility can be tested independently.
- Treat shell parsing, quoting, working-directory behavior, and environment inheritance as correctness concerns rather than incidental scripting details.

### 11.1 Cross-platform script parity

When a PowerShell automation script is part of the human handoff loop, provide a bash counterpart with strictly equivalent outcomes rather than requiring PowerShell as the only supported shell.

- Equivalent outcomes mean the same git operations, the same generated artifacts (archive, manifest, applied-patch archiving), the same exit-code contract, and the same failure classification for the same inputs. Cosmetic differences (message wording, OS-native line endings) are acceptable; differences in what gets committed, staged, or archived are not.
- Any host-specific default, such as a patch download directory, is a configurable property with the same name and precedence in both script families rather than a value hardcoded differently per platform. See `knowledge/SteadyArc_Tooling.md` for the current property.
- When only one script family can be executed in the current environment, follow the static-evidence rule in §5.3: state what was checked (parser success, source-contract tests, packaging) and what remains `not performed` rather than implying the other family was exercised.
