# Arena log regression fixtures

`multigame.log` is the representative end-to-end fixture for the replay pipeline.

It contains:

- two best-of-three matches;
- three games in each match;
- six routed game sessions in total;
- game-complete records for every game;
- match-complete records for both matches.

The fixture is intentionally replayed without Scryfall or the persistent card cache.
Tests should distinguish Arena observations from reconstructed and inferred behavior.

Personal player names, user identifiers, and session identifiers have been replaced with
stable test values. Match identifiers are retained because they are part of the routing
assertions.

SHA-256: `fadb965d22b8e317e130c58b659e58d20da61bf1970245358dfb5ee8c6217176`

Treat this fixture as immutable. Add a new fixture rather than editing it when a new edge
case cannot be represented by focused synthetic records.
