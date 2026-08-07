# Steady Arc Information Architecture

## Purpose

This document is the authoritative map of Steady Arc information. It explains what each class of document is for, which files are authoritative for a question, and how a new human or engineering agent should navigate the repository without relying on prior conversation.

It defines information placement and navigation. It does not replace the workflow rules, current project state, or agent-specific operating instructions.

## Information classes

Every maintained Steady Arc document belongs to one primary class.

### 1. Entry points

Entry points orient a specific reader and direct them to authoritative material. They should be short and should link rather than restate policy.

Examples:

- `README.md` — human repository orientation;
- `AGENTS.md` — Codex repository entry point;
- `CUSTOM_GPT_SETUP.md` — Custom GPT operator setup and pilot entry point;
- `knowledge/SteadyArc_Concordance.md` — token-compressed topic→document§section index over current knowledge, for fast agent lookup rather than full-document reading. It is descriptive of headings that already exist elsewhere; it is never itself the authoritative source for a rule.

Entry points are not authoritative for active ownership, roadmap state, or durable engineering decisions.

### 2. Current project state

Current-state files answer what is happening now.

- `.steadyarc/handoff.md` — the active handoffs index plus one full record per open handoff ID: current ownership transition, delegated authority, constraints, blocking questions, return evidence, and next owner for each active arc (see "Multiple concurrently active handoffs" below);
- `.steadyarc/handoff-history/` — preserved returned or closed handoff records keyed by immutable handoff ID; these provide historical evidence but never compete with `handoff.md` as current state;
- `.steadyarc/roadmap.md` — the primary Engineering Arc under `## Current arc`, any concurrently active arcs under `## Concurrent arcs`, each arc's ordered items, completion state, and active item, plus shared planning decisions;
- `.steadyarc/deferred-issues.md` — unrelated discoveries deliberately excluded from the active item.

Current-state files are project-owned. Bootstrap and update tooling must not replace populated copies.

### 3. Durable project memory

- `.steadyarc/engineering-notes.md` — durable project-specific invariants, architectural decisions, terminology, validated operating facts, and rationale expected to remain useful after the current handoff ends.

Routine progress, temporary status, and duplicated roadmap history do not belong here.

### 3b. Durable design memory

- `.steadyarc/design-notes.md` — durable **product/design** intent: what is being built and why, softer tradeoffs, user-facing goals, and directions rejected for product reasons rather than engineering ones.

This is distinct from engineering notes. Engineering notes preserve durable *engineering* invariants and architectural decisions; design notes preserve durable *product/design* rationale — the "what are we building and why" question that engineering notes do not answer. A decision belongs here when it is about user-facing intent or product tradeoffs, and in engineering notes when it is about architecture, invariants, or implementation constraints. Some decisions have both a design rationale and an engineering consequence; record each half in its own file and link between them rather than duplicating the narrative.

Routine progress, implementation-level rationale, and temporary status do not belong here.

### 3c. User collaboration preferences

- `.steadyarc/user-preferences.md` — durable notes about how a specific human prefers to collaborate: preferred agent capability profiles for particular kinds of work, communication or review style, and other standing preferences that are independent of any single arc or handoff.

This answers "how does this human like to work with agents on this project," which is a different question from "what is durably true about the engineering" (engineering notes) or "what are we building and why" (design notes). It is not a place for one-off instructions that belong in the active handoff.

### 4. Normative workflow specification

- `knowledge/SteadyArc_Workflow.md` — agent-neutral Engineering Arc, roadmap, evidence, validation, delegation, documentation, and patch-delivery rules;
- `knowledge/SteadyArc_ProjectBootstrap.md` — normative bootstrap and update requirements;
- `knowledge/SteadyArc_Tooling.md` — reusable tool behavior and operating contracts;
- `knowledge/SteadyArc_AgentProfiles.md` — agent-neutral capability profiles (local relay, patch-exchange, remote background) and the operating guidance each needs.

These files define Steady Arc itself. They must remain agent-neutral unless a requirement is intrinsically tied to a particular tool surface.

### 5. Agent adapters

Agent adapters translate the core workflow into the conventions and capabilities of a particular assistant.

- `knowledge/SteadyArc_CodexWorkflow.md` — Codex bootstrap/update and repository-operation adapter;
- `knowledge/CustomGPT_SystemPrompt.md` — Custom GPT adapter for uploaded-artifact handling, response behavior, and tool-surface constraints;
- future agent adapters should remain separate rather than duplicating or modifying the core workflow.

