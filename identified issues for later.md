# Identified Issues for Later

These are intentional deferred improvements. They are not current roadmap items and should only be addressed after the current semantic validation work is complete.

## Canonical turn-delta counter formatting

Current format:

```text
TD#3 p1 counters=c1#100:+1/+1+2
```

Although machine-readable, the boundary between the counter type and the signed delta is difficult for humans to parse. A reader has to infer that `+1/+1` is the counter name and `+2` is the change.

Possible improvements:

- `c1#100:+1/+1:+2`
- `c1#100:+1/+1=+2`
- `c1#100:+1/+1(add=2)`

Goals:

- Preserve compactness.
- Make the counter type and delta visually distinct.
- Keep state and delta formats intentionally different, for example:
  - State: `[+1/+1=2]`
  - Delta: `+1/+1:+2`

Revisit this only as an explicit canonical-format revision with exporter regression coverage.

## Post-validation playtest and replay-completeness arc

After the current focused semantic-validation arc is complete, pause roadmap implementation and evaluate the system through real games before selecting the next development arc.

Evaluation should include:

- Play several representative Arena games and inspect the reconstructed replay against what actually happened.
- Review the compact AI-Speak export in the coaching chat that motivated the current validation work.
- Record confusing, missing, redundant, or misleading export details as concrete examples rather than immediately redesigning the schema.
- Make only small evidence-backed adjustments during this playtest period.

A likely later arc is **replay completeness**, expanding beyond the currently validated core semantics. Candidate areas include:

- replacement effects;
- delayed triggers;
- linked abilities;
- transform and double-faced-card handling;
- meld and split-card behavior where Arena exposes it;
- hidden-information reconstruction improvements;
- clearer confidence for inferred events;
- a growing regression corpus built from real-game oddities.

Do not begin that arc automatically. First use the playtest findings to decide which gaps are strategically important and which are merely theoretical edge cases.
