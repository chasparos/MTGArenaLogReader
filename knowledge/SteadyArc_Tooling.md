# Steady Arc Tooling

## Canonical support paths

Steady Arc uses a dedicated directory to avoid interfering with project documentation or another agent's workflow:

- `.steadyarc/roadmap.md`
- `.steadyarc/deferred-issues.md`
- `.steadyarc/engineering-notes.md`
- `.steadyarc/handoff.md`

Knowledge and reusable templates may live outside `.steadyarc/`, but active project state belongs there.

Agent adapters may add repository-native entry points or bridge files, such as an agent instruction file or a short `.steadyarc/hello-<agent>.md`. Those are adapter artifacts, not canonical project memory, and their installation rules belong to the corresponding adapter.

## Maven Wrapper requirement

Repositories using `PatchSequence.ps1` must commit the Maven Wrapper:

- `mvnw`
- `mvnw.cmd`
- `.mvn/wrapper/maven-wrapper.properties`

Sandboxed agents may be unable to generate or download the wrapper. During bootstrap, instruct the human to create it from an environment with Maven available, normally with:

```text
mvn wrapper:wrapper
```

The wrapper files must then be committed before relying on the patch sequence. Do not pretend the wrapper exists or silently substitute a globally installed Maven. Routine Windows commands should use `mvnw.cmd`; a global Maven installation is only a bootstrap aid for creating a missing wrapper.

## Cross-platform support contract

Steady Arc distinguishes the repository build launcher from optional operating-system-specific workflow tools.

- A Maven repository normally commits both `mvnw.cmd` and `mvnw`.
- `mvnw.cmd` is the authoritative launcher for Windows PowerShell tooling such as `PatchSequence.ps1`, `RunWidget.ps1`, and release scripts.
- The committed `mvnw` must use Unix-compatible line endings and retain executable permission so Linux and macOS agents or CI can run the project build without rewriting the wrapper.
- PowerShell workflow automation may be declared Windows-only when that is an explicit project constraint recorded in `.steadyarc/engineering-notes.md`.
- A Windows-only automation decision does not justify a broken Unix Maven Wrapper. If Unix build execution is intentionally unsupported, record the reason, affected validation, and human-side command rather than allowing accidental CRLF or permission defects.
- When `pwsh` is available on a non-Windows host, PowerShell scripts may be smoke-tested there, but success under PowerShell Core does not prove Windows PowerShell 5.1 behavior and vice versa.

Bootstrap and patch delivery must report separately whether the repository build launcher and the operating-system-specific workflow tooling were exercised.

## NewPatch.ps1

`NewPatch.ps1` is the repository-supported patch-production tool. It emits Git-native `--binary --full-index` patches and avoids text normalization.

It is optional producer-side tooling. Users following the normal snapshot → agent-produced patch → `PatchSequence.ps1` loop do not invoke it. Use it only when changes already exist in a working tree or in paired baseline/modified directories and must be converted into a transportable patch.

From a Git working tree:

```powershell
.\NewPatch.ps1 -OutputPath "change.patch"
```

From snapshot directories without `.git` metadata:

```powershell
.\NewPatch.ps1 `
    -OutputPath "change.patch" `
    -BaselineDirectory "C:\path\to\baseline" `
    -ModifiedDirectory "C:\path\to\modified"
