# Codex Instructions

Steady Arc governs repository workflow in this project. This file is the Codex entry point, not a duplicate workflow specification.

## Enter the repository

Read in this order and stop once you have enough authority and context for the delegated task:

1. `.steadyarc/handoff.md` — current ownership, delegated authority, constraints, and return condition.
2. The current arc and active item in `.steadyarc/roadmap.md`.
3. Relevant sections of `.steadyarc/engineering-notes.md` and `.steadyarc/design-notes.md`; use the former for engineering invariants and the latter for durable product/design intent.
4. `.steadyarc/user-preferences.md` for explicit standing collaboration preferences.
5. `knowledge/SteadyArc_CodexWorkflow.md` only for Codex-specific operation, continuation-artifact handling, or knowledge-ZIP bootstrap/update work.
6. `knowledge/SteadyArc_InformationArchitecture.md` only when document authority or placement is unclear.

Inspect source, tests, scripts, and current validation evidence before changing behavior.

## Context recovery for short-memory agents

Repository memory, not task conversation history, is authoritative. Before an
implementation pass, write down from the active handoff and roadmap: the defect
being corrected, the bounded acceptance criteria, relevant design excerpts, and
the required evidence level. If a follow-up says to fix or continue an issue,
recover that issue from these files and the inspected source/tests before asking
the human to repeat it. Do not mark visual behavior complete from structural
tests alone; distinguish contract, integration, rendered-fixture, and human
visual evidence.

For geometry work, read `docs/guides/geometry-toolkit.md` before designing a
new generator. Prefer composition of the shared toolkit; asset-specific geometry
primitives require a demonstrated gap and should normally become reusable
engine-neutral operations first.

## Validation artifact workflow (GitHub Copilot task agent)

File uploads are not available in this interface. Instead, the human operator runs
the platform-appropriate publisher locally, which runs the corresponding patch
sequence without a patch, commits the generated log and manifest separately, and
pushes the branch so this agent can read them directly:

```powershell
.\PublishValidationArtifacts.ps1
```

```bash
./PublishValidationArtifacts.sh
```

The PowerShell publisher accepts optional `-SourceCommitMessage` and
`-ArtifactCommitMessage` parameters. The Bash publisher accepts the corresponding
optional positional arguments. Both require a compatible JDK and do not commit
`latest snapshot.zip`.

**Reading artifacts after a push:**
- `latest snapshot manifest.json` — always read first; contains the committed source revision SHA, build outcome, and test summary.
- `latest test results.log` — always read for full Maven output when diagnosing failures.
- `latest snapshot.zip` — gitignored (large binary); only request it when inspecting compiled output or files not visible from the patch.

**Manifest SHA vs HEAD mismatch:** The platform-specific patch sequence commits source
changes, generates the manifest and log, then the publisher makes a second,
artifact-only commit and pushes both commits. The `repository.commit` in the manifest
identifies the committed source revision; the artifacts live in the subsequent commit.
This is expected. Treat the manifest's `repository.commit` as the authoritative source
baseline, not the current HEAD when the artifacts were pushed.

**Artifact merge conflicts:** `latest test results.log` and `latest snapshot manifest.json`
are an inseparable generated pair. Do not manually merge their contents or treat either
side as valid evidence after a source merge or rebase. Prefer aborting the operation,
synchronizing the source branch, and asking the human to rerun the publisher. If a merge
must finish first, have the human rerun the publisher on the merged branch and verify
that the replacement manifest names the merged source commit and hashes the replacement
test log.

## Authority boundary

Treat `Active — review` as inspection-only. Treat `Active — implementation` as the named receiver's bounded delegated scope, not as general permission to modify the repository. A request to inspect continuation artifacts and continue authorizes inspection and planning unless an applicable handoff or explicit human instruction grants implementation authority.

Ask the human about material ambiguity before implementation when the handoff is review-only or its completion criteria cannot be satisfied safely.

## During delegated work

Follow the documentation placement rules in `knowledge/SteadyArc_InformationArchitecture.md`:

- preserve the original delegation and use `.steadyarc/handoff.md` only for material ownership, scope, constraint, blocking-question, or return changes;
- keep routine progress in commits, tests, code comments, or implementation documentation;
- record durable project decisions in `.steadyarc/engineering-notes.md`;
- record unrelated discoveries in `.steadyarc/deferred-issues.md`;
- complete the handoff return report when responsibility returns.

Before the last implementation step or return-document update, determine whether required validation needs the human-session support relay. If it does, check the channel and ask the human to start or restart it early; do not wait until validation is the only remaining action.

When given a Steady Arc knowledge ZIP for another repository, follow `knowledge/SteadyArc_CodexWorkflow.md`. Start with package inspection and updater dry-run. Never replace populated `.steadyarc/` project memory or locally modified managed tools without explicit human approval.
