package app.deckplanner.candidate;

import app.deckplanner.catalog.CatalogCardIdentity;
import app.deckplanner.filter.CatalogFilterIndex;
import app.enrichment.CardCache;
import app.model.card.CardInfo;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

/** Local-first card-name resolution for deck import, with an optional exact-name fallback. */
public final class CardNameRepository {
    public record Resolution(String identity, CardInfo card, boolean fallback) { }

    private final CatalogFilterIndex index;
    private final CardCache persistentCache;
    private final Function<String, Optional<CardInfo>> exactNameFallback;
    private final Function<String, List<CardInfo>> printingsFallback;

    public CardNameRepository(CatalogFilterIndex index,
                              Function<String, Optional<CardInfo>> exactNameFallback) {
        this(index, null, exactNameFallback, ignored -> List.of());
    }

    public CardNameRepository(CatalogFilterIndex index,
                              CardCache persistentCache,
                              Function<String, Optional<CardInfo>> exactNameFallback) {
        this(index, persistentCache, exactNameFallback, ignored -> List.of());
    }

    public CardNameRepository(CatalogFilterIndex index,
                              CardCache persistentCache,
                              Function<String, Optional<CardInfo>> exactNameFallback,
                              Function<String, List<CardInfo>> printingsFallback) {
        this.index = Objects.requireNonNull(index);
        this.persistentCache = persistentCache;
        this.exactNameFallback = Objects.requireNonNull(exactNameFallback);
        this.printingsFallback = Objects.requireNonNull(printingsFallback);
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

        if (persistentCache != null) {
            Optional<CardInfo> cached = persistentCache.findByExactName(wantedName);
            if (cached.isPresent()) {
                return Optional.of(localResolution(cached.get()));
            }
        }

        return exactNameFallback.apply(wantedName)
                .filter(Objects::nonNull)
                .map(this::rememberFallback);
    }

    public Optional<CardInfo> resolveIdentity(String identity) {
        if (identity == null || identity.isBlank()) return Optional.empty();
        for (var indexed : index.cards()) {
            if (identity.equals(indexed.group().identity())) {
                return Optional.of(indexed.group().preferredPrinting());
            }
        }
        return persistentCache == null ? Optional.empty()
                : persistentCache.findByCatalogIdentity(identity);
    }

    public List<CardInfo> printings(String identity) {
        Optional<app.deckplanner.filter.IndexedCatalogCard> local = index.cards().stream()
                .filter(card -> identity != null && identity.equals(card.group().identity()))
                .findFirst();
        if (local.isPresent()) {
            String name = local.get().group().preferredPrinting().getName();
            List<CardInfo> remote = printingsFallback.apply(name);
            return remote == null || remote.isEmpty() ? local.get().group().printings() : List.copyOf(remote);
        }
        Optional<CardInfo> cached = resolveIdentity(identity);
        if (cached.isEmpty()) return List.of();
        List<CardInfo> remote = printingsFallback.apply(cached.get().getName());
        return remote == null || remote.isEmpty() ? List.of(cached.get()) : List.copyOf(remote);
    }

    private Resolution rememberFallback(CardInfo card) {
        if (persistentCache != null && card.getArenaId() != null && card.getArenaId() > 0) {
            persistentCache.put(card.getArenaId(), Optional.of(card));
        }
        return new Resolution(CatalogCardIdentity.of(card), card, true);
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
