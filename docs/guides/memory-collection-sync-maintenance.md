# Memory collection synchronization maintenance

## Supported evidence baseline

The scanner was accepted on Windows against Arena client `2026.61.30.13636` (Unity player `0.1.13636.1303683`) using `Raw_CardDatabase_7bc4fb29468604399aa7f1c7afb07405.mtga`. That catalog exposed 26,126 valid Arena IDs. These values describe the tested baseline, not a promise that future Arena builds retain the same memory layout.

## Safety behavior

Collection synchronization is fail-closed. Arena not running, access denied, client exit, partial reads, cancellation, invalid verified-card input, insufficient exact confirmations, ambiguous candidates, or changed layout must not replace the last complete H2 ownership publication. The rest of the application remains usable when synchronization is unavailable.

The scanner owns its Windows access, evidence rules, verified-card state, diagnostics, and H2 ownership table. Application code consumes only the neutral batch ownership lookup and update-session protocol. Do not join this table to the independent Player.log-observation collection schema.

## Normal diagnostic sequence

1. Confirm the production wizard can locate the Arena installation and local card database.
2. Start Arena and navigate to **Decks**, then **Collection** when prompted.
3. Confirm the selected card printings and owned quantities precisely.
4. Inspect progress for process acquisition, readable-region discovery, candidate consensus, and publication outcome.
5. Treat any result other than complete eligible consensus as non-publishing. Preserve the prior ownership snapshot.

## After an Arena update

1. Rebuild the known-ID catalog from the newest `MTGA_Data/Downloads/Raw/Raw_CardDatabase_*.mtga` file.
2. Run the harness in evidence mode with at least two currently verified card printings; prefer previously verified full playsets, but never assume they remain valid without an exact runtime match.
3. Compare candidate layouts, known-ID ratios, quantities, duplicates, independent-window support, and generation consensus with the accepted fixtures and thresholds.
4. If the layout or semantics differ, keep publication disabled while adding deterministic fixtures for the new representation. Never loosen ambiguity, exact-confirmation, quantity, or atomic-publication gates merely to make a scan pass.
5. Re-establish semantics with controlled before/after evidence when necessary, then run the full Maven suite and a real-client harness review before accepting the new client baseline.
6. Record the client/player identity, catalog filename and ID count, observed layout, validation results, and human evidence in the roadmap, handoff, and engineering notes.

## Recovery guidance

If a scan fails after a client update, the user may retry after opening Arena's Collection screen and confirming the requested cards. Repeated ambiguity or missing candidates indicates version drift requiring engineering review, not a reason to clear or overwrite ownership. Existing published ownership remains the safest available state.
