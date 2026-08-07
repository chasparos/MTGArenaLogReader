# Steady Arc Design Notes

## Deck Planner product intent

The Deck Planner is a responsive desktop workspace for discovering cards that are both available in MTG Arena and legal in a selected format, narrowing that catalog through structured and semantic filters, collecting candidate cards under consideration, and exporting authoritative card facts for deck-building assistance.

The experience should remain useful when collection ownership is unknown. Ownership-dependent controls and displays may appear only when Arena supplies an authoritative complete collection observation; deck contents, craftability, Scryfall metadata, cosmetics, and generic inventory records are not substitutes for ownership truth.

## Interaction direction

The planner is a Swing-component experience rather than a canvas/game-loop surface. It should support readable card presentation, responsive resizing and scrolling, keyboard-visible focus, clear active filter states, explicit loading/offline/error states, and direct selection/consideration gestures without blocking the EDT.

An existing Arena-exported deck can be used as a starting point for consideration. Import is additive candidate seeding rather than deck ownership inference: the current filters stay in place, duplicate copies collapse to candidate membership, and unresolved cards are surfaced to the user.

## AI assistance direction

Deck-building export is advisory. The payload supplies authoritative card rules and provenance-aware collection quantities, distinguishes unknown from known zero, and asks the model for strategic inference without allowing it to invent or substitute card facts.
