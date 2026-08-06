package app.enrichment;

import app.model.InformationBundle;
import app.model.card.CardInfo;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Shared throttled cache boundary for log-driven and bulk card enrichment. */
public final class CardEnrichmentService {
    private final ScryfallClient scryfallClient;
    private final CardCache cardCache;
    private final Duration minimumRequestSpacing;
    private final ConcurrentMap<Long, Optional<CardInfo>> arenaCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Optional<CardInfo>> relatedCache = new ConcurrentHashMap<>();
    private final Object throttleLock = new Object();
    private long nextRequestNanos;

    public CardEnrichmentService(ScryfallClient scryfallClient,
                                 CardCache cardCache,
                                 Duration minimumRequestSpacing) {
        this.scryfallClient = scryfallClient;
        this.cardCache = cardCache;
        this.minimumRequestSpacing = minimumRequestSpacing;
    }

    public Optional<CardInfo> resolveArenaCard(long arenaId) {
        return arenaCache.computeIfAbsent(arenaId, this::fetchArenaCard);
    }

    /**
     * Accepts a card already returned by a Scryfall catalog page, persists it in
     * the shared Arena cache when addressable, and resolves token parts through
     * the same throttled boundary used by live log enrichment.
     */
    public void acceptCatalogCard(CardInfo card) {
        if (card == null) throw new IllegalArgumentException("Catalog card is null");
        if (card.getArenaId() != null && card.getArenaId() > 0) {
            cardCache.put(card.getArenaId(), Optional.of(card));
            arenaCache.put(card.getArenaId(), Optional.of(card));
        }
        prefetchRelated(card, null);
    }

    public void enrichBundle(CardInfo source, InformationBundle bundle) {
        prefetchRelated(source, bundle);
    }

    private Optional<CardInfo> fetchArenaCard(long arenaId) {
        Optional<CardCache.CachedCard> persisted = cardCache.find(arenaId);
        if (persisted.isPresent() && cacheEntryIsUsable(persisted.get())) {
            return persisted.get().card();
        }
        if (persisted.isPresent()) cardCache.delete(arenaId);
        synchronized (throttleLock) {
            throttle();
            Optional<CardInfo> result = scryfallClient.findByArenaId(arenaId);
            cardCache.put(arenaId, result);
            return result;
        }
    }

    private void prefetchRelated(CardInfo source, InformationBundle bundle) {
        if (source.getAllParts() == null) return;
        source.getAllParts().stream()
                .filter(part -> part != null && "token".equalsIgnoreCase(part.getComponent()))
                .filter(part -> part.getId() != null && !part.getId().isBlank())
                .forEach(part -> {
                    Optional<CardInfo> related = relatedCache.computeIfAbsent(
                            part.getId(), this::fetchRelated);
                    if (bundle != null) {
                        related.ifPresent(card -> bundle.getRelatedCards().put(part.getId(), card));
                    }
                });
    }

    private Optional<CardInfo> fetchRelated(String scryfallId) {
        synchronized (throttleLock) {
            throttle();
            return scryfallClient.findByScryfallId(scryfallId);
        }
    }

    private boolean cacheEntryIsUsable(CardCache.CachedCard cached) {
        if (cached.cacheVersion() < CardCache.CURRENT_CACHE_VERSION) return false;
        if (cached.found()) {
            return cached.card().isPresent()
                    && cached.card().get().hasReplayMetadata()
                    && cached.updatedAt().isAfter(Instant.now().minus(Duration.ofDays(90)));
        }
        return cached.updatedAt().isAfter(Instant.now().minus(Duration.ofDays(7)));
    }

    private void throttle() {
        long waitNanos = nextRequestNanos - System.nanoTime();
        if (waitNanos > 0) {
            try {
                Thread.sleep(waitNanos / 1_000_000L,
                        (int) (waitNanos % 1_000_000L));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while throttling", interrupted);
            }
        }
        nextRequestNanos = System.nanoTime() + minimumRequestSpacing.toNanos();
    }
}
