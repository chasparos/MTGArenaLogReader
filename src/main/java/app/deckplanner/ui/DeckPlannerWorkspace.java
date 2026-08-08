package app.deckplanner.ui;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.collection.CollectionQuantity;
import app.deckplanner.candidate.DeckListImporter;
import app.deckplanner.candidate.CardNameRepository;
import app.deckplanner.candidate.KnownArenaDeck;
import app.deckplanner.candidate.KnownArenaDeckSource;
import app.deckplanner.candidate.CandidateModel;
import app.deckplanner.candidate.CandidateWorkspaceState;
import app.deckplanner.candidate.CandidateSetRepository;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.filter.IndexedCatalogCard;
import app.ui.AppColors;
import app.ui.AppScrollBarUI;
import app.model.card.CardInfo;
import app.model.card.MagicCardOrdering;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

/**
 * Reusable DP-05 workspace that composes filter controls, asynchronous filtering, explicit result
 * states, and the responsive DP-04 browser without taking ownership of catalog acquisition.
 */
public final class DeckPlannerWorkspace extends JPanel implements AutoCloseable {
    private static final String CONTENT = "content";
    private static final String STATE = "state";

    private final DeckPlannerFilterModel filterModel;
    private final DeckPlannerFilterPanel filters;
    private final CardBrowserPanel browser;
    private final CandidatePanel candidatePanel = new CandidatePanel();
    private final CatalogFilterIndex catalogIndex;
    private final CandidateModel candidateModel;
    private final CardNameRepository cardNames;
    private final KnownArenaDeckSource knownDecks;
    private final Executor worker;
    private volatile Set<String> candidateFilterIdentities = Set.of();
    private final CandidateModel.Listener candidateListener;
    private final CardBrowserScrollPane browserScrollPane;
    private final DeckPlannerResultsStatePanel statePanel = new DeckPlannerResultsStatePanel();
    private final JPanel resultCards = new JPanel(new CardLayout());
    private final JPanel statusStrip = new JPanel(new BorderLayout(8, 0));
    private final JLabel availabilityBanner = new JLabel();
    private final JProgressBar refreshProgress = new JProgressBar();
    private final DeckPlannerFilterCoordinator coordinator;

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability) {
        this(model, index, imageSource, scheduler, worker, debounce, availability,
                CandidateModel.transientModel(), ignored -> CollectionQuantity.UNKNOWN);
    }

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability,
                                CandidateModel candidateModel,
                                ToIntFunction<CardInfo> collectionQuantitySource) {
        this(model, index, imageSource, scheduler, worker, debounce, availability,
                candidateModel, collectionQuantitySource,
                CardNameRepository.local(index), KnownArenaDeckSource.empty());
    }

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability,
                                CandidateModel candidateModel,
                                ToIntFunction<CardInfo> collectionQuantitySource,
                                CardNameRepository cardNames,
                                KnownArenaDeckSource knownDecks) {
        this(model, index, imageSource, scheduler, worker, debounce, availability,
                candidateModel, collectionQuantitySource, cardNames, knownDecks,
                CandidateWorkspaceState.transientState(), null);
    }

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability,
                                CandidateModel candidateModel,
                                ToIntFunction<CardInfo> collectionQuantitySource,
                                CardNameRepository cardNames,
                                KnownArenaDeckSource knownDecks,
                                CandidateWorkspaceState workspaceState,
                                CandidateSetRepository candidateSets) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(index);
        Objects.requireNonNull(imageSource);
        this.filterModel = model;
        this.catalogIndex = index;
        this.candidateModel = Objects.requireNonNull(candidateModel);
        this.cardNames = Objects.requireNonNull(cardNames);
        this.knownDecks = Objects.requireNonNull(knownDecks);
        this.worker = Objects.requireNonNull(worker);
        this.candidateListener = ignored -> onCandidatesChanged();
        assertEdt();

        setLayout(new BorderLayout());
        setOpaque(true);
        filters = new DeckPlannerFilterPanel(model, allTags(index.cards()));
        browser = new CardBrowserPanel(CardGridLayout.readableDefaults(),
                new ViewportImageWindow(240), imageSource);
        browser.setCandidateListener(new CardBrowserPanel.CandidateListener() {
            @Override public void added(Collection<String> identities) {
                candidateModel.add(identities);
            }
            @Override public void removed(String identity) {
                candidateModel.remove(identity);
            }
        });
        browserScrollPane = new CardBrowserScrollPane(browser);
        candidatePanel.bind(candidateModel, workspaceState, collectionQuantitySource);
        if (candidateSets != null) {
            candidatePanel.setCandidateSetActions(
                    () -> candidateSets.list().stream().map(CandidateSetRepository.CandidateSet::name).toList(),
                    name -> {
                        candidateSets.save(name, candidateModel.identities(), workspaceState.snapshot());
                        candidatePanel.refreshCandidateSetNames();
                    },
                    name -> candidateSets.load(name).ifPresent(set -> {
                        workspaceState.replace(set.workspace());
                        candidateModel.replace(set.identities());
                    }));
        }
        candidatePanel.setImportAction(this::showDeckImportDialog);
        candidatePanel.setMagicSortAction(() -> candidateModel.sortByMagic(catalogIndex));
        candidatePanel.setSelectionAction(selection -> selection
                .filter(this::isResolvedCandidate)
                .ifPresent(ignored -> filterModel.setCandidateOnly(true)));
        candidatePanel.setPreferredSize(new Dimension(470, 600));
        candidatePanel.setMinimumSize(new Dimension(360, 300));

        JScrollPane filterScroll = new JScrollPane(filters,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        filterScroll.setBorder(BorderFactory.createEmptyBorder());
        filterScroll.getVerticalScrollBar().setUI(new AppScrollBarUI());
        filterScroll.getVerticalScrollBar().getModel().addChangeListener(event ->
                syncScrollbarEnabled(filterScroll.getVerticalScrollBar()));
        syncScrollbarEnabled(filterScroll.getVerticalScrollBar());
        filterScroll.getViewport().setBackground(filters.getBackground());
        filterScroll.setPreferredSize(new Dimension(350, 600));
        filterScroll.setMinimumSize(new Dimension(310, 300));

        availabilityBanner.setBorder(new EmptyBorder(0, 8, 0, 0));
        refreshProgress.setIndeterminate(true);
        refreshProgress.setBorderPainted(false);
        refreshProgress.setPreferredSize(new Dimension(92, 6));
        refreshProgress.setMaximumSize(new Dimension(92, 6));
        refreshProgress.setVisible(false);
        statusStrip.setOpaque(true);
        statusStrip.setBorder(new EmptyBorder(4, 2, 4, 8));
        statusStrip.setPreferredSize(new Dimension(10, 28));
        statusStrip.setMinimumSize(new Dimension(10, 28));
        statusStrip.add(availabilityBanner, BorderLayout.CENTER);
        statusStrip.add(refreshProgress, BorderLayout.EAST);

        JPanel content = new JPanel(new BorderLayout());
        content.add(statusStrip, BorderLayout.NORTH);
        content.add(browserScrollPane, BorderLayout.CENTER);
        resultCards.add(content, CONTENT);
        resultCards.add(statePanel, STATE);

        add(filterScroll, BorderLayout.WEST);
        add(resultCards, BorderLayout.CENTER);
        add(candidatePanel, BorderLayout.EAST);
        refreshThemeColors();
        showCandidates();

        coordinator = new DeckPlannerFilterCoordinator(model,
                state -> filterResult(index, state),
                scheduler, worker, debounce);
        coordinator.setListener(this::showResult);
        coordinator.setAvailability(availability);
        candidateModel.addListener(candidateListener);
    }

    public void start() {
        assertEdt();
        coordinator.start();
    }

    public CardBrowserPanel browser() { return browser; }
    public DeckPlannerFilterPanel filters() { return filters; }
    public CandidatePanel candidates() { return candidatePanel; }

    private void showCandidates() {
        assertEdt();
        List<String> identities = candidateModel.identities();
        candidateFilterIdentities = Set.copyOf(identities);
        browser.setCandidateIdentities(new java.util.LinkedHashSet<>(identities));
        candidatePanel.setEntries(candidateModel.resolve(catalogIndex));
    }

    private void onCandidatesChanged() {
        assertEdt();
        showCandidates();
        if (filterModel.state().candidateOnly()) {
            coordinator.restart();
        }
    }

    private boolean isResolvedCandidate(String identity) {
        if (identity == null) return false;
        return catalogIndex.cards().stream()
                .anyMatch(card -> identity.equals(card.group().identity()));
    }

    private DeckPlannerFilterCoordinator.Result filterResult(
            CatalogFilterIndex index, DeckPlannerFilterModel.State state) {
        List<IndexedCatalogCard> cards = index.filter(state.filters());
        if (state.candidateOnly()) {
            Set<String> allowed = candidateFilterIdentities;
            cards = cards.stream()
                    .filter(card -> allowed.contains(card.group().identity()))
                    .toList();
        }
        Map<app.deckplanner.filter.SemanticTag, Long> tagCloud = cards.stream()
                .flatMap(card -> card.tags().stream())
                .collect(Collectors.groupingBy(tag -> tag, TreeMap::new, Collectors.counting()));
        return new DeckPlannerFilterCoordinator.Result(cards, tagCloud);
    }

    public DeckListImporter.Result importDeckText(String deckText) {
        assertEdt();
        DeckListImporter.Result result = DeckListImporter.resolve(deckText, cardNames);
        candidateModel.add(result.identities());
        return result;
    }

    private void showDeckImportDialog() {
        assertEdt();
        JTextArea input = new JTextArea(18, 48);
        input.setLineWrap(false);
        JScrollPane inputScroll = new JScrollPane(input);
        inputScroll.getVerticalScrollBar().setUI(new AppScrollBarUI());

        List<KnownArenaDeck> decks;
        try {
            decks = knownDecks.list();
        } catch (RuntimeException unavailable) {
            decks = List.of();
        }
        JComboBox<KnownArenaDeck> known = new JComboBox<>(decks.toArray(KnownArenaDeck[]::new));
        known.setEnabled(!decks.isEmpty());
        JButton useKnown = new JButton("Use selected Arena deck");
        useKnown.setEnabled(!decks.isEmpty());
        useKnown.addActionListener(event -> {
            KnownArenaDeck selected = (KnownArenaDeck) known.getSelectedItem();
            if (selected != null) input.setText(selected.deckText());
        });

        JPanel knownPanel = new JPanel(new BorderLayout(6, 6));
        knownPanel.setBorder(BorderFactory.createTitledBorder("Known Arena decks"));
        knownPanel.add(known, BorderLayout.CENTER);
        knownPanel.add(useKnown, BorderLayout.EAST);
        if (decks.isEmpty()) {
            knownPanel.add(new JLabel("No observed Arena decks are available in the deck cache."),
                    BorderLayout.SOUTH);
        }

        JPanel content = new JPanel(new BorderLayout(6, 6));
        content.add(knownPanel, BorderLayout.NORTH);
        content.add(inputScroll, BorderLayout.CENTER);
        content.add(new JLabel("Paste or select a deck. Missing exact names may use Scryfall fallback."),
                BorderLayout.SOUTH);
        int choice = JOptionPane.showConfirmDialog(this, content, "Import deck into candidates",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) return;

        String deckText = input.getText();
        CompletableFuture
                .supplyAsync(() -> DeckListImporter.resolve(deckText, cardNames), worker)
                .whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null) {
                        JOptionPane.showMessageDialog(this,
                                "Deck import failed: " + rootMessage(failure),
                                "Deck import", JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    candidateModel.add(result.identities());
                    showImportResult(result);
                }));
    }

    private void showImportResult(DeckListImporter.Result result) {
        String message;
        if (result.parsedCardLines() == 0) {
            message = "No Arena deck card lines were found.";
        } else if (result.unresolvedNames().isEmpty()) {
            message = "Imported " + result.resolvedCards() + " unique card" +
                    (result.resolvedCards() == 1 ? "" : "s") + " into candidates.";
        } else {
            message = "Imported " + result.resolvedCards() + " unique card" +
                    (result.resolvedCards() == 1 ? "" : "s") + ". Could not resolve: " +
                    String.join(", ", result.unresolvedNames());
        }
        if (result.fallbackCards() > 0) {
            message += " " + result.fallbackCards() + " name" +
                    (result.fallbackCards() == 1 ? " was" : "s were") +
                    " resolved by exact-name Scryfall fallback.";
        }
        JOptionPane.showMessageDialog(this, message, "Deck import",
                result.unresolvedNames().isEmpty()
                        ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName() : current.getMessage();
    }

    public void setAvailability(DeckPlannerFilterCoordinator.Availability availability) {
        coordinator.setAvailability(availability);
    }

    @Override public void updateUI() {
        super.updateUI();
        if (availabilityBanner != null) refreshThemeColors();
    }

    private void showResult(DeckPlannerFilterCoordinator.ViewState state) {
        assertEdt();
        if (state instanceof DeckPlannerFilterCoordinator.Content content) {
            statePanel.deactivate();
            setRefreshing(false);
            filters.setTagCloud(content.tagCloud());
            browserScrollPane.setCards(toBrowserCards(content.cards()));
            showCandidates();
            showAvailability(content.availability());
            showCard(CONTENT);
        } else if (state instanceof DeckPlannerFilterCoordinator.Empty empty) {
            setRefreshing(false);
            filters.setTagCloud(empty.tagCloud());
            browserScrollPane.setCards(List.of());
            showCandidates();
            showAvailability(empty.availability());
            statePanel.showState(empty);
            showCard(STATE);
        } else if (state instanceof DeckPlannerFilterCoordinator.Loading loading) {
            showAvailability(loading.availability());
            if (browser.cards().isEmpty()) {
                setRefreshing(false);
                statePanel.showState(loading);
                showCard(STATE);
            } else {
                statePanel.deactivate();
                setRefreshing(true);
                showCard(CONTENT);
            }
        } else if (state instanceof DeckPlannerFilterCoordinator.Failed failed) {
            setRefreshing(false);
            showAvailability(failed.availability());
            statePanel.showState(failed);
            showCard(STATE);
        }
    }

    private void showAvailability(DeckPlannerFilterCoordinator.Availability availability) {
        availabilityBanner.setText(switch (availability) {
            case READY -> refreshProgress.isVisible() ? "Updating results…" : "";
            case PARTIAL_CACHE -> "Partial catalog — showing cached cards while loading continues";
            case OFFLINE -> "Offline — showing the most recent cached catalog";
        });
    }

    private void setRefreshing(boolean refreshing) {
        refreshProgress.setVisible(refreshing);
        if (refreshing && availabilityBanner.getText().isBlank()) {
            availabilityBanner.setText("Updating results…");
        } else if (!refreshing && "Updating results…".equals(availabilityBanner.getText())) {
            availabilityBanner.setText("");
        }
        statusStrip.repaint();
    }

    private void showCard(String name) {
        ((CardLayout) resultCards.getLayout()).show(resultCards, name);
        resultCards.revalidate();
        resultCards.repaint();
    }

    private void refreshThemeColors() {
        Color background = AppColors.color("Panel.background", new Color(0x202328));
        Color foreground = AppColors.color("Label.foreground", Color.WHITE);
        setBackground(background);
        resultCards.setBackground(background);
        statusStrip.setBackground(AppColors.color("Panel.background", background));
        availabilityBanner.setForeground(AppColors.color("Label.disabledForeground", new Color(0xB8BDC7)));
        refreshProgress.setBackground(statusStrip.getBackground());
        refreshProgress.setForeground(AppColors.color("App.accent", new Color(0xC69B52)));
    }

    private static Collection<app.deckplanner.filter.SemanticTag> allTags(List<IndexedCatalogCard> cards) {
        return cards.stream().flatMap(card -> card.tags().stream()).collect(
                java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    private static List<CardBrowserPanel.BrowserCard> toBrowserCards(List<IndexedCatalogCard> cards) {
        return cards.stream()
                .sorted((left, right) -> {
                    int compared = MagicCardOrdering.normalComparator().compare(
                            left.group().preferredPrinting(), right.group().preferredPrinting());
                    return compared != 0 ? compared
                            : left.group().identity().compareToIgnoreCase(right.group().identity());
                })
                .map(card -> new CardBrowserPanel.BrowserCard(
                        card.group().identity(), card.group().preferredPrinting().getName()))
                .toList();
    }


    private static void syncScrollbarEnabled(JScrollBar scrollBar) {
        BoundedRangeModel model = scrollBar.getModel();
        scrollBar.setEnabled(model.getExtent() < model.getMaximum() - model.getMinimum());
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Deck Planner workspace must be used on the EDT");
        }
    }

    @Override public void close() {
        candidateModel.removeListener(candidateListener);
        coordinator.close();
    }
}
