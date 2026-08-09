package app.deckplanner.candidate;

import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Resolves legal state, alternate printings, and a persisted favorite printing per logical card. */
public final class AlternateArtResolver {
    public record ArtSet(String identity, boolean legal, List<CardInfo> printings,
                         Optional<String> favoriteScryfallId, boolean complete) {
        private static boolean hasRenderableImage(CardInfo card) {
            return card != null && card.previewImageUrl() != null && !card.previewImageUrl().isBlank();
        }

        public CardInfo preferred() {
            if (favoriteScryfallId.isPresent()) {
                CardInfo matched = printings.stream()
                        .filter(Objects::nonNull)
                        .filter(card -> favoriteScryfallId.get().equals(card.getId()))
                        .sorted((left, right) -> Boolean.compare(
                                hasRenderableImage(right), hasRenderableImage(left)))
                        .findFirst().orElse(null);
                if (matched != null) return matched;
            }
            // Without an explicit favorite, keep the first usable local/cached printing stable.
            // Some persisted catalog entries may carry card metadata without image URIs; prefer
            // the first cached printing that can actually render before falling back to metadata-only.
            CardInfo renderable = printings.stream()
                    .filter(Objects::nonNull)
                    .filter(card -> card.previewImageUrl() != null && !card.previewImageUrl().isBlank())
                    .findFirst().orElse(null);
            return renderable != null ? renderable
                    : printings.stream().filter(Objects::nonNull).findFirst().orElse(null);
        }
    }

    private final CatalogFilterIndex index;
    private final CardNameRepository cards;
    private final PrintingPreferenceRepository preferences;
    private final ConcurrentMap<String, List<CardInfo>> enrichedPrintings = new ConcurrentHashMap<>();

    public AlternateArtResolver(CatalogFilterIndex index, CardNameRepository cards,
                                PrintingPreferenceRepository preferences) {
        this.index = Objects.requireNonNull(index);
        this.cards = Objects.requireNonNull(cards);
        this.preferences = Objects.requireNonNull(preferences);
    }

    /** Cache-only state used by normal rendering; never performs enrichment. */
    public ArtSet resolveCached(String identity) {
        boolean legal = index.cards().stream().anyMatch(card -> card.group().identity().equals(identity));
        List<CardInfo> enriched = enrichedPrintings.get(identity);
        List<CardInfo> printings = enriched == null ? cards.cachedPrintings(identity) : enriched;
        return new ArtSet(identity, legal, List.copyOf(printings), preferences.favorite(identity),
                enriched != null);
    }

    /** Explicit user-requested enrichment used only by the catalog art chooser. */
    public ArtSet resolve(String identity) {
        boolean legal = index.cards().stream().anyMatch(card -> card.group().identity().equals(identity));
        List<CardInfo> printings = List.copyOf(cards.printings(identity));
        enrichedPrintings.put(identity, printings);
        return new ArtSet(identity, legal, printings, preferences.favorite(identity), true);
    }

    public void favorite(String identity, CardInfo printing) {
        if (printing != null && printing.getId() != null) preferences.setFavorite(identity, printing.getId());
    }
}
