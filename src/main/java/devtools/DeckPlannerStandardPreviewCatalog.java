package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.CardCatalogPage;
import app.deckplanner.catalog.CardCatalogSource;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.catalog.FormatCatalogService;
import app.enrichment.CardCache;
import app.enrichment.ScryfallClient;
import app.model.card.CardInfo;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Loads the real Standard Arena catalog used by the DP-06 human preview through the same
 * persistent catalog/card-cache boundary as the product.
 */
final class DeckPlannerStandardPreviewCatalog {
    static final String FORMAT = "standard";
    static final Duration FRESH_CACHE_AGE = Duration.ofHours(12);

    private DeckPlannerStandardPreviewCatalog() { }

    static LoadResult load(Path previewRoot) {
        Objects.requireNonNull(previewRoot);
        Gson gson = new GsonBuilder().create();
        Path catalogPath = previewRoot.resolve("format-catalog");
        try (ScryfallClient scryfall = new ScryfallClient(gson);
             CardCache cardCache = new CardCache(gson, previewRoot.resolve("card-cache"));
             FormatCatalogRepository repository = new FormatCatalogRepository(gson, catalogPath)) {
            Optional<FormatCatalogRepository.Snapshot> current = repository.current(FORMAT);
            if (current.isPresent()) {
                boolean fresh = freshEnough(current.get());
                return new LoadResult(current,
                        fresh ? DeckPlannerFilterCoordinator.Availability.READY
                                : DeckPlannerFilterCoordinator.Availability.PARTIAL_CACHE,
                        "Using persistent Standard catalog from " + current.get().completedAt()
                                + (fresh ? "" : " while a background refresh runs."),
                        !fresh);
            }
            FormatCatalogService service = new FormatCatalogService(
                    scryfall, repository, card -> persistCatalogCard(cardCache, card));
            service.refresh(FORMAT, () -> false);
            Optional<FormatCatalogRepository.Snapshot> snapshot = repository.current(FORMAT);
            return new LoadResult(snapshot, DeckPlannerFilterCoordinator.Availability.READY,
                    snapshot.map(value -> "Loaded and cached the full Standard Arena catalog ("
                                    + value.cardGroups().size() + " logical cards).")
                            .orElse("Catalog refresh completed without a published snapshot."), false);
        } catch (RuntimeException error) {
            try (FormatCatalogRepository repository =
                         new FormatCatalogRepository(gson, catalogPath)) {
                Optional<FormatCatalogRepository.Snapshot> cached = repository.current(FORMAT);
                if (cached.isPresent()) {
                    return new LoadResult(cached, DeckPlannerFilterCoordinator.Availability.OFFLINE,
                            "Scryfall refresh failed; using cached Standard catalog: " + error.getMessage(), true);
                }
            } catch (RuntimeException ignored) {
                // Preserve the original acquisition error below.
            }
            return new LoadResult(Optional.empty(), DeckPlannerFilterCoordinator.Availability.OFFLINE,
                    "Could not load real Standard cards: " + error.getMessage(), true);
        }
    }

    static void refresh(Path previewRoot) {
        Objects.requireNonNull(previewRoot);
        Gson gson = new GsonBuilder().create();
        Path catalogPath = previewRoot.resolve("format-catalog");
        try (ScryfallClient scryfall = new ScryfallClient(gson);
             CardCache cardCache = new CardCache(gson, previewRoot.resolve("card-cache"));
             FormatCatalogRepository repository = new FormatCatalogRepository(gson, catalogPath)) {
            FormatCatalogService service = new FormatCatalogService(
                    scryfall, repository, card -> persistCatalogCard(cardCache, card));
            service.refresh(FORMAT, () -> Thread.currentThread().isInterrupted());
        } catch (RuntimeException error) {
            System.err.println("[DeckPlannerPreview] Background Standard refresh failed: "
                    + error.getMessage());
        }
    }

    static LoadResult load(Path catalogPath,
                           CardCatalogSource source,
                           Consumer<CardInfo> enrichmentStep,
                           int cardLimit) {
        Gson gson = new GsonBuilder().create();
        try (FormatCatalogRepository repository = new FormatCatalogRepository(gson, catalogPath)) {
            return refresh(repository, source, enrichmentStep, cardLimit);
        }
    }

    private static LoadResult refresh(FormatCatalogRepository repository,
                                      CardCatalogSource source,
                                      Consumer<CardInfo> enrichmentStep,
                                      int cardLimit) {
        if (cardLimit < 1) throw new IllegalArgumentException("cardLimit < 1");
        FormatCatalogService service = new FormatCatalogService(
                new BoundedSource(source, cardLimit), repository, enrichmentStep);
        service.refresh(FORMAT, () -> false);
        Optional<FormatCatalogRepository.Snapshot> snapshot = repository.current(FORMAT);
        return new LoadResult(snapshot, DeckPlannerFilterCoordinator.Availability.READY,
                snapshot.map(value -> "Loaded " + value.cardGroups().size()
                                + " real Standard cards through the catalog pipeline.")
                        .orElse("Catalog refresh completed without a published snapshot."), false);
    }

    private static void persistCatalogCard(CardCache cache, CardInfo card) {
        if (card != null && card.getArenaId() != null && card.getArenaId() > 0) {
            cache.put(card.getArenaId(), Optional.of(card));
        }
    }

    private static boolean freshEnough(FormatCatalogRepository.Snapshot snapshot) {
        return snapshot.completedAt() != null
                && !snapshot.completedAt().isBefore(Instant.now().minus(FRESH_CACHE_AGE));
    }

    record LoadResult(Optional<FormatCatalogRepository.Snapshot> snapshot,
                      DeckPlannerFilterCoordinator.Availability availability,
                      String status,
                      boolean refreshRecommended) {
        LoadResult {
            snapshot = snapshot == null ? Optional.empty() : snapshot;
            availability = Objects.requireNonNull(availability);
            status = status == null ? "" : status;
        }
    }

    private static final class BoundedSource implements CardCatalogSource {
        private final CardCatalogSource delegate;
        private int remaining;

        private BoundedSource(CardCatalogSource delegate, int maximumCards) {
            this.delegate = Objects.requireNonNull(delegate);
            this.remaining = maximumCards;
        }

        @Override public CardCatalogPage firstPage(String normalizedFormat) {
            return limit(delegate.firstPage(normalizedFormat));
        }

        @Override public CardCatalogPage nextPage(String cursor) {
            return limit(delegate.nextPage(cursor));
        }

        private CardCatalogPage limit(CardCatalogPage page) {
            if (remaining <= 0) return new CardCatalogPage(List.of(), null);
            List<CardInfo> cards = page.cards();
            int count = Math.min(remaining, cards.size());
            List<CardInfo> accepted = List.copyOf(cards.subList(0, count));
            remaining -= count;
            String next = remaining > 0 ? page.nextCursor() : null;
            return new CardCatalogPage(accepted, next);
        }
    }
}
