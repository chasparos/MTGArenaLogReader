package app.deckplanner.consideration;

import app.deckplanner.catalog.CatalogCardIdentity;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;

import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Local-first card-name resolution for deck import, with an optional exact-name fallback. */
public final class CardNameRepository {
    public record Resolution(String identity, CardInfo card, boolean fallback) { }

    private final CatalogFilterIndex index;
    private final Function<String, Optional<CardInfo>> exactNameFallback;

    public CardNameRepository(CatalogFilterIndex index,
                              Function<String, Optional<CardInfo>> exactNameFallback) {
        this.index = Objects.requireNonNull(index);
        this.exactNameFallback = Objects.requireNonNull(exactNameFallback);
    }

    public static CardNameRepository local(CatalogFilterIndex index) {
        return new CardNameRepository(index, ignored -> Optional.empty());
    }

    public Optional<Resolution> resolve(String name, String setCode, String collectorNumber) {
        if (name == null || name.isBlank()) return Optional.empty();
        String wantedName = name.strip();
        String wantedSet = normalize(setCode);
        String wantedCollector = normalize(collectorNumber);

        CardInfo nameMatch = null;
        for (var indexed : index.cards()) {
            for (CardInfo card : indexed.group().printings()) {
                if (card == null || card.getName() == null
                        || !card.getName().equalsIgnoreCase(wantedName)) continue;
                if (nameMatch == null) nameMatch = card;
                if (matches(card.getSet(), wantedSet) && matches(card.getCollectorNumber(), wantedCollector)) {
                    return Optional.of(localResolution(card));
                }
            }
        }
        if (nameMatch != null) return Optional.of(localResolution(nameMatch));

        return exactNameFallback.apply(wantedName)
                .filter(Objects::nonNull)
                .map(card -> new Resolution(CatalogCardIdentity.of(card), card, true));
    }

    private Resolution localResolution(CardInfo card) {
        return new Resolution(CatalogCardIdentity.of(card), card, false);
    }

    private static boolean matches(String actual, String wanted) {
        return wanted == null || (actual != null && normalize(actual).equals(wanted));
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.strip().toLowerCase(Locale.ROOT);
    }
}