An adapter may add agent-specific entry sequences or cautions, but it must not redefine Engineering Arcs, evidence standards, ownership semantics, or project memory.

The dependency direction is one way: adapters depend on the agent-neutral core. Core documents do not require a particular agent adapter, name agent-owned repository files as canonical memory, or assign policy responsibilities to a branded assistant. Names that identify real compatibility artifacts or implemented tool surfaces may remain in implementation contracts when changing the name would misdescribe the artifact.

### 6. Implementation and release artifacts

Source code, tests, scripts, manifests, templates, version files, and packaging configuration implement or distribute the workflow.

Implementation detail belongs in:

- code and code comments when local to the implementation;
- tests when expressing executable contracts;
- commits and patch recaps for bounded change history;
- implementation documentation when operators need it.

Implementation artifacts do not become normative merely because they encode current behavior. Conflicts between behavior and normative specification must be resolved explicitly.

## Entry-point navigation contract

Each entry point must answer three questions quickly:

1. Who is this path for?
2. What is the first authoritative file to read?
3. Under what condition should the reader continue to another file?

Entry points should use a short ordered path or task table. They should not require a reader to consume every workflow document before inspecting the active task. Once the reader has enough authority, current state, and relevant durable context, source and evidence inspection takes precedence over additional general reading.

Repository entry points have these primary owners:

- `README.md` routes humans and general repository visitors by task;
- `AGENTS.md` routes Codex into current authority and only then to its adapter;
- `CUSTOM_GPT_SETUP.md` routes the human operator through Custom GPT configuration and the external pilot;
- `VERSIONING.md` routes release operators through version transitions.

A link to an authoritative source is preferred to a condensed restatement. Small safety reminders are acceptable when they prevent action before authority is established.

## Authority by question

Use the narrowest authoritative source:

| Question | Authoritative source |
| --- | --- |
| What may this agent do now? | The matching record for the agent's arc in `.steadyarc/handoff.md` (see the Active handoffs index when more than one arc is active) plus the human's explicit instruction |
| What work is active and what comes next? | `.steadyarc/roadmap.md` — `## Current arc` for the primary arc, `## Concurrent arcs` for parallel arcs; each arc answers this question independently |
| What durable project decisions must be preserved? | `.steadyarc/engineering-notes.md` for engineering invariants; `.steadyarc/design-notes.md` for product/design rationale |
| Where should an unrelated discovery go? | `.steadyarc/deferred-issues.md` |
| How does this human prefer to collaborate? | `.steadyarc/user-preferences.md` |
| What are the general Steady Arc rules? | `knowledge/SteadyArc_Workflow.md` |
| How is Steady Arc installed or updated? | `knowledge/SteadyArc_ProjectBootstrap.md` |
| How should reusable tools behave? | `knowledge/SteadyArc_Tooling.md` |
| What operating guidance does an agent's capability profile need? | `knowledge/SteadyArc_AgentProfiles.md` |
| How should Codex consume a release or operate here? | `AGENTS.md` and `knowledge/SteadyArc_CodexWorkflow.md` |
| What does the current implementation actually do? | source, tests, scripts, and current validation evidence |
| Where is a given topic documented, at a glance? | `knowledge/SteadyArc_Concordance.md` (pointer only — confirm against the cited document before acting) |

When two sources appear to conflict, do not silently choose the more convenient one. Preserve repository state, identify the conflict, and resolve it within the bounded item or raise it as a blocking question.

## Reading paths

### New agent entering an existing Steady Arc repository

1. Read the repository-specific entry point, if present.
2. Read `.steadyarc/handoff.md`, starting with the Active handoffs index if more than one handoff is open, and read the record matching this agent's delegation.
3. Read the current arc — and, if this delegation is for a concurrent arc, that arc's subsection — and its active item in `.steadyarc/roadmap.md`.
4. Read relevant durable decisions in `.steadyarc/engineering-notes.md` and, for product/design questions, `.steadyarc/design-notes.md`.
5. Read `.steadyarc/user-preferences.md` for standing collaboration preferences.
6. Read only the normative or agent-adapter sections needed for the delegated work, including the matching capability profile in `knowledge/SteadyArc_AgentProfiles.md`.
7. Inspect source, tests, and validation evidence before changing behavior.

### Human evaluating project state

