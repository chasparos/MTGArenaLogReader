package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.CardCatalogPage;
import app.deckplanner.catalog.CardCatalogSource;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.catalog.FormatCatalogService;
import app.enrichment.CardCache;
import app.enrichment.CardEnrichmentService;
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
 * Loads the bounded real-card catalog used by the DP-06 human preview through the same catalog
 * service/repository boundary as the product.
 */
final class DeckPlannerStandardPreviewCatalog {
    static final String FORMAT = "standard";
    static final int DEFAULT_CARD_LIMIT = 96;
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
            if (current.filter(DeckPlannerStandardPreviewCatalog::freshEnough).isPresent()) {
                return new LoadResult(current, DeckPlannerFilterCoordinator.Availability.READY,
                        "Using cached Standard catalog from " + current.get().completedAt());
            }
            CardEnrichmentService enrichment = new CardEnrichmentService(
                    scryfall, cardCache, Duration.ofMillis(110));
            return refresh(repository, scryfall, enrichment::acceptCatalogCard, DEFAULT_CARD_LIMIT);
        } catch (RuntimeException error) {
            try (FormatCatalogRepository repository =
                         new FormatCatalogRepository(gson, catalogPath)) {
                Optional<FormatCatalogRepository.Snapshot> cached = repository.current(FORMAT);
                if (cached.isPresent()) {
                    return new LoadResult(cached, DeckPlannerFilterCoordinator.Availability.OFFLINE,
                            "Scryfall refresh failed; using cached Standard catalog: " + error.getMessage());
                }
            } catch (RuntimeException ignored) {
                // Preserve the original acquisition error below.
            }
            return new LoadResult(Optional.empty(), DeckPlannerFilterCoordinator.Availability.OFFLINE,
                    "Could not load real Standard cards: " + error.getMessage());
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
                        .orElse("Catalog refresh completed without a published snapshot."));
    }

    private static boolean freshEnough(FormatCatalogRepository.Snapshot snapshot) {
        return snapshot.completedAt() != null
                && !snapshot.completedAt().isBefore(Instant.now().minus(FRESH_CACHE_AGE));
    }

    record LoadResult(Optional<FormatCatalogRepository.Snapshot> snapshot,
                      DeckPlannerFilterCoordinator.Availability availability,
                      String status) {
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
