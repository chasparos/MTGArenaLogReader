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
