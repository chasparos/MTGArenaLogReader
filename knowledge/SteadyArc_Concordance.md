# Steady Arc Concordance

Machine-oriented index over current Steady Arc knowledge. Token-compressed for agent lookup, not human prose. Not authoritative — resolve every mapped ref against the cited doc/section before acting; this file compresses locations, not rules. On conflict: cited source wins.

## 0. Legend

Doc codes:

```
IA =knowledge/SteadyArc_InformationArchitecture.md
WF =knowledge/SteadyArc_Workflow.md
TL =knowledge/SteadyArc_Tooling.md
PB =knowledge/SteadyArc_ProjectBootstrap.md
AP =knowledge/SteadyArc_AgentProfiles.md
CW =knowledge/SteadyArc_CodexWorkflow.md
SP =knowledge/SteadyArc_AssistantSandboxProfile.md
GPT=knowledge/CustomGPT_SystemPrompt.md
RM =.steadyarc/roadmap.md
HO =.steadyarc/handoff.md
HH =.steadyarc/handoff-history/<id>.md
EN =.steadyarc/engineering-notes.md
DN =.steadyarc/design-notes.md
UP =.steadyarc/user-preferences.md
DI =.steadyarc/deferred-issues.md
TMPL_H=templates/handoff.md
TMPL_C=templates/hello-codex.md
```

Notation: `topic → DOC§section [state]`. `§N` = numbered heading, `§Name` = named heading, `~` = distributed/no single anchor, `+` = also see. State tags: `[N]`=normative rule, `[S]`=current state (mutates), `[M]`=durable memory, `[A]`=adapter-only, `[Ex]`=worked example.

## 1. Class map (IA §Information classes)

```
entry      : README.md, AGENTS.md, CUSTOM_GPT_SETUP.md, VERSIONING.md               [A]
state      : HO, RM, DI                                                              [S]
mem.eng    : EN                                                                      [M]
mem.design : DN                                                                      [M]
mem.pref   : UP                                                                      [M]
spec       : WF, PB, TL, AP                                                          [N]
adapter    : CW, GPT                                                                 [A]
impl       : src/*, pom.xml, *.ps1, *.sh, templates/*                                [-]
concordance: this file (SteadyArc_Concordance.md)                                    [A]
```

## 2. Topic index (alphabetical)

```
active-item                    → RM§Active item                         [S]
agent-adapter-rule              → IA§5 +WF§10                            [N]
agent-capability-profiles       → AP~ +WF§10.1                          [N]
arc-definition                  → WF§2                                   [N]
arc-lifecycle(10 steps)         → WF§4                                   [N]
arc-type                        → RM§Current arc +WF§2                  [S,N]
areas-in-scope(arc)             → RM§Current arc +WF§2.1                [S,N]
authority-by-question(table)    → IA§Authority by question               [N]
bash-script-parity              → WF§11.1 +TL§Cross-platform...          [N]
bootstrap-goal                  → PB§Bootstrap goal                      [N]
bootstrap-phases                → PB§Bootstrap phases                    [N]
bootstrap-verification-suite    → PB§Bootstrap verification suite        [N]
BootstrapSteadyArc.ps1          → TL§BootstrapSteadyArc.ps1 and updater CLI [N]
capability-profile:local-relay  → AP§Profile: Local relay agent           [N]
capability-profile:patch-exch.  → AP§Profile: Patch-exchange agent        [N]
capability-profile:remote-bg    → AP§Profile: Remote background agent     [N]
CodexWorkflow-adapter           → CW~                                     [A]
completion-criteria(arc)        → RM§Current arc +WF§2                  [S,N]
concordance-file(this)          → this file                              [A]
concurrent-arcs                 → RM§Concurrent arcs +WF§2.1             [S,N]
constraint-catalogue(sandbox)   → SP§Constraint catalogue                [N]
core-rules                      → WF§3                                   [N]
Custom-GPT-adapter              → GPT~                                   [A]
deferred-issue-placement        → IA§`.steadyarc/deferred-issues.md`     [N]
delegation(handoff field)       → HO§Delegation +TMPL_H§Delegation       [S]
design-notes-placement          → IA§`.steadyarc/design-notes.md` +DN~   [N]
design-vs-engineering-notes     → IA§3b +DN§Why design notes are separate [N]
entry-point-contract            → IA§Entry-point navigation contract     [N]
evidence(3-axis validation)     → WF§7                                   [N]
failure-investigation           → WF§5                                   [N]
handoff-amendment-rule          → WF§9 +WF§Ownership-transition invariants [N]
handoff-history-dir             → IA§`.steadyarc/handoff-history/` +WF§9.1 [N]
handoff-index(multi-arc)        → HO§Active handoffs index +WF§9.1       [S,N]
handoff-protocol                → WF§9                                   [N]
handoff-states(enum)            → WF§Handoff states                      [N]
information-classes(all)        → IA§Information classes                 [N]
item-states(canonical enum)     → WF§2.2                                 [N]
knowledge-packaging             → EN§Knowledge packaging                 [M]
maintenance-rules(IA doc)       → IA§Maintenance rules                   [N]
NewPatch.ps1                    → TL§NewPatch.ps1 +WF§5.1                [N]
non-interference-rule           → WF§2.1                                 [N]
ordered-item-format(narrative)  → WF§2.2                                 [N]
ordered-items(current arc)      → RM§Ordered items                       [S,Ex]
parallel-arcs                   → WF§2.1                                 [N]
patch-delivery-contract         → WF§6                                   [N]
patch-download-location(prop)   → TL§Patch download location property    [N]
patch-production                → WF§5.1                                 [N]
PatchSequence.ps1               → TL§PatchSequence.ps1                   [N]
payload-button                  → TL§Payload button                      [N]
placement-decision-sequence     → IA§Placement decision sequence         [N]
placement-rules(general)        → IA§Placement rules                    [N]
project-bootstrap-normative     → PB~                                    [N]
reading-path:new-agent          → IA§New agent entering...               [N]
reading-path:human              → IA§Human evaluating project state      [N]
reading-path:bootstrap-op       → IA§Bootstrap or update operator        [N]
release-candidate-evidence      → RM§Release-candidate evidence          [S]
retained-context(prior arc)     → RM§Retained context...                 [S]
RunWidget.ps1                   → TL§RunWidget.ps1                       [N]
sandbox-profile(constraints)    → SP~                                    [A]
sandbox:1-repo-write-scope      → SP§1. Single-repository write scope    [N]
sandbox:no-release-archive      → SP§2. Release archive not supplied     [N]
sandbox:jdk-mismatch            → SP§3. JDK version mismatch             [N]
sandbox:restricted-push         → SP§4. Restricted push path             [N]
sandbox:no-attachments          → SP§5. No file attachments available    [N]
snapshot-contract                → TL§Snapshot contract                  [N]
static-evidence(PowerShell)      → WF§5.3 +TL§Static PowerShell evidence  [N]
support-relay                    → CW§Support relay +TL§Sandbox support relay [N]
tool-isolation                   → TL§Tool isolation                     [N]
updater-conflict-handling        → TL§Bootstrap and tool updates         [N]
upstream-feedback-packet         → SP§Upstream feedback packet format    [A]
user-preferences-placement       → IA§3c +UP~                            [N]
validation-status-contract       → TL§Validation status contract         [N]
```

