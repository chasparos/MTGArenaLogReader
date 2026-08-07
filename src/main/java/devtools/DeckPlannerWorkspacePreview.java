package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.collection.CollectionQuantity;
import app.deckplanner.consideration.UnderConsiderationModel;
import app.deckplanner.consideration.UnderConsiderationRepository;
import app.deckplanner.consideration.CardNameRepository;
import app.deckplanner.consideration.DeckCacheKnownArenaDeckSource;
import app.deck.persistence.DeckCache;
import app.enrichment.CardCache;
import app.enrichment.ScryfallClient;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.ui.CardBrowserPanel;
import app.deckplanner.ui.CardImageCacheSource;
import app.deckplanner.ui.DeckPlannerWorkspace;
import app.enrichment.CardImageCache;
import app.model.card.CardInfo;
import app.settings.ThemeService;
import app.ui.AppColors;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/** Standalone human click-test harness for DP-06 acceptance. */
public final class DeckPlannerWorkspacePreview {
    private static final String STALE_IDENTITY = "preview-stale-card";
    private static final Path DEFAULT_ROOT =
            Path.of("target", "deck-planner-dp06-preview");

    private DeckPlannerWorkspacePreview() { }

    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(DeckPlannerWorkspacePreview::launchRealStandardPreview);
    }

    private static void launchRealStandardPreview() {
        assertEdt();
        JFrame frame = new JFrame("Deck Planner DP-06 Acceptance Review");
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        JLabel loading = new JLabel("Loading real Arena-available Standard cards through the catalog pipeline…",
                SwingConstants.CENTER);
        loading.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        frame.setContentPane(loading);
        frame.setSize(1100, 720);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        ExecutorService loader = Executors.newSingleThreadExecutor(
                runnable -> daemon(runnable, "planner-preview-catalog"));
        AtomicReference<PreviewSession> session = new AtomicReference<>();
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) {
                PreviewSession active = session.getAndSet(null);
                if (active != null) active.close();
                loader.shutdownNow();
            }
        });

        CompletableFuture
                .supplyAsync(() -> DeckPlannerStandardPreviewCatalog.load(DEFAULT_ROOT), loader)
                .whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
                    if (!frame.isDisplayable()) return;
                    if (failure != null) {
                        showCatalogFailure(frame, "Could not load Standard preview: " + failure.getMessage());
                        return;
                    }
                    if (result.snapshot().isEmpty()) {
                        showCatalogFailure(frame, result.status());
                        return;
                    }
                    PreviewSession created = createRealSession(
                            DEFAULT_ROOT.resolve("consideration"),
                            result.snapshot().get(),
                            result.availability(),
                            realImageSource(result.snapshot().get()));
                    session.set(created);
                    frame.setContentPane(created.content());
                    frame.setSize(1500, 900);
                    frame.revalidate();
                    frame.repaint();
                    created.workspace().start();
                }));
    }

    private static void showCatalogFailure(JFrame frame, String message) {
        JPanel failure = new JPanel(new BorderLayout(8, 8));
        failure.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel title = new JLabel("Real Standard catalog unavailable");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JTextArea detail = new JTextArea(message + "\n\n"
                + "The DP-06 acceptance preview does not substitute synthetic cards. "
                + "Restore network access or prime a completed cached Standard snapshot, then relaunch.");
        detail.setEditable(false);
        detail.setLineWrap(true);
        detail.setWrapStyleWord(true);
        detail.setOpaque(false);
        failure.add(title, BorderLayout.NORTH);
        failure.add(detail, BorderLayout.CENTER);
        frame.setContentPane(failure);
        frame.revalidate();
        frame.repaint();
    }

    static PreviewSession createSession(Path databasePath,
                                        FormatCatalogRepository.Snapshot snapshot,
                                        DeckPlannerFilterCoordinator.Availability availability,
                                        CardBrowserPanel.ImageSource imageSource) {
        return createSession(databasePath, snapshot, availability, imageSource,
                CardNameRepository.local(new CatalogFilterIndex(snapshot)),
                app.deckplanner.consideration.KnownArenaDeckSource.empty(),
                null, null, null);
    }

    private static PreviewSession createRealSession(Path databasePath,
                                        FormatCatalogRepository.Snapshot snapshot,
                                        DeckPlannerFilterCoordinator.Availability availability,
                                        CardBrowserPanel.ImageSource imageSource) {
        assertEdt();
        Gson gson = new GsonBuilder().disableHtmlEscaping().create();
        CatalogFilterIndex index = new CatalogFilterIndex(snapshot);
        ScryfallClient nameLookup = new ScryfallClient(gson);
        Path observedDeckDatabase = Path.of(
                System.getProperty("user.home"), ".arena-log-viewer", "card-cache");
        CardCache observedCardCache = new CardCache(gson, observedDeckDatabase);
        DeckCache observedDeckCache = new DeckCache(gson, observedCardCache, observedDeckDatabase);
        return createSession(databasePath, snapshot, availability, imageSource,
                new CardNameRepository(index, nameLookup::findByExactName),
                new DeckCacheKnownArenaDeckSource(observedDeckCache, 24),
                nameLookup, observedCardCache, observedDeckCache);
    }

    private static PreviewSession createSession(Path databasePath,
                                                FormatCatalogRepository.Snapshot snapshot,
                                                DeckPlannerFilterCoordinator.Availability availability,
                                                CardBrowserPanel.ImageSource imageSource,
                                                CardNameRepository cardNames,
                                                app.deckplanner.consideration.KnownArenaDeckSource knownDecks,
                                                ScryfallClient nameLookup,
                                                CardCache observedCardCache,
                                                DeckCache observedDeckCache) {
        assertEdt();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> daemon(r, "planner-preview-scheduler"));
        ExecutorService worker = Executors.newSingleThreadExecutor(
                r -> daemon(r, "planner-preview-worker"));
        CatalogFilterIndex index = new CatalogFilterIndex(snapshot);

        UnderConsiderationRepository repository = new UnderConsiderationRepository(databasePath);
        UnderConsiderationModel consideration =
                UnderConsiderationModel.persisted(repository, Runnable::run);
        if (consideration.identities().isEmpty()) consideration.add(initialConsideration(snapshot));

        DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(
                new DeckPlannerFilterModel("standard"), index, imageSource,
                scheduler, worker, Duration.ofMillis(120), availability,
                consideration, ignored -> CollectionQuantity.UNKNOWN,
                cardNames, knownDecks);


        String sampleArenaDeck = sampleArenaDeck(snapshot);
        JTextArea checklist = new JTextArea("""
                DP-06 HUMAN CLICK ACCEPTANCE — REAL STANDARD CARDS
                1. Verify the browser is populated with real Arena-available Standard cards and real cached Scryfall images.
                2. Double-click browser cards; verify they appear at right and get a consideration badge.
                3. Drag candidate chips into a new order; verify Remove/Clear work, then use Normal MTG sort and verify the order visibly changes.
                4. Apply filters after adding candidates; verify hidden candidates remain at right and badges return when filters reset.
                5. Verify the seeded "Unavailable card" row remains recoverable and removable.
                6. Click Import deck: if observed Arena decks exist, select one and load it; also paste the sample deck below. Verify local names resolve first, missing exact names use Scryfall fallback when available, and unresolved names are reported.
                7. Close and relaunch this preview; verify candidate membership/order survives restart.
                8. Exercise Ready / Partial cache / Offline cache and normal resizing/scrolling. Later DP-06 rework steps will replace row rendering/order/import/filter interactions before final acceptance.
                """);
        checklist.setEditable(false);
        checklist.setFocusable(false);
        checklist.setLineWrap(true);
        checklist.setWrapStyleWord(true);
        checklist.setRows(8);
        checklist.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        checklist.setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        checklist.setForeground(AppColors.color("Label.foreground", Color.WHITE));

        JTextArea sampleDeck = new JTextArea(sampleArenaDeck);
        sampleDeck.setEditable(false);
        sampleDeck.setRows(7);
        sampleDeck.setColumns(28);
        sampleDeck.setBorder(BorderFactory.createTitledBorder(
                "Real Standard sample — copy this into Import deck"));

        JPanel stateButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 4));
        stateButtons.setOpaque(false);
        JButton ready = new JButton("Ready");
        JButton partial = new JButton("Partial cache");
        JButton offline = new JButton("Offline cache");
        JButton reset = new JButton("Reset acceptance state");
        ready.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.READY));
        partial.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.PARTIAL_CACHE));
        offline.addActionListener(e -> workspace.setAvailability(DeckPlannerFilterCoordinator.Availability.OFFLINE));
        reset.addActionListener(e -> {
            consideration.clear();
            consideration.add(initialConsideration(snapshot));
        });
        stateButtons.add(reset);
        stateButtons.add(ready);
        stateButtons.add(partial);
        stateButtons.add(offline);

        JLabel catalogStatus = new JLabel("Catalog: " + snapshot.cardGroups().size()
                + " real Standard identities; completed " + snapshot.completedAt());
        catalogStatus.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));

        JPanel review = new JPanel(new BorderLayout(8, 4));
        review.setOpaque(true);
        review.setBackground(checklist.getBackground());
        review.add(catalogStatus, BorderLayout.NORTH);
        review.add(new JScrollPane(checklist,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER), BorderLayout.CENTER);
        review.add(new JScrollPane(sampleDeck), BorderLayout.WEST);
        review.add(stateButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(checklist.getBackground());
        content.add(review, BorderLayout.NORTH);
        content.add(workspace, BorderLayout.CENTER);
        return new PreviewSession(content, workspace, scheduler, worker, repository,
                nameLookup, observedCardCache, observedDeckCache);
    }

    static String sampleArenaDeck(FormatCatalogRepository.Snapshot snapshot) {
        List<FormatCatalogRepository.CardGroup> groups = snapshot.cardGroups();
        StringBuilder deck = new StringBuilder("Deck\n");
        int mainCount = Math.min(3, groups.size());
        for (int i = 0; i < mainCount; i++) {
            deck.append(i == 0 ? "4 " : "1 ")
                    .append(groups.get(i).preferredPrinting().getName()).append('\n');
        }
        if (groups.size() > mainCount) {
            deck.append("\nSideboard\n1 ")
                    .append(groups.get(mainCount).preferredPrinting().getName()).append('\n');
        }
        deck.append("1 Card That Does Not Exist\n");
        return deck.toString();
    }

    static List<String> initialConsideration(FormatCatalogRepository.Snapshot snapshot) {
        List<String> identities = new ArrayList<>();
        snapshot.cardGroups().stream().limit(2)
                .map(FormatCatalogRepository.CardGroup::identity)
                .forEach(identities::add);
        identities.add(STALE_IDENTITY);
        return List.copyOf(identities);
    }

    private static CardBrowserPanel.ImageSource realImageSource(
            FormatCatalogRepository.Snapshot snapshot) {
        Map<String, CardInfo> cards = new LinkedHashMap<>();
        for (FormatCatalogRepository.CardGroup group : snapshot.cardGroups()) {
            cards.put(group.identity(), group.preferredPrinting());
        }
        CardImageCache imageCache = new CardImageCache(DEFAULT_ROOT.resolve("images"));
        return new CardImageCacheSource(imageCache,
                identity -> Optional.ofNullable(cards.get(identity)));
    }

    private static Thread daemon(Runnable runnable, String name) {
        Thread thread = new Thread(runnable, name);
        thread.setDaemon(true);
        return thread;
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Preview content must be created on EDT");
        }
    }

    record PreviewSession(JComponent content, DeckPlannerWorkspace workspace,
                          ScheduledExecutorService scheduler, ExecutorService worker,
                          UnderConsiderationRepository repository,
                          ScryfallClient nameLookup, CardCache observedCardCache,
                          DeckCache observedDeckCache) implements AutoCloseable {
        @Override public void close() {
            workspace.close();
            scheduler.shutdownNow();
            worker.shutdownNow();
            repository.close();
            if (observedDeckCache != null) observedDeckCache.close();
            if (observedCardCache != null) observedCardCache.close();
            if (nameLookup != null) nameLookup.close();
        }
    }
}
