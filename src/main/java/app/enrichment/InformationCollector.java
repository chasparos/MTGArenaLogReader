package app.enrichment;

import app.model.card.CardInfo;
import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Provides InformationCollector for card-data enrichment and ordered delivery of decoded log messages.
 *
 * <p>It sits between log decoding and game routing, adding optional external card information without replacing Arena-observed truth.</p>
 *
 * <p>Network and cache failures must not prevent the underlying Arena message from continuing through the pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class InformationCollector implements Runnable, AutoCloseable {
    private final BlockingQueue<LogMessageInterface> input;
    private final BlockingQueue<LogMessageInterface> uiQueue;
    private final ScryfallClient scryfallClient;
    private final CardCache cardCache;
    private final Duration minimumRequestSpacing;
    private final ExecutorService restExecutor;
    private final Consumer<Throwable> errorHandler;
    private final ConcurrentMap<Long, Optional<CardInfo>> cache = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Optional<CardInfo>> relatedCache = new ConcurrentHashMap<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Object throttleLock = new Object();
    private long nextRequestNanos;

    public InformationCollector(BlockingQueue<LogMessageInterface> input,
                                BlockingQueue<LogMessageInterface> uiQueue,
                                ScryfallClient scryfallClient,
                                CardCache cardCache,
                                Duration minimumRequestSpacing,
                                ExecutorService restExecutor,
                                Consumer<Throwable> errorHandler) {
        this.input = input;
        this.uiQueue = uiQueue;
        this.scryfallClient = scryfallClient;
        this.cardCache = cardCache;
        this.minimumRequestSpacing = minimumRequestSpacing;
        this.restExecutor = restExecutor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                LogMessageInterface message = input.take();
                uiQueue.put(message); // base message is immediately available
                attachInformationAsync(message);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Throwable error) {
                errorHandler.accept(error);
            }
        }
    }

    private void attachInformationAsync(LogMessageInterface message) {
        if (message.getReferencedCardIds().isEmpty()) {
            message.getModelFuture().complete(new InformationBundle());
            return;
        }

        restExecutor.submit(() -> {
            InformationBundle bundle = new InformationBundle();
            try {
                for (long arenaId : message.getReferencedCardIds()) {
                    Optional<CardInfo> card = cache.computeIfAbsent(arenaId, this::fetchThrottled);
                    card.ifPresent(value -> {
                        bundle.getCards().put(arenaId, value);
                        prefetchRelatedTokens(value, bundle);
                    });
                }
                message.getModelFuture().complete(bundle);
            } catch (Throwable error) {
                message.getModelFuture().completeExceptionally(error);
                errorHandler.accept(error);
            }
        });
    }

    private void prefetchRelatedTokens(CardInfo source, InformationBundle bundle) {
        if (source.getAllParts() == null) return;
        source.getAllParts().stream()
                .filter(part -> part != null && "token".equalsIgnoreCase(part.getComponent()))
                .filter(part -> part.getId() != null && !part.getId().isBlank())
                .forEach(part -> {
                    Optional<CardInfo> related = relatedCache.computeIfAbsent(part.getId(), this::fetchRelatedThrottled);
                    related.ifPresent(card -> bundle.getRelatedCards().put(part.getId(), card));
                });
    }

    private Optional<CardInfo> fetchRelatedThrottled(String scryfallId) {
        synchronized (throttleLock) {
            throttle();
            return scryfallClient.findByScryfallId(scryfallId);
        }
    }

    private void throttle() {
        long now = System.nanoTime();
        long waitNanos = nextRequestNanos - now;
        if (waitNanos > 0) {
            try {
                long millis = waitNanos / 1_000_000L;
                int nanos = (int) (waitNanos % 1_000_000L);
                Thread.sleep(millis, nanos);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while throttling", interrupted);
            }
        }
        nextRequestNanos = System.nanoTime() + minimumRequestSpacing.toNanos();
    }

    private Optional<CardInfo> fetchThrottled(long arenaId) {
        Optional<CardCache.CachedCard> persisted = cardCache.find(arenaId);
        if (persisted.isPresent() && cacheEntryIsUsable(persisted.get())) {
            return persisted.get().card();
        }
        if (persisted.isPresent()) {
            CardCache.CachedCard stale = persisted.get();
            System.out.println("[CardCache] Refreshing arenaId=" + arenaId
                    + " cacheVersion=" + stale.cacheVersion()
                    + " found=" + stale.found()
                    + " metadataComplete=" + stale.card().map(CardInfo::hasReplayMetadata).orElse(false));
            cardCache.delete(arenaId);
        }

        synchronized (throttleLock) {
            throttle();
            Optional<CardInfo> result = scryfallClient.findByArenaId(arenaId);
            cardCache.put(arenaId, result);
            return result;
        }
    }

    private boolean cacheEntryIsUsable(CardCache.CachedCard cached) {
        if (cached.cacheVersion() < CardCache.CURRENT_CACHE_VERSION) return false;
        if (cached.found()) {
            // Old revisions persisted only names/rules text. Force one refresh so images,
            // faces, IDs, links, type metadata, and the rest of the Card object are available.
            if (cached.card().isEmpty() || !cached.card().get().hasReplayMetadata()) return false;
            return cached.updatedAt().isAfter(Instant.now().minus(Duration.ofDays(90)));
        }
        // Do not remember a historical 404 forever; Arena/Scryfall mappings can arrive later.
        return cached.updatedAt().isAfter(Instant.now().minus(Duration.ofDays(7)));
    }

    @Override
    public void close() {
        running.set(false);
    }
}