## 3. Reverse index (doc → topics, one line each)

```
IA  : entry-points, current-state, durable-memory×3, normative-spec, adapters, impl-artifacts,
      entry-point-contract, authority-table, reading-paths×3, placement-rules,
      handoff-as-ownership-record, operational-memory-placement×5, maintenance-rules
WF  : purpose, engineering-arcs, parallel-arcs(2.1), ordered-item-format(2.2), core-rules,
      artifact-first-continuation(3.1), arc-lifecycle(4), failure-investigation(5),
      patch-production(5.1), static-evidence(5.3), patch-delivery(6), validation-evidence(7),
      multi-agent-collab(8), handoff-protocol(9)+states+invariants+provenance+multi(9.1),
      agent-coexistence(10)+capability-profiles(10.1), automation-scripts(11)+script-parity(11.1)
TL  : canonical-support-paths, maven-wrapper-req, cross-platform-contract, NewPatch.ps1,
      PatchSequence.ps1+patch-download-location+script-parity, bootstrap/tool-updates,
      BootstrapSteadyArc.ps1+updater-CLI, payload-button, RunWidget.ps1,
      validation-status-contract, static-PowerShell-evidence, snapshot-contract,
      tool-isolation, sandbox-support-relay
PB  : bootstrap-goal, assessment-mode(no-archive), procedure, verification-suite,
      bootstrap-phases, initial-file-purposes(roadmap/deferred/eng-notes/handoff), existing-workflows
AP  : purpose, profile:local-relay, profile:patch-exchange, profile:remote-background,
      choosing/combining-profiles
CW  : purpose, inputs, authority/precedence, repo-bridge, package-inspection,
      target-inspection, dry-run-first, bootstrap, update, docs/handoff, support-relay,
      required-delivery
SP  : purpose, constraint-catalogue(1..5), upstream-feedback-packet-format,
      observed-constraint, evidence, suggested-change, reusable-vs-project-specific
GPT : feedback-style, primary-user
RM  : current-arc(objective/type/scope/completion-criteria), ordered-items(WMP-01..11),
      active-item, retained-context(prior arc), concurrent-arcs, planning-decisions,
      release-candidate-evidence
HO  : active-handoffs-index, per-handoff(state/engineering-context/delegation/
      activity-amendments/return-report)
EN  : invariants, tool-behavior, 2nd-project-readiness, versioning, knowledge-packaging,
      release-version-automation, focused-regressions, bootstrap/release-doc-contract,
      clean-RC-verification, desktop-visual-identity, custom-GPT-pilot-contract,
      bootstrap/managed-tool-updates, WMP-1.0-design-decisions
DN  : why-SteadyArc-exists, why-concurrent-arcs, why-design/eng-notes-separate,
      why-bash/PowerShell-parity-required
UP  : preferred-profile-by-task, commit/review-preferences, workflow-habits
DI  : (project-specific deferred items; see file directly — no stable topic list)
```

## 4. Question → doc shortcut (subset of IA's table, repeated here for single-file lookup)

```
"what may I do now?"            → HO(matching arc record) + human instruction
"what's active / next?"         → RM §Current arc / §Concurrent arcs
"what's durably engineering-true?" → EN
"what's durably product-true?"  → DN
"unrelated discovery, where?"   → DI
"how does this human collaborate?" → UP
"general SteadyArc rules?"      → WF
"install/update procedure?"     → PB
"tool behavior contract?"       → TL
"agent capability guidance?"    → AP
"Codex-specific operation?"     → CW (+AGENTS.md)
"Custom GPT-specific operation?" → GPT
"what does code actually do?"   → source/tests/scripts/validation evidence
```

## 5. Maintenance

Regenerate affected entries when a heading is added, renamed, or removed in any indexed doc, or when a new `.steadyarc/*.md`/`knowledge/*.md` file is created. This file is descriptive of current headings; it is not itself a place to record new rules — add those to the cited doc first, then reflect the pointer here.
