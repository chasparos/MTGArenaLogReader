# Open Issues

This document tracks known reconstruction and presentation gaps that are significant enough to preserve across development sessions.

## Room cards are reconstructed as separate unknown permanents

**Status:** Open  
**Area:** Semantic reconstruction  
**Observed in:** Turn 9 of the Inklistrad sample match

Arena reports a Room card together with additional object and card-group identifiers for its two halves. The current reconstruction resolves the combined Room card but leaves the half objects as entries such as `Unknown blue Room enchantment [Arena #92136]`.

Consequences:

- the two Room halves cannot be named reliably;
- the unopened half cannot be shown as **Locked**;
- the combined Room and its half objects appear as separate battlefield permanents;
- exports and coaching views receive an inaccurate battlefield model.

### Intended direction

Represent one reconstructed Room permanent with semantic state for both halves:

- identity of the left and right halves;
- which half or halves are unlocked;
- lock state for each unopened half.

The Arena half identifiers should be retained as evidence and resolved through card metadata where possible. This belongs in semantic reconstruction rather than in the exporter or view layer, so the replay, coaching export, and future analysis all consume the same state.

The lifetime of this state is the lifetime of the Room permanent. Transient cast or unlock choices should remain decision-scoped observations.
