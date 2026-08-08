package app.deckplanner.candidate;

import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Resolves legal state, alternate printings, and a persisted favorite printing per logical card. */
public final class AlternateArtResolver {
    public record ArtSet(String identity, boolean legal, List<CardInfo> printings,
                         Optional<String> favoriteScryfallId) {
        public CardInfo preferred() {
            if (favoriteScryfallId.isPresent()) {
                for (CardInfo card : printings) {
                    if (card != null && favoriteScryfallId.get().equals(card.getId())) return card;
                }
            }
            return printings.stream().filter(Objects::nonNull).max(Comparator
                    .comparing((CardInfo card) -> card.getArenaId() != null)
                    .thenComparing(card -> card.getReleasedAt() == null ? "" : card.getReleasedAt())
                    .thenComparing(card -> card.getId() == null ? "" : card.getId()))
                    .orElse(null);
        }
    }

    private final CatalogFilterIndex index;
    private final CardNameRepository cards;
    private final PrintingPreferenceRepository preferences;

    public AlternateArtResolver(CatalogFilterIndex index, CardNameRepository cards,
                                PrintingPreferenceRepository preferences) {
        this.index = Objects.requireNonNull(index);
        this.cards = Objects.requireNonNull(cards);
        this.preferences = Objects.requireNonNull(preferences);
    }

    public ArtSet resolve(String identity) {
        boolean legal = index.cards().stream().anyMatch(card -> card.group().identity().equals(identity));
        List<CardInfo> printings = cards.printings(identity);
        return new ArtSet(identity, legal, List.copyOf(printings), preferences.favorite(identity));
    }

    public void favorite(String identity, CardInfo printing) {
        if (printing != null && printing.getId() != null) preferences.setFavorite(identity, printing.getId());
    }
}