```

The directory fallback copies bytes into a temporary Git repository, commits the baseline, overlays the modified tree, and produces the patch with Git. It must not normalize line endings or decode and rewrite repository files. The output still requires the repository's normal application and validation path.

## PatchSequence.ps1

The helper supports two paths:

- apply a unified patch, then test, commit, and snapshot;
- finalize manual edits by omitting the patch argument.

Its responsibilities are:

1. verify the Maven Wrapper exists;
2. resolve a patch from an explicit path or the user's Downloads directory;
3. run `git apply --check`;
4. apply and archive the patch;
5. execute the Maven test suite through `mvnw.cmd`;
6. write `latest test results.log`;
7. stage and commit repository changes;
8. create `latest snapshot.zip` from `HEAD`;
9. return the test exit code.

Do not embed project-specific paths or names in the reusable source.

## Bootstrap and tool updates

Steady Arc releases include a repository-owned bootstrap/update executable plus a versioned manifest. The tool supports three public operations:

- **Install** — inspect a target repository and add missing compatible Steady Arc tooling and initial workflow files.
- **Update** — replace only managed tool files that still match the previously installed release; report locally modified files as conflicts requiring explicit human approval.
- **Dry run** — perform the same discovery, compatibility checks, destination planning, drift classification, and conflict reporting without writing files.

The manifest is the machine-readable distribution contract. It records the release version, managed artifact identifiers, packaged source paths, destination rules, and content hashes. Installed repositories retain enough manifest state to distinguish an unchanged older tool from a project-owned modification.

Managed files are classified before mutation:

1. **Absent** — eligible for installation.
2. **Unchanged managed version** — eligible for update.
3. **Locally modified** — never overwritten unattended.
4. **Unsupported or incompatible** — reported with the failed compatibility rule and no write.

The updater must not treat `.steadyarc/` project memory as replaceable tooling. It may create missing initial workflow files, but populated roadmap, notes, deferred issues, and handoff state remain project-owned.

Dry-run output must be sufficient to perform the same operation manually. Manual placement uses the exact artifacts and destination rules from the release manifest rather than a separately maintained copy procedure.

Before writing, the updater creates a rollback set for every destination it may touch. If any write, integration check, or post-write validation fails, it restores all touched destinations. Rollback is scoped to the updater's own changes and must not reset unrelated repository work.

Tool updates are version transitions, not blind copies. A new Steady Arc release may add, replace, or retire managed artifacts through explicit manifest entries. Retired files are reported first and removed only with explicit approval when they are unchanged from their managed version.

## BootstrapSteadyArc.ps1 and updater CLI

The release archive carries `bootstrap/BootstrapSteadyArc.ps1`, `bootstrap/steady-arc-bootstrap.jar`, the filtered manifest, and the exact managed artifacts. A Custom GPT must receive that verified archive as a conversation attachment before bootstrapping an uninitialized repository. If it is absent, request it and stop rather than reconstructing managed artifacts from Knowledge prose.

For the supported 1.0 human trust boundary, launch the Bootstrap Assistant:

```powershell
.\bootstrap\BootstrapSteadyArc.ps1 -Ui -Target C:\path\to\repository
```

It discovers a usable JDK and verifies Git cleanliness, Maven Wrapper structure,
release hashes, and occupied destinations. It shows the complete managed plan,
requires a separate human approval for every locally modified destination, and
enables installation only after preflight and approval succeed. Installation is
transactional and re-verifies the resulting managed hashes.

The mechanical install manages `PatchSequence.ps1`, `NewPatch.ps1`, and the
complete Maven Wrapper. Widget, relay, dependency, build-plugin, and project
memory integration remain inputs to the first reviewed project-specific patch;
copying those sources alone could leave an existing project unbuildable.
After installation, the assistant enables `RUN FIRST PATCH` only while Git
reports no changes outside the installed managed files and updater state. The
human supplies the reviewed patch file and commit message; they are passed as
separate process arguments to repository-owned `PatchSequence.ps1`, whose live
merged output and final exit code are displayed without interpretation.
Fully agent-driven bootstrap remains deferred. The CLI dry run remains available:

```powershell
.\bootstrap\BootstrapSteadyArc.ps1 -Operation dry-run -Target C:\path\to\repository
```

Install missing artifacts:

```powershell
.\bootstrap\BootstrapSteadyArc.ps1 -Operation install -Target C:\path\to\repository
```

Update unchanged managed artifacts:

```powershell
.\bootstrap\BootstrapSteadyArc.ps1 -Operation update -Target C:\path\to\repository
```

A locally modified managed file blocks writes until the human chooses either
`REPLACE` or `KEEP`. The CLI equivalents are `-ApproveModified` and
`-KeepModified`; kept project-owned files are neither overwritten nor recorded
as managed. The plan output names each packaged source and target destination,
so the same manifest supports manual placement.

The updater records managed hashes in `.steadyarc/managed-tools.properties`. That file is updater state, not project roadmap memory. It never authorizes replacement of populated roadmap, notes, deferred issues, or handoff files.

## Payload button

`ChatGptPayloadButton` is the default payload helper for supported Maven/Java bootstraps. Omit it only after an explicit human opt-out or when a documented repository constraint makes integration incompatible.

The class name is an implemented compatibility identifier, not a dependency of the workflow policy on a particular assistant. Core contracts describe its payload-helper behavior; agent adapters describe how their tool surface consumes that payload.

The distribution carries the canonical reusable artifacts at:

- `bootstrap/payload-helper/src/main/java/devtools/ChatGptPayloadButton.java`
- `bootstrap/payload-helper/RunWidget.ps1`

Bootstrap work must copy or adapt those artifacts rather than reconstructing the helper from this description. The target Maven build must compile the source and provide the repository-local launch path used by `RunWidget.ps1`.

From the repository root, it locates the most recently modified files matching:

- `latest snapshot*.zip`
- `latest test results*.log`

Dragging the center control transfers both files as an operating-system file list. Dragging the surrounding panel moves the undecorated window.

A repository-aware widget may also expose two compact tool buttons:

- **Run clipboard command** — after an explicit click, read the current clipboard text and run it as a PowerShell command with the repository root as the working directory.
- **Open terminal** — open a PowerShell terminal with the repository root as the working directory.

Use dependency-free chevron glyphs or bundled project-owned artwork. Do not fetch icons at runtime. Clipboard content must never execute on startup, on focus, or merely because the clipboard changes. Read it only after the run control is explicitly clicked. Empty or non-text clipboard content should produce a clear, non-destructive message.

Commands containing `PatchSequence.ps1` may run as captured processes. Other commands should open in a separate visible terminal so their native interaction remains available. Both paths start at the resolved repository root. Captured long-running commands provide immediate, non-blocking feedback by streaming merged stdout and stderr into a larger translucent undecorated window with wrapped lines and no horizontal scrollbar. The widget does not parse command output into a second success or failure narrative; it shows the process exit code in the same window, keeps the final raw output visible for five seconds, and then fades the window away. Clicking or scrolling pins the output for inspection; after the pinned window loses focus, it waits five seconds before fading. Its status strip is a drag handle whose last visible screen location is persisted across Widget launches. Escape starts fading immediately without terminating the running command.

Any execution-policy override must be process-scoped. Passing `-ExecutionPolicy Bypass` to a widget-created PowerShell process is acceptable; changing CurrentUser or LocalMachine execution policy is not.

## RunWidget.ps1

When the payload helper is bootstrapped, include a project-agnostic `RunWidget.ps1` launcher. It should:

1. resolve its own repository root;
2. verify the repository-local Maven Wrapper exists;
3. launch the payload widget through that wrapper;
4. forward documented optional arguments such as a repository-label override;
5. require a complete JDK by validating both `bin\java.exe` and `bin\javac.exe`;
6. resolve Java in order from a valid existing `JAVA_HOME`, IntelliJ `.idea/misc.xml` plus registered SDK metadata, `java.exe` on `PATH`, then common JDK installation locations;
7. export the resolved `JAVA_HOME` and JDK `bin` directory only to the Maven Wrapper process environment;
8. fail with actionable setup instructions when Java or wrapper prerequisites remain unavailable.

The launcher does not permanently configure the user's shell. Direct calls to `mvnw.cmd`, `PatchSequence.ps1`, or release scripts still depend on the invoking shell's Java environment.

The helper is transport tooling, not application logic. A target project may place it under an existing development-tools package or keep it in a small auxiliary Maven module.

## Validation status contract

Tool output and delivery reports keep these statuses separate:

1. **Patch applicability** — `git apply --check` or the repository tool's equivalent preflight.
2. **Project validation** — the supported build and relevant test command.
3. **Runtime smoke validation** — direct execution of the changed user-facing tool or behavior in its intended environment.

A status is `passed`, `failed`, or `not performed`. Include the actual command or evidence. Static checks may support a `not performed` runtime status, but never convert it to `passed`.


## Static PowerShell evidence

When PowerShell execution is unavailable, static evidence may reduce uncertainty but never counts as runtime smoke validation.

Acceptable static evidence, reported precisely, includes:

- parser validation performed by a compatible PowerShell parser without invoking the script's operational path;
- focused source-contract tests for quoting, argument forwarding, working-directory selection, process boundaries, failure markers, and required safety guards;
- package and manifest assertions proving that the intended script is transported under the expected path and hash;
- byte-level or diff inspection confirming that edits preserve line endings and do not rewrite unrelated script content;
- comparison with a previously successful runtime result for the same unchanged script hash, identified as historical evidence rather than a current smoke test.

Static checks must identify the PowerShell edition or parser version when relevant. Windows PowerShell 5.1 and PowerShell 7 parser results are not interchangeable proof of runtime compatibility.

The following do not qualify as runtime validation:

- reading the script;
- compiling adjacent Java code;
- matching expected strings;
- a successful Maven test suite that never invokes the script;
- parser success alone;
- successful execution of a different script or a different script hash.

Report the runtime-smoke axis as `not performed` when the target script was not executed through the behavior under review. State which static checks passed, what they cover, and what remains unverified. If the missing runtime evidence blocks safe delivery, record the required human-side smoke command instead of promoting static evidence.

## Snapshot contract

The preferred handoff payload contains all three files generated by `PatchSequence.ps1`:

- `latest snapshot.zip`
- `latest test results.log`
- `latest snapshot manifest.json`

The manifest is companion evidence; it does not replace the snapshot as repository truth or the test log as validation output. It records the committed HEAD and branch, post-commit working-tree status, the archive production and ignored-file policy, build command, test summary, runtime versions, archive and log hashes, archive size, and a bounded inventory of the largest tracked files.

The snapshot remains a `git archive` of committed `HEAD`. It intentionally excludes `.git` metadata, ignored files, and untracked files. The manifest makes those boundaries explicit rather than changing the authoritative ZIP format.

The payload widget transfers all three files together. File numbering added by an upload client does not change semantic identity. Generated payloads should normally be ignored by Git.

### Continuing from the payload

On receipt, verify the archive and test-log hashes before using their contents as one transfer set. Then inspect the snapshot's handoff, roadmap, and repository-native instructions and compare them with the manifest commit, dirty-state disclosure, build command, and validation result.

The artifacts divide responsibility:

- the snapshot supplies committed repository state;
- the test log supplies full validation output;
- the manifest binds those files to provenance, summary, runtime, and omitted-working-tree evidence;
- `.steadyarc/handoff.md` inside the snapshot supplies delegated scope, ownership, return condition, and safe next action.

If the handoff is inactive, returned, or closed, “look this over and continue” permits assessment and a proposed next action, but implementation still requires a new explicit delegation. If it is active, continue only within its named receiver, scope, and constraints. Report stale or conflicting handoff provenance before changing files.

When a returned handoff arrives in an archive whose hash and commit match the manifest and whose matching test log records the stated validation, the payload itself proves that PatchSequence finalization already occurred. Follow the returned owner and post-receipt next action; do not rerun a historical finalization command merely because an earlier activity amendment mentions it.

## Tool isolation

Do not merge Steady Arc scripts or core documents into agent-specific configuration. Core tools and adapter artifacts may reference the same repository, but each remains independently understandable and removable.

## Sandbox support relay

The optional Widget support relay is a human-session execution bridge for a fixed operation catalogue. It is not a general shell and does not convert sandbox failure into general repository authority.

The first supported operations are `git-status`, `git-diff-check`, and `maven-test`. The client submits only an operation ID, request UUID, and short-lived session token. The Widget resolves the repository root and assembles the executable, complete argument list, working directory, timeout, and output handling. Unknown fields, unsupported operations, stale tokens, and unsafe batch-launcher paths are rejected.

The transport uses `.steadyarc/relay/` with separate request and result directories. A client writes a temporary properties file and renames it to publish the complete request. The relay claims a request by atomic rename before parsing so a request cannot execute twice. Output is bounded, commands time out, and descendant processes are terminated with the parent.

On Windows, relay startup is fail-closed unless ACLs restrict the transport to the Widget owner and the configured sandbox account. The session is off by default and uses a 30-minute sliding idle timeout. A fully parsed request with a matching file ID, supported operation, and valid session token renews the lease before execution; rejected traffic never renews it. Stopping the relay or closing the Widget invalidates the session immediately. This operating-system identity check establishes that the request came from a process under the sandbox account; it cannot distinguish one model or application from another process running as that same account.

Relay start and stop use non-blocking fading toasts. On start, the Widget copies repository, channel path, idle-timeout behavior, current expiry, supported operations, and the client command to the clipboard so the human can paste the channel description into the receiving engineering agent. The session marker republishes the renewed expiry atomically after accepted use. Clipboard text must never contain the session token.

`InvokeSteadyArcRelay.ps1` is the repository client. Its operation parameter is a fixed validation set and it accepts no command string or additional process arguments. `PatchSequence.ps1`, bootstrap mutation, release operations, and arbitrary commands remain outside the initial relay contract.
