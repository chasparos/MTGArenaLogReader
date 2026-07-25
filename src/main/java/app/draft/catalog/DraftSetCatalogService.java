package app.draft.catalog;

import app.draft.model.DraftSet;
import app.enrichment.CardCache;
import app.enrichment.ScryfallClient;
import app.model.card.CardInfo;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

public final class DraftSetCatalogService {
    private static final Duration SET_CACHE_AGE = Duration.ofDays(14);

    private final ScryfallClient scryfall;
    private final CardCache cache;
    private final Executor executor;

    public DraftSetCatalogService(
            ScryfallClient scryfall,
            CardCache cache,
            Executor executor) {
        this.scryfall = Objects.requireNonNull(scryfall, "scryfall");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    public CompletableFuture<List<DraftSet>> sets() {
        return CompletableFuture.supplyAsync(scryfall::listSets, executor);
    }

    public CompletableFuture<List<CardInfo>> cards(String setCode) {
        return CompletableFuture.supplyAsync(() ->
                cache.findSet(setCode, SET_CACHE_AGE)
                        .orElseGet(() -> fetchAndCache(setCode)), executor);
    }

    private List<CardInfo> fetchAndCache(String setCode) {
        List<CardInfo> cards = scryfall.findArenaCardsInSet(setCode);
        cache.putSet(setCode, cards);
        return cards;
    }
}