1. Read `.steadyarc/roadmap.md`, including any `## Concurrent arcs` subsections.
2. Read `.steadyarc/handoff.md` when ownership has moved or delegated work is active; check the index first if more than one handoff is open.
3. Read validation evidence and the latest patch recap.
4. Consult engineering notes for engineering rationale and design notes for product rationale, not routine progress.

### Bootstrap or update operator

1. Read the relevant operator entry point.
2. Inspect the release and target repository.
3. Follow `knowledge/SteadyArc_ProjectBootstrap.md`.
4. Use the applicable agent adapter when an assistant performs the operation.
5. Preserve populated `.steadyarc/` project memory.

## Placement rules

Before adding information, ask:

1. Is this about present ownership or return evidence? Put it in `handoff.md`.
2. Is this ordered work or its completion state? Put it in `roadmap.md`, in the matching arc's subsection if a concurrent arc is involved.
3. Will this decision still matter after the current arc or handoff? Put engineering rationale in `engineering-notes.md` and product/design rationale in `design-notes.md`.
4. Is it unrelated to the active item? Put it in `deferred-issues.md`.
5. Is it about how this human prefers to collaborate, independent of any single arc? Put it in `user-preferences.md`.
6. Is it a reusable, project-agnostic rule? Put it in the appropriate normative knowledge file.
7. Is it agent-specific? Put it in an adapter, agent entry point, or the matching capability profile in `knowledge/SteadyArc_AgentProfiles.md`.
8. Is it local implementation detail? Keep it with code, tests, commits, or implementation documentation.

Do not copy the same narrative into several files. Link to the authoritative source and add only the context required by the current document.



## Handoff as ownership-transition record

`.steadyarc/handoff.md` is not merely a status page. It is the authoritative, append-preserving record of a bounded transfer of engineering responsibility — or, when concurrent arcs are active, an index of several such records (see `knowledge/SteadyArc_Workflow.md` §9.1).

Its information has two layers:

- **immutable delegation facts** — handoff ID, original sender, receiver, return owner, requested action, completion criteria, and initial constraints;
- **current transition state** — lifecycle status, newest material amendment, authoritative revision, return evidence, unresolved issues, and ownership after return.

Use an amendment when the same receiver keeps the same bounded objective but authority, scope, constraints, blocking questions, revision, or return condition changes. Create a new handoff ID when the receiver changes, the bounded objective materially changes, or closed work is resumed.

A return is complete only when the record states:

- what was completed;
- what verification passed, failed, or was not performed;
- unresolved issues;
- the recommended next action;
- the owner after return.

Earlier delegation and amendment text remains historical evidence. Do not edit it to make later decisions appear to have existed from the beginning. `Closed` is terminal; future work starts a new record.

The return report is read after repository finalization. Its recommended next action therefore starts with receipt and evaluation of the manifest-matching committed payload; it never asks the receiver to repeat that completed finalization. If the active record named a pre-change baseline, the returned record distinguishes that baseline from the final authority supplied by the companion manifest.

## Operational-memory placement contract

The project-memory files — `roadmap.md`, `handoff.md`, `engineering-notes.md`, `design-notes.md`, `user-preferences.md`, and `deferred-issues.md` — answer different questions. Placement is determined by the information's function, not by which file an agent happens to be editing.

### `.steadyarc/roadmap.md` — ordered intent

Use the roadmap for accepted work ordering and state:

- **Put here:** the primary Engineering Arc objective under `## Current arc`, any concurrent arcs under `## Concurrent arcs`, each arc's ordered bounded items, completion markers, single active item, and shared planning decisions that constrain later items.
- **Do not put here:** implementation diaries, test transcripts, detailed return evidence, unrelated ideas, or architectural rationale that must survive after the item is complete.
- **Positive example:** `- [ ] Add drift-aware bootstrap update support.`
- **Negative example:** `Tried three parser approaches today; the second failed on Windows.`

An item is complete only when its acceptance evidence exists. Marking it complete is state, not a substitute for the evidence. When more than one arc is active, each arc's active item is independent; do not collapse them into a single "active item" answer.

### `.steadyarc/handoff.md` — ownership and transfer evidence

Use the handoff for the active ownership transition or transitions:

- **Put here:** an Active handoffs index when more than one handoff is open, and per record: current and delegated owner, the arc it belongs to, authority, bounded scope, constraints, blocking questions, authoritative revision, return condition, amendments that materially change delegation, verification returned, unresolved issues, and next owner.
- **Do not put here:** routine progress notes, speculative design discussion, duplicated roadmap history, or durable decisions without a direct ownership consequence.
- **Positive example:** `Codex may change the Widget relay only; release production remains out of scope.`
- **Negative example:** `Renamed three local variables and reformatted the parser.`

