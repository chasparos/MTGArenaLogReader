package app.deckplanner.application;

import app.deck.persistence.DeckCache;
import app.deckplanner.candidate.*;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.catalog.FormatCatalogService;
import app.deckplanner.collection.ArenaCollectionRepository;
import app.deckplanner.collection.CollectionQuantity;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.ui.CardImageCacheSource;
import app.deckplanner.ui.DeckPlannerWorkspace;
import app.enrichment.CardCache;
import app.enrichment.CardImageCache;
import app.enrichment.ScryfallClient;
import app.model.card.CardInfo;
import app.ui.ApplicationModule;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** Cache-first production Deck Planner module and owner of planner-specific resources. */
public final class DeckPlannerModule extends JPanel implements ApplicationModule, AutoCloseable {
    private static final Duration FRESH_CACHE_AGE = Duration.ofHours(24);
    private final FormatCatalogRepository catalogRepository;
    private final FormatCatalogService catalogService;
    private final CardCache cardCache;
    private final CardImageCache imageCache;
    private final DeckCache deckCache;
    private final ArenaCollectionRepository collectionRepository;
    private final ScryfallClient scryfallClient;
    private final ScheduledExecutorService scheduler;
    private final Executor worker;
    private final Path candidateDatabase;
    private final AtomicBoolean closed = new AtomicBoolean();
    private volatile DeckPlannerWorkspace workspace;
    private volatile CandidateRepository candidates;
    private volatile CandidateSetRepository candidateSets;
    private volatile PrintingPreferenceRepository printingPreferences;
    private boolean started;

    public DeckPlannerModule(Path applicationDataRoot,
                             FormatCatalogRepository catalogRepository,
                             FormatCatalogService catalogService,
                             CardCache cardCache,
                             CardImageCache imageCache,
                             DeckCache deckCache,
                             ArenaCollectionRepository collectionRepository,
                             ScryfallClient scryfallClient,
                             ScheduledExecutorService scheduler,
                             Executor worker) {
        super(new BorderLayout());
        this.catalogRepository = catalogRepository;
        this.catalogService = catalogService;
        this.cardCache = cardCache;
        this.imageCache = imageCache;
        this.deckCache = deckCache;
        this.collectionRepository = collectionRepository;
        this.scryfallClient = scryfallClient;
        this.scheduler = scheduler;
        this.worker = worker;
        candidateDatabase = applicationDataRoot.resolve("deck-planner");
        showMessage("Loading Deck Planner from the persistent Standard catalog…");
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> catalogRepository.current("standard"), worker)
                .whenComplete((snapshot, error) -> SwingUtilities.invokeLater(() ->
                        catalogLoaded(snapshot, error)));
    }

    @Override public String id() { return "deck-planner"; }
    @Override public String displayName() { return "Deck Planner"; }
    @Override public JComponent component() { return this; }
    @Override public String shellStatus() {
        return workspace == null ? "Loading Deck Planner" : "Deck Planner ready";
    }

    @Override
    public void activated() {
        started = true;
        DeckPlannerWorkspace current = workspace;
        if (current != null) current.start();
    }

    private void catalogLoaded(Optional<FormatCatalogRepository.Snapshot> snapshot,
                               Throwable loadError) {
        if (closed.get()) return;
        if (loadError != null) {
            showMessage("Deck Planner catalog could not be opened: " + rootMessage(loadError));
            return;
        }
        if (snapshot.isPresent() && !snapshot.get().cardGroups().isEmpty()) {
            installWorkspace(snapshot.get(), DeckPlannerFilterCoordinator.Availability.READY);
            if (refreshRecommended(snapshot.get())) refreshInBackground();
        } else {
            showMessage("No completed Standard catalog is cached. Refreshing in the background…");
            refreshInBackground();
        }
    }

    private void refreshInBackground() {
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> catalogService.refresh("standard", closed::get), worker)
                .thenApply(ignored -> catalogRepository.current("standard"))
                .whenComplete((snapshot, error) -> SwingUtilities.invokeLater(() -> {
                    if (closed.get() || workspace != null) return;
                    if (error != null || snapshot == null || snapshot.isEmpty()) {
                        showMessage("Standard catalog unavailable. Connect to the network or prime the cache, then relaunch.");
                        return;
                    }
                    installWorkspace(snapshot.get(), DeckPlannerFilterCoordinator.Availability.READY);
                }));
    }

    private void installWorkspace(FormatCatalogRepository.Snapshot snapshot,
                                  DeckPlannerFilterCoordinator.Availability availability) {
        if (closed.get() || workspace != null) return;
        CatalogFilterIndex index = new CatalogFilterIndex(snapshot);
        Map<String, CardInfo> cards = new LinkedHashMap<>();
        snapshot.cardGroups().forEach(group -> cards.put(group.identity(), group.preferredPrinting()));
        candidates = new CandidateRepository(candidateDatabase);
        candidateSets = new CandidateSetRepository(candidateDatabase);
        printingPreferences = new PrintingPreferenceRepository(candidateDatabase);
        CandidateModel candidateModel = CandidateModel.persisted(candidates, Runnable::run);
        CandidateWorkspaceState workspaceState = new CandidateWorkspaceState(
                candidateSets.loadWorkspace(), candidateSets::replaceWorkspace);
        CardNameRepository names = new CardNameRepository(index, cardCache,
                scryfallClient::findByExactNameBestEffort,
                scryfallClient::findPrintingsByExactName);
        DeckPlannerWorkspace created = new DeckPlannerWorkspace(
                new DeckPlannerFilterModel("standard"), index,
                new CardImageCacheSource(imageCache, identity -> Optional.ofNullable(cards.get(identity))),
                scheduler, worker, Duration.ofMillis(120), availability,
                candidateModel, this::collectionQuantity,
                names, new DeckCacheKnownArenaDeckSource(deckCache, 24),
                workspaceState, candidateSets,
                new AlternateArtResolver(index, names, printingPreferences));
        created.setPrintingImageLoader(imageCache::get);
        workspace = created;
        removeAll();
        add(created, BorderLayout.CENTER);
        revalidate();
        repaint();
        if (started) created.start();
    }

    private int collectionQuantity(CardInfo card) {
        if (card == null || card.getArenaId() == null || card.getArenaId() <= 0) {
            return CollectionQuantity.UNKNOWN;
        }
        return collectionRepository.quantity(card.getArenaId(), Duration.ofDays(7)).copies();
    }

    private void showMessage(String text) {
        removeAll();
        JLabel message = new JLabel(text, SwingConstants.CENTER);
        message.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        add(message, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    static boolean refreshRecommended(FormatCatalogRepository.Snapshot snapshot) {
        return snapshot.completedAt() == null
                || snapshot.completedAt().isBefore(Instant.now().minus(FRESH_CACHE_AGE));
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        if (workspace != null) workspace.close();
        if (printingPreferences != null) printingPreferences.close();
        if (candidateSets != null) candidateSets.close();
        if (candidates != null) candidates.close();
    }
}
