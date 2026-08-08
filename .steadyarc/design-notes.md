# Steady Arc Design Notes

## Deck Planner product intent

The Deck Planner is a responsive desktop workspace for discovering cards that are both available in MTG Arena and legal in a selected format, narrowing that catalog through structured and semantic filters, collecting candidate cards, and exporting authoritative card facts for deck-building assistance.

The experience should remain useful when collection ownership is unknown. Ownership-dependent controls and displays may appear only when Arena supplies an authoritative complete collection observation; deck contents, craftability, Scryfall metadata, cosmetics, and generic inventory records are not substitutes for ownership truth.

## Interaction direction

The planner is a Swing-component experience rather than a canvas/game-loop surface. It should support readable card presentation, responsive resizing and scrolling, keyboard-visible focus, clear active filter states, explicit loading/offline/error states, and direct selection/candidate gestures without blocking the EDT.

An existing Arena-exported deck can be used as a starting point for a Candidate Set. Import is additive candidate seeding rather than deck ownership inference: the current filters stay in place, duplicate copies collapse to candidate membership, and unresolved cards are surfaced to the user.

## AI assistance direction

Deck-building export is advisory. The payload supplies authoritative card rules and provenance-aware collection quantities, distinguishes unknown from known zero, and asks the model for strategic inference without allowing it to invent or substitute card facts.

## DP-06 candidate-workspace feedback (2026-08-07)

Human acceptance must exercise the planner against real current Standard cards, not synthetic stand-ins. The preview should obtain a bounded real-card subset through the same Scryfall/catalog/cache pipeline used by the product so visual and interaction feedback covers representative names, mana costs, card types, faces, images, and stable identities.

The candidate workspace is a lightweight candidate set rather than an ownership view. While Arena collection publication remains unavailable under `SA-MTGA-DEF-003`, candidate rows should not show simulated or inferred owned quantities.

Candidate ordering has two intentional modes: persistent manual order, primarily manipulated by drag and drop, and an explicit `Use normal MTG sorting` action. Normal MTG sorting should reuse one shared ordering rule already represented in the Draft Assistant / Deck Tracker family rather than allowing planner-specific ordering to drift.

Card presentation in the candidate list should reuse the replay card-chip visual language. This is a shared presentation primitive, not a near-copy created only for Deck Planner.

Deck import is a planner entry path rather than only a text parser. The import surface should offer known Arena decks already observed by the local deck subsystem and also accept pasted Arena-export text. Name resolution belongs behind a common repository that prefers the current local catalog/cache and uses exact-name Scryfall lookup only as a metadata fallback. Scryfall resolution never proves ownership.

The candidate-only catalog view is an additive filter layer. It must leave the user's structured/tag filter state intact. Selecting a candidate may activate that layer for focused browsing; a visible filter-panel control turns the layer on or off explicitly.

## DP-06 real-card review follow-up (2026-08-08)

Human acceptance is primarily a product/design review surface. The visible checklist should group subjective UX questions rather than ask the human to manually re-prove automated source, legality, persistence, or regression contracts.

The candidate workspace should read as a category workspace, not a conventional list. Its default visual groups are Creatures, Noncreatures, and Nonbasic Lands; cards wrap into rows, use larger replay-style chips, and expose precise insertion feedback while dragging. The component model should continue to permit later planner-owned semantic groups such as card advantage, recursion, or win conditions without changing the persisted candidate identity/order contract.

Real-card breadth is part of acceptance quality. The preview should use the full current Arena-available Standard catalog and the same persistent metadata/image caches as the application. Import should favor already-observed local card metadata, especially for decks the application has seen before, and only then use Scryfall as a metadata fallback.

Subtype/tribal filtering is expected to need a dedicated long-list interaction rather than being dropped into the existing compact tag cloud unchanged. That design is deferred from the current candidate-workspace pass.



## DP-06 candidate-workspace iteration 2 (2026-08-08)

“Candidate” and “Candidate Set” are the canonical product terms. Current code and documentation should use them; historical handoff records remain unchanged evidence.

The candidate workspace is a category board rather than a list. Selection should hug the replay-chip geometry, categories must be visible/reorderable/removable, and later semantic groupings are first-class product intent rather than decoration. Removing a category never removes cards: its members move to an implicit `Uncategorized` group.

The catalog and candidate board should share Magic-aware ordering and drag semantics. Catalog cards are intentionally larger for human reading, selected catalog cards use a golden outline in dark mode, and cross-surface drag/drop should eventually use the replay-chip painter for its drag image.

Logical candidate identity remains separate from printing/art choice. Alternate printings may be browsed and one art may be favored for presentation without duplicating the logical candidate. A known card that is outside the selected legal format is still resolvable and should be marked Illegal rather than treated as missing.


## Candidate categories and Candidate Sets

Candidate categories are planner organization, not card facts or ownership. Category order is user-controlled. Removing a category must preserve its cards by moving them into an implicit `Uncategorized` category; `Uncategorized` should disappear when empty. Named Candidate Sets preserve an ordered candidate membership plus the planner category organization so a human can save and revisit alternative deck-building directions without treating those sets as observed Arena decks.
