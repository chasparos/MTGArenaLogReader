package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.collection.CollectionQuantity;
import app.deckplanner.candidate.CandidateModel;
import app.deckplanner.candidate.CandidateRepository;
import app.deckplanner.candidate.CandidateSetRepository;
import app.deckplanner.candidate.CandidateWorkspaceState;
import app.deckplanner.candidate.CardNameRepository;
import app.deckplanner.candidate.PrintingPreferenceRepository;
import app.deckplanner.candidate.AlternateArtResolver;
import app.deckplanner.candidate.DeckCacheKnownArenaDeckSource;
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

/** Standalone human click-test harness for Deck Planner human review. */
public final class DeckPlannerWorkspacePreview {
    private static final String STALE_IDENTITY = "preview-stale-card";
    private static final Path DEFAULT_ROOT =
            Path.of("target", "deck-planner-dp06-preview");
    private static final Path PERSISTENT_ROOT =
            Path.of(System.getProperty("user.home"), ".arena-log-viewer");

    private DeckPlannerWorkspacePreview() { }

    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(DeckPlannerWorkspacePreview::launchRealStandardPreview);
    }

    private static void launchRealStandardPreview() {
        assertEdt();
        JFrame frame = new JFrame("Deck Planner DP-08 Integration Review");
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
                .supplyAsync(() -> DeckPlannerStandardPreviewCatalog.load(PERSISTENT_ROOT), loader)
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
                            DEFAULT_ROOT.resolve("candidates"),
                            result.snapshot().get(),
                            result.availability(),
                            realImageSource(result.snapshot().get()));
                    session.set(created);
                    frame.setContentPane(created.content());
                    frame.setSize(1500, 900);
                    frame.revalidate();
                    frame.repaint();
                    created.workspace().start();
                    if (result.refreshRecommended()) {
                        loader.execute(() -> DeckPlannerStandardPreviewCatalog.refresh(PERSISTENT_ROOT));
                    }
                }));
    }

    private static void showCatalogFailure(JFrame frame, String message) {
        JPanel failure = new JPanel(new BorderLayout(8, 8));
        failure.setBorder(BorderFactory.createEmptyBorder(28, 28, 28, 28));
        JLabel title = new JLabel("Real Standard catalog unavailable");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        JTextArea detail = new JTextArea(message + "\n\n"
                + "The Deck Planner review preview does not substitute synthetic cards. "
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
                app.deckplanner.candidate.KnownArenaDeckSource.empty(),
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
                new CardNameRepository(index, observedCardCache, nameLookup::findByExactNameBestEffort,
                        nameLookup::findPrintingsByExactName),
                new DeckCacheKnownArenaDeckSource(observedDeckCache, 24),
                nameLookup, observedCardCache, observedDeckCache);
    }

    private static PreviewSession createSession(Path databasePath,
                                                FormatCatalogRepository.Snapshot snapshot,
                                                DeckPlannerFilterCoordinator.Availability availability,
                                                CardBrowserPanel.ImageSource imageSource,
                                                CardNameRepository cardNames,
                                                app.deckplanner.candidate.KnownArenaDeckSource knownDecks,
                                                ScryfallClient nameLookup,
                                                CardCache observedCardCache,
                                                DeckCache observedDeckCache) {
        assertEdt();
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(
                r -> daemon(r, "planner-preview-scheduler"));
        ExecutorService worker = Executors.newSingleThreadExecutor(
                r -> daemon(r, "planner-preview-worker"));
        CatalogFilterIndex index = new CatalogFilterIndex(snapshot);

        CandidateRepository repository = new CandidateRepository(databasePath);
        CandidateSetRepository candidateSets = new CandidateSetRepository(databasePath);
        PrintingPreferenceRepository printingPreferences = new PrintingPreferenceRepository(databasePath);
        CandidateWorkspaceState workspaceState = new CandidateWorkspaceState(
                candidateSets.loadWorkspace(), candidateSets::replaceWorkspace);
        CandidateModel candidates =
                CandidateModel.persisted(repository, Runnable::run);
        if (candidates.identities().isEmpty()) candidates.add(initialCandidates(snapshot));

        DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(
                new DeckPlannerFilterModel("standard"), index, imageSource,
                scheduler, worker, Duration.ofMillis(120), availability,
                candidates, ignored -> CollectionQuantity.UNKNOWN,
                cardNames, knownDecks, workspaceState, candidateSets,
                new AlternateArtResolver(index, cardNames, printingPreferences));
        CardImageCache printingImages = new CardImageCache(PERSISTENT_ROOT.resolve("images"));
        workspace.setPrintingImageLoader(printingImages::get);


        String sampleArenaDeck = sampleArenaDeck(snapshot);
        JPanel checklist = acceptanceChecklist();

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
            candidates.clear();
            candidates.add(initialCandidates(snapshot));
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
        review.setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        review.add(catalogStatus, BorderLayout.NORTH);
        JScrollPane checklistScroll = new JScrollPane(checklist,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        checklistScroll.getVerticalScrollBar().setUnitIncrement(12);
        review.add(checklistScroll, BorderLayout.CENTER);
        review.add(new JScrollPane(sampleDeck), BorderLayout.WEST);
        review.add(stateButtons, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(true);
        content.setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        content.add(review, BorderLayout.NORTH);
        content.add(workspace, BorderLayout.CENTER);
        return new PreviewSession(content, workspace, scheduler, worker, repository, candidateSets,
                printingPreferences, nameLookup, observedCardCache, observedDeckCache);
    }

    static JPanel acceptanceChecklist() {
        String[] steps = {
                "Startup/lifecycle: does the workspace become usable promptly from the persistent catalog and remain responsive while background refresh or images continue?",
                "Offline/degraded states: do Ready, Partial cache, and Offline cache communicate what is happening without blocking normal cached-card planning?",
                "End-to-end flow: does filter/refine → Candidates → note/set workflow → AI export still feel coherent after the integration work?",
                "Shutdown/relaunch: after closing and reopening the preview, does persisted planning state return cleanly without stale dialogs, hung work, or duplicated background activity?"
        };

        Color background = AppColors.color("Panel.background", new Color(0x202328));
        Color foreground = AppColors.color("Label.foreground", Color.WHITE);
        JPanel checklist = new JPanel();
        checklist.setName("dp08-review-checklist");
        checklist.setLayout(new BoxLayout(checklist, BoxLayout.Y_AXIS));
        checklist.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        checklist.setBackground(background);

        JLabel title = new JLabel("DP-08 HUMAN INTEGRATION REVIEW");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        title.setForeground(foreground);
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        checklist.add(title);
        checklist.add(Box.createVerticalStrut(4));

        JLabel status = new JLabel();
        status.setName("dp08-review-status");
        status.setForeground(foreground);
        status.setAlignmentX(Component.LEFT_ALIGNMENT);

        List<JCheckBox> boxes = new ArrayList<>();
        Runnable updateStatus = () -> {
            long checked = boxes.stream().filter(AbstractButton::isSelected).count();
            if (checked == steps.length) {
                status.setText("Integration review: " + checked + "/" + steps.length
                        + " checked — report lifecycle/performance observations; DP-08 remains active.");
            } else {
                status.setText("Integration review: " + checked + "/" + steps.length
                        + " checked — DP-08 remains active.");
            }
        };

        for (int index = 0; index < steps.length; index++) {
            JCheckBox box = new JCheckBox((index + 1) + ". " + steps[index]);
            box.setName("dp08-review-step-" + (index + 1));
            box.setOpaque(false);
            box.setForeground(foreground);
            box.setAlignmentX(Component.LEFT_ALIGNMENT);
            box.addActionListener(event -> updateStatus.run());
            boxes.add(box);
            checklist.add(box);
        }
        checklist.add(Box.createVerticalStrut(5));
        checklist.add(status);
        updateStatus.run();
        return checklist;
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

    static List<String> initialCandidates(FormatCatalogRepository.Snapshot snapshot) {
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
        CardImageCache imageCache = new CardImageCache(PERSISTENT_ROOT.resolve("images"));
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
                          CandidateRepository repository, CandidateSetRepository candidateSets,
                          PrintingPreferenceRepository printingPreferences,
                          ScryfallClient nameLookup, CardCache observedCardCache,
                          DeckCache observedDeckCache) implements AutoCloseable {
        @Override public void close() {
            workspace.close();
            scheduler.shutdownNow();
            worker.shutdownNow();
            repository.close();
            candidateSets.close();
            printingPreferences.close();
            if (observedDeckCache != null) observedDeckCache.close();
            if (observedCardCache != null) observedCardCache.close();
            if (nameLookup != null) nameLookup.close();
        }
    }
}