The handoff explains who may act and what evidence returns ownership. It is not the project's general activity log.

### `.steadyarc/engineering-notes.md` — durable project knowledge

Use engineering notes for facts and rationale expected to remain useful after the current item and handoff end:

- **Put here:** architectural decisions, invariants, terminology, validated environment or interoperability facts, rejected alternatives whose rationale prevents repetition, and stable operating constraints.
- **Do not put here:** current ownership, task status, temporary failures, full command output, every implementation choice recorded in a patch recap, or product/design rationale that belongs in `design-notes.md`.
- **Positive example:** `Populated .steadyarc files are project-owned memory and are never replaced by updater payloads.`
- **Negative example:** `The current patch still needs one more test before handoff.`

A result becoming old does not make it durable. Record it only when future work must preserve or reason from it.

### `.steadyarc/design-notes.md` — durable design and product rationale

Use design notes for durable **product/design** intent rather than engineering architecture:

- **Put here:** what is being built and why, user-facing goals, softer tradeoffs, and directions rejected for product reasons.
- **Do not put here:** architectural or implementation rationale (use `engineering-notes.md`), current ownership or task status, or routine progress.
- **Positive example:** `The widget favors a single always-on-top launcher over a tray icon because discoverability matters more than desktop tidiness for new users.`
- **Negative example:** `The updater performs atomic writes with a rollback set for every destination it may change.` (this is an engineering invariant — it belongs in `engineering-notes.md`)

A design decision that also has an engineering consequence gets a short record in each file, linked rather than duplicated.

### `.steadyarc/user-preferences.md` — collaboration preferences

Use this file for durable notes about how a specific human prefers to collaborate:

- **Put here:** preferred agent capability profile for a kind of work, commit-granularity or review-style preferences, and other standing preferences independent of any single arc.
- **Do not put here:** one-off instructions for the current handoff, or preferences that materially change delegated scope (those belong in the handoff itself).
- **Positive example:** `The human prefers small, individually reviewable commits when an agent's environment supports autonomous commits.`
- **Negative example:** `For this handoff only, skip the release step.` (belongs in the active handoff, not here)

### `.steadyarc/deferred-issues.md` — deliberately inactive discoveries

Use deferred issues for worthwhile findings that are outside the active bounded item:

- **Put here:** a concise problem or opportunity, why it is deferred, any prerequisite or safety boundary, and enough context to evaluate it later.
- **Do not put here:** hidden blockers to the active item, accepted roadmap work, vague brainstorming, or defects that must be fixed for the current patch to be correct.
- **Positive example:** `Evaluate signed artifact manifests in a separate security arc after the 1.0 pilot.`
- **Negative example:** `Tests fail on the changed behavior, but defer fixing them.`

Listing an issue does not activate it. Promotion into an Engineering Arc requires an explicit planning decision.

### Placement decision sequence

When information appears to fit more than one file, decide in this order:

1. Does it change who may act, the delegated boundary, or the evidence required to return ownership? Use `handoff.md`.
2. Does it change accepted work order, item state, or the current active item for some arc? Use `roadmap.md`.
3. Must future work preserve or reason from it after the current transfer ends? Use `engineering-notes.md`.
4. Is it durable product/design rationale — what is being built and why? Use `design-notes.md`.
5. Is it a durable preference about how this human collaborates, independent of any single arc? Use `user-preferences.md`.
6. Is it useful but unrelated to the active item? Use `deferred-issues.md`.
7. Otherwise keep it with the implementation: code, tests, commit, patch recap, or operator documentation.

Use links or short references when one event affects several authorities. Do not copy a complete narrative into multiple memory files.

## Maintenance rules

- Each document has one primary information class.
- Entry points stay concise and avoid becoming shadow specifications.
- Current-state files are updated as state changes; normative files change only when the reusable workflow changes.
- Agent adapters may depend on the core but the core must not depend on a specific agent.
- Historical detail should remain only when it explains a durable decision or provides required handoff evidence.
- Navigation changes should reduce required reading, not add another mandatory layer.
- Update `knowledge/SteadyArc_Concordance.md` when a heading in an indexed document is added, renamed, or removed, or when a new `knowledge/*.md`/`.steadyarc/*.md` file is created; it is a pointer index, never an alternate source of the rule itself.
