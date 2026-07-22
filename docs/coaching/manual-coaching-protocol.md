# Manual Coaching Protocol

## Purpose

Manual coaching is an explicit copy/paste workflow. It lets the user evaluate the reconstruction and response format before any API integration or token spending.

The application does not contact an AI service when these controls are used:

1. Select match, game, turn, or selected-turn context in the coaching replay.
2. Enter or choose a question.
3. Use **Translate to AI-speak into clipboard**.
4. Paste the request into the chosen AI client.
5. Copy the response.
6. Use **Translate clipboard from AI-speak**.

The question and imported response are persisted as conversation messages. The generated request itself is not persisted as a second source of truth.

## Protocol template

The request contract lives at:

```text
src/main/resources/coach/protocols/coach_request.txt
```

`ManualCoachingPromptBuilder` loads this classpath resource and substitutes exactly two properties:

```text
${question}
${context}
```

Both properties are required. A missing resource or property is a startup/use error rather than a reason to silently fall back to a hard-coded prompt. Protocol wording can therefore evolve independently from Java code while remaining versioned in source control.

The coaching request protocol and reconstruction protocol have separate versions:

```text
MTGA_COACH_REQUEST_V1
MTGA_MATCH_V4
```

Changing coaching instructions does not require changing the match reconstruction schema.

## Context scopes

The persisted match reconstruction is canonical. Scoped prompts slice that reconstruction instead of rebuilding it from replay display text.

- **Match** includes the complete reconstruction.
- **Game** keeps the protocol header and dictionaries, then includes only the selected game.
- **Turn** and **selected turns** keep the header, dictionaries, game preamble, opening hand, and selected turn blocks.

Slicing preserves stable references such as `[T10]`, `[E101]`, `[A90]`, `[c3]`, and `[c3#194]`.

The coaching layer must not become a parallel reconstruction engine. Missing graveyard, exile, hand-content, library-count, or legal-action information should be added to canonical reconstructed state first and then exported.

## Response references

AI responses are stored exactly as imported. Display translation is non-destructive.

The first resolver recognizes card protocol tokens using the reconstruction's `CARD` dictionary:

```text
[c3]      -> Osteomancer Adept [c3]
[c3#194]  -> Osteomancer Adept [c3#194]
```

Unknown references remain unchanged. Keeping the token visible preserves traceability and leaves a clean migration path to clickable chips and replay navigation.

Event, turn, state, action, decision, life, and result references are intentionally not expanded yet. Their useful presentation requires a richer component than plain `JTextArea`; replacing them with long event prose would reduce readability.

## Observed legality and actions from other zones

The reconstruction records what happened, not every action that was legal. Coaching must not infer an unchosen cast from a graveyard, exile, command zone, or other zone merely because card text appears to permit it.

The preferred future source is Arena's offered action set. Such observations belong to the priority window and exact game state in which Arena exposed them:

```text
offered action lifetime = priority window / observed state
```

They must not become enduring card properties. Complex permissions such as Osteomancer Adept should be represented from Arena-observed offered actions rather than by introducing a partial Magic rules engine.

Opponent options may be reported only when public information or Arena output makes them known. Hidden-hand possibilities remain unknown.

## Architectural constraints

- Persisted reconstruction remains the source of truth.
- Prompt construction belongs to the coaching application layer.
- Swing consumes resolved display text but does not parse raw GRE messages.
- Imported responses remain immutable audit data; presentation resolution happens when rendering.
- Reconstruction state must have the same lifetime as the information it represents.
