package app.deckplanner.ui;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.collection.CollectionQuantity;
import app.deckplanner.consideration.DeckListImporter;
import app.deckplanner.consideration.CardNameRepository;
import app.deckplanner.consideration.KnownArenaDeck;
import app.deckplanner.consideration.KnownArenaDeckSource;
import app.deckplanner.consideration.UnderConsiderationModel;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.filter.IndexedCatalogCard;
import app.ui.AppColors;
import app.ui.AppScrollBarUI;
import app.model.card.CardInfo;

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
    private final UnderConsiderationPanel considerationPanel = new UnderConsiderationPanel();
    private final CatalogFilterIndex catalogIndex;
    private final UnderConsiderationModel considerationModel;
    private final CardNameRepository cardNames;
    private final KnownArenaDeckSource knownDecks;
    private final Executor worker;
    private volatile Set<String> considerationFilterIdentities = Set.of();
    private final UnderConsiderationModel.Listener considerationListener;
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
                UnderConsiderationModel.transientModel(), ignored -> CollectionQuantity.UNKNOWN);
    }

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability,
                                UnderConsiderationModel considerationModel,
                                ToIntFunction<CardInfo> collectionQuantitySource) {
        this(model, index, imageSource, scheduler, worker, debounce, availability,
                considerationModel, collectionQuantitySource,
                CardNameRepository.local(index), KnownArenaDeckSource.empty());
    }

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability,
                                UnderConsiderationModel considerationModel,
                                ToIntFunction<CardInfo> collectionQuantitySource,
                                CardNameRepository cardNames,
                                KnownArenaDeckSource knownDecks) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(index);
        Objects.requireNonNull(imageSource);
        this.filterModel = model;
        this.catalogIndex = index;
        this.considerationModel = Objects.requireNonNull(considerationModel);
        this.cardNames = Objects.requireNonNull(cardNames);
        this.knownDecks = Objects.requireNonNull(knownDecks);
        this.worker = Objects.requireNonNull(worker);
        this.considerationListener = ignored -> onConsiderationChanged();
        assertEdt();

        setLayout(new BorderLayout());
        setOpaque(true);
        filters = new DeckPlannerFilterPanel(model, allTags(index.cards()));
        browser = new CardBrowserPanel(CardGridLayout.readableDefaults(),
                new ViewportImageWindow(240), imageSource);
        browser.setConsiderationListener(new CardBrowserPanel.ConsiderationListener() {
            @Override public void added(Collection<String> identities) {
                considerationModel.add(identities);
            }
            @Override public void removed(String identity) {
                considerationModel.remove(identity);
            }
        });
        browserScrollPane = new CardBrowserScrollPane(browser);
        considerationPanel.bind(considerationModel, collectionQuantitySource);
        considerationPanel.setImportAction(this::showDeckImportDialog);
        considerationPanel.setMagicSortAction(() -> considerationModel.sortByMagic(catalogIndex));
        considerationPanel.setSelectionAction(selection -> selection
                .filter(this::isResolvedCandidate)
                .ifPresent(ignored -> filterModel.setConsiderationOnly(true)));
        considerationPanel.setPreferredSize(new Dimension(470, 600));
        considerationPanel.setMinimumSize(new Dimension(360, 300));

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
        add(considerationPanel, BorderLayout.EAST);
        refreshThemeColors();
        showConsideration();

        coordinator = new DeckPlannerFilterCoordinator(model,
                state -> filterResult(index, state),
                scheduler, worker, debounce);
        coordinator.setListener(this::showResult);
        coordinator.setAvailability(availability);
        considerationModel.addListener(considerationListener);
    }

    public void start() {
        assertEdt();
        coordinator.start();
    }

    public CardBrowserPanel browser() { return browser; }
    public DeckPlannerFilterPanel filters() { return filters; }
    public UnderConsiderationPanel consideration() { return considerationPanel; }

    private void showConsideration() {
        assertEdt();
        List<String> identities = considerationModel.identities();
        considerationFilterIdentities = Set.copyOf(identities);
        browser.setUnderConsiderationIdentities(new java.util.LinkedHashSet<>(identities));
        considerationPanel.setEntries(considerationModel.resolve(catalogIndex));
    }

    private void onConsiderationChanged() {
        assertEdt();
        showConsideration();
        if (filterModel.state().considerationOnly()) {
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
        if (state.considerationOnly()) {
            Set<String> allowed = considerationFilterIdentities;
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
        considerationModel.add(result.identities());
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
        int choice = JOptionPane.showConfirmDialog(this, content, "Import deck into consideration",
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
                    considerationModel.add(result.identities());
                    showImportResult(result);
                }));
    }

    private void showImportResult(DeckListImporter.Result result) {
        String message;
        if (result.parsedCardLines() == 0) {
            message = "No Arena deck card lines were found.";
        } else if (result.unresolvedNames().isEmpty()) {
            message = "Imported " + result.resolvedCards() + " unique card" +
                    (result.resolvedCards() == 1 ? "" : "s") + " into consideration.";
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
            showConsideration();
            showAvailability(content.availability());
            showCard(CONTENT);
        } else if (state instanceof DeckPlannerFilterCoordinator.Empty empty) {
            setRefreshing(false);
            filters.setTagCloud(empty.tagCloud());
            browserScrollPane.setCards(List.of());
            showConsideration();
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
        return cards.stream().map(card -> new CardBrowserPanel.BrowserCard(
                card.group().identity(), card.group().preferredPrinting().getName())).toList();
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
        considerationModel.removeListener(considerationListener);
        coordinator.close();
    }
}
