package app.deckplanner.consideration;

import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.deck.persistence.DeckCache;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only adapter from the observed Arena deck cache to Deck Planner import choices. */
public final class DeckCacheKnownArenaDeckSource implements KnownArenaDeckSource {
    private final DeckCache cache;
    private final int limit;

    public DeckCacheKnownArenaDeckSource(DeckCache cache, int limit) {
        this.cache = Objects.requireNonNull(cache);
        this.limit = limit;
    }

    @Override public List<KnownArenaDeck> list() {
        if (limit <= 0) return List.of();
        return cache.recent(limit).stream()
                .map(deck -> new KnownArenaDeck(deck.deckId(), deck.name(), toArenaText(deck)))
                .toList();
    }

    private static String toArenaText(CachedDeck deck) {
        StringBuilder out = new StringBuilder();
        appendSection(out, "Deck", deck.mainDeck());
        appendSection(out, "Sideboard", deck.sideboard());
        appendSection(out, "Commander", deck.commandZone());
        appendSection(out, "Companion", deck.companions());
        return out.toString().stripTrailing();
    }

    private static void appendSection(StringBuilder out, String heading, List<DeckEntry> entries) {
        if (entries == null) return;
        List<String> lines = new ArrayList<>();
        for (DeckEntry entry : entries) {
            if (entry == null || entry.quantity() <= 0 || entry.card() == null
                    || entry.card().getName() == null || entry.card().getName().isBlank()) continue;
            lines.add(entry.quantity() + " " + entry.card().getName());
        }
        if (lines.isEmpty()) return;
        if (!out.isEmpty()) out.append("\n\n");
        out.append(heading).append('\n');
        for (String line : lines) out.append(line).append('\n');
    }
}
