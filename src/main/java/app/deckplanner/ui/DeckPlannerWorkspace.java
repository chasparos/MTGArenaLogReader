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
import app.deckplanner.candidate.AlternateArtResolver;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.filter.IndexedCatalogCard;
import app.ui.AppColors;
import app.ui.AppScrollBarUI;
import app.ui.SvgIcon;
import app.replay.ReplayCardChip;
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
import java.util.function.Function;
import java.awt.image.BufferedImage;
import java.util.Optional;
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
    private final AlternateArtResolver alternateArtResolver;
    private volatile Set<String> candidateFilterIdentities = Set.of();
    private final CandidateModel.Listener candidateListener;
    private final CardBrowserScrollPane browserScrollPane;
    private final DeckPlannerResultsStatePanel statePanel = new DeckPlannerResultsStatePanel();
    private final JPanel resultCards = new JPanel(new CardLayout());
    private final JPanel statusStrip = new JPanel(new BorderLayout(8, 0));
    private final JLabel availabilityBanner = new JLabel();
    private final JProgressBar refreshProgress = new JProgressBar();
    private final JScrollPane filterScroll;
    private final JPanel filterRegion = new JPanel(new BorderLayout());
    private final JPanel candidateRegion = new JPanel(new BorderLayout());
    private boolean candidatesExpanded;
    private Function<CardInfo, CompletableFuture<Optional<BufferedImage>>> printingImageLoader =
            ignored -> CompletableFuture.completedFuture(Optional.empty());
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
        this(model, index, imageSource, scheduler, worker, debounce, availability,
                candidateModel, collectionQuantitySource, cardNames, knownDecks,
                workspaceState, candidateSets, null);
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
                                CandidateSetRepository candidateSets,
                                AlternateArtResolver alternateArtResolver) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(index);
        Objects.requireNonNull(imageSource);
        this.filterModel = model;
        this.catalogIndex = index;
        this.candidateModel = Objects.requireNonNull(candidateModel);
        this.cardNames = Objects.requireNonNull(cardNames);
        this.knownDecks = Objects.requireNonNull(knownDecks);
        this.worker = Objects.requireNonNull(worker);
        this.alternateArtResolver = alternateArtResolver;
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
            @Override public void removed(Collection<String> identities) {
                candidateModel.remove(identities);
            }
        });
        browser.setDragImageProvider(this::catalogDragImage);
        if (alternateArtResolver != null) browser.setAlternateArtListener(this::showAlternateArtPicker);
        browserScrollPane = new CardBrowserScrollPane(browser);
        candidatePanel.bind(candidateModel, workspaceState, collectionQuantitySource);
        if (candidateSets != null) {
            candidatePanel.setCandidateSetActions(
                    () -> candidateSets.list().stream().map(CandidateSetRepository.CandidateSet::name).toList(),
                    name -> {
                        candidateSets.save(name, candidateModel.identities(), workspaceState.snapshot(),
                                candidatePanel.candidateSetNoteFor(name));
                        candidatePanel.setCandidateSetNote(name, candidatePanel.candidateSetNoteFor(name));
                        candidatePanel.refreshCandidateSetNames();
                    },
                    name -> candidateSets.load(name).ifPresent(set -> {
                        candidatePanel.setCandidateSetNote(set.name(), set.note());
                        workspaceState.replace(set.workspace());
                        candidateModel.replace(set.identities());
                    }));
        }
        candidatePanel.setImportAction(this::showDeckImportDialog);
        candidatePanel.setMagicSortAction(() -> candidateModel.sortByMagic(catalogIndex));
        candidatePanel.setSelectionAction(selection -> selection
                .filter(this::isResolvedCandidate)
                .ifPresent(identity -> {
                    browser.scrollToIdentity(identity);
                    filterModel.setCandidateOnly(true);
                }));
        if (alternateArtResolver != null) {
            candidatePanel.setAlternateArtAction(null,
                    identity -> alternateArtResolver.resolveCached(identity).preferred());
        }
        candidatePanel.setPreferredSize(new Dimension(470, 600));
        candidatePanel.setMinimumSize(new Dimension(360, 300));

        filterScroll = new JScrollPane(filters,
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

        JButton filterToggle = overlayToggle(
                new SvgIcon("/svg/tap.svg", 15), "Hide filters");
        JButton candidateToggle = overlayToggle(
                new SvgIcon("/svg/untap.svg", 15), "Expand candidates");

        JPanel columns = new JPanel(new BorderLayout());
        filterRegion.add(filterScroll, BorderLayout.CENTER);
        candidateRegion.add(candidatePanel, BorderLayout.CENTER);
        columns.add(filterRegion, BorderLayout.WEST);
        columns.add(resultCards, BorderLayout.CENTER);
        columns.add(candidateRegion, BorderLayout.EAST);

        JLayeredPane workspaceLayer = new JLayeredPane() {
            @Override public void doLayout() {
                columns.setBounds(0, 0, getWidth(), getHeight());
                columns.doLayout();
                filterRegion.doLayout();
                candidateRegion.doLayout();
                positionWorkspaceToggles(this, filterToggle, candidateToggle);
            }
        };
        workspaceLayer.add(columns, JLayeredPane.DEFAULT_LAYER);
        workspaceLayer.add(filterToggle, JLayeredPane.PALETTE_LAYER);
        workspaceLayer.add(candidateToggle, JLayeredPane.PALETTE_LAYER);
        java.awt.event.ComponentAdapter overlayRelayout = new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                SwingUtilities.invokeLater(() -> {
                    if (!workspaceLayer.isDisplayable() && workspaceLayer.getWidth() <= 0) return;
                    workspaceLayer.doLayout();
                    workspaceLayer.repaint();
                });
            }
        };
        filterRegion.addComponentListener(overlayRelayout);
        candidateRegion.addComponentListener(overlayRelayout);
        workspaceLayer.addComponentListener(overlayRelayout);

        filterToggle.addActionListener(event -> {
            boolean visible = filterScroll.isVisible();
            filterScroll.setVisible(!visible);
            filterToggle.setToolTipText(visible ? "Show filters" : "Hide filters");
            workspaceLayer.revalidate();
            workspaceLayer.doLayout();
            workspaceLayer.repaint();
        });
        candidateToggle.addActionListener(event -> {
            candidatesExpanded = !candidatesExpanded;
            Dimension size = candidatesExpanded
                    ? new Dimension(760, 600) : new Dimension(470, 600);
            candidatePanel.setPreferredSize(size);
            candidateRegion.setPreferredSize(size);
            columns.invalidate();
            columns.doLayout();
            candidateRegion.doLayout();
            positionWorkspaceToggles(workspaceLayer, filterToggle, candidateToggle);
            candidateToggle.setToolTipText(candidatesExpanded
                    ? "Contract candidates" : "Expand candidates");
            workspaceLayer.revalidate();
            workspaceLayer.repaint();
        });

        add(workspaceLayer, BorderLayout.CENTER);

        coordinator = new DeckPlannerFilterCoordinator(model,
                state -> filterResult(index, state),
                scheduler, worker, debounce);
        coordinator.setListener(this::showResult);
        coordinator.setAvailability(availability);
        candidateModel.addListener(candidateListener);
        showCandidates();
    }

    public void start() {
        assertEdt();
        coordinator.start();
    }

    public CardBrowserPanel browser() { return browser; }
    public DeckPlannerFilterPanel filters() { return filters; }
    public CandidatePanel candidates() { return candidatePanel; }

    private Image catalogDragImage(List<String> identities) {
        if (identities == null || identities.isEmpty()) return null;
        Set<String> wanted = new java.util.LinkedHashSet<>(identities);
        List<CardInfo> cards = catalogIndex.cards().stream()
                .filter(card -> wanted.contains(card.group().identity()))
                .map(card -> card.group().preferredPrinting())
                .toList();
        return ReplayCardChip.createDragImage(cards);
    }

    private void showCandidates() {
        assertEdt();
        List<String> identities = candidateModel.identities();
        candidateFilterIdentities = Set.copyOf(identities);
        browser.setCandidateIdentities(new java.util.LinkedHashSet<>(identities));
        candidatePanel.setEntries(candidateModel.resolve(catalogIndex, cardNames::resolveIdentity));
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
        candidatePanel.setBusy(true, "Importing deck… local cache first; enrichment stays optional.");
        CompletableFuture
                .supplyAsync(() -> DeckListImporter.resolve(deckText, cardNames), worker)
                .whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
                    if (failure != null) {
                        candidatePanel.setBusy(false, "Import failed: " + rootMessage(failure));
                        return;
                    }
                    candidateModel.add(result.identities());
                    candidatePanel.setBusy(false, importResultMessage(result));
                }));
    }

    private String importResultMessage(DeckListImporter.Result result) {
        String message;
        if (result.parsedCardLines() == 0) {
            message = "No Arena deck card lines were found.";
        } else if (result.unresolvedNames().isEmpty()) {
            message = "Imported " + result.resolvedCards() + " unique card" +
                    (result.resolvedCards() == 1 ? "" : "s") + ".";
        } else {
            message = "Imported " + result.resolvedCards() + "; unresolved: " +
                    String.join(", ", result.unresolvedNames());
        }
        if (result.fallbackCards() > 0) {
            message += " " + result.fallbackCards() + " used best-effort enrichment.";
        }
        return message;
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

    private List<CardBrowserPanel.BrowserCard> toBrowserCards(List<IndexedCatalogCard> cards) {
        return cards.stream()
                .sorted((left, right) -> {
                    int compared = MagicCardOrdering.normalComparator().compare(
                            left.group().preferredPrinting(), right.group().preferredPrinting());
                    return compared != 0 ? compared
                            : left.group().identity().compareToIgnoreCase(right.group().identity());
                })
                .map(card -> {
                    AlternateArtResolver.ArtSet art = alternateArtResolver == null
                            ? null : alternateArtResolver.resolveCached(card.group().identity());
                    CardInfo presentation = art == null || art.preferred() == null
                            ? card.group().preferredPrinting() : art.preferred();
                    int count = art == null ? card.group().printings().size() : art.printings().size();
                    boolean known = art == null || art.complete();
                    return new CardBrowserPanel.BrowserCard(
                            card.group().identity(), presentation.getName(),
                            count, presentation, known);
                })
                .toList();
    }



    public void setPrintingImageLoader(
            Function<CardInfo, CompletableFuture<Optional<BufferedImage>>> loader) {
        printingImageLoader = loader == null
                ? ignored -> CompletableFuture.completedFuture(Optional.empty()) : loader;
    }

    private void showAlternateArtPicker(String identity) {
        if (alternateArtResolver == null || identity == null) return;

        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, Dialog.ModalityType.MODELESS);
        dialog.setUndecorated(true);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        dialog.setFocusableWindowState(true);
        dialog.getRootPane().setBorder(BorderFactory.createLineBorder(
                AppColors.color("App.accent", new Color(0xD6A84B)), 1, true));
        dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
            @Override public void windowLostFocus(java.awt.event.WindowEvent event) {
                if (dialog.isDisplayable()) dialog.dispose();
            }
        });

        JPanel loading = new JPanel(new BorderLayout(8, 8));
        loading.setBorder(new EmptyBorder(10, 12, 10, 12));
        JProgressBar progress = new JProgressBar();
        progress.setIndeterminate(true);
        loading.add(new JLabel("Loading printings…"), BorderLayout.NORTH);
        loading.add(progress, BorderLayout.CENTER);
        dialog.setContentPane(loading);
        dialog.setSize(360, 84);
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);

        CompletableFuture
                .supplyAsync(() -> alternateArtResolver.resolve(identity), worker)
                .whenComplete((artSet, error) -> SwingUtilities.invokeLater(() -> {
                    if (!dialog.isDisplayable()) return;
                    if (error != null || artSet == null || artSet.printings().isEmpty()) {
                        dialog.dispose();
                        return;
                    }

                    int cardWidth = 220;
                    int cardHeight = 307;
                    JPanel cards = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
                    cards.setBorder(new EmptyBorder(4, 4, 4, 4));
                    for (CardInfo card : artSet.printings()) {
                        JButton choice = new JButton();
                        choice.setToolTipText(printingTooltip(card));
                        choice.setPreferredSize(new Dimension(cardWidth, cardHeight));
                        choice.setMinimumSize(choice.getPreferredSize());
                        choice.setMaximumSize(choice.getPreferredSize());
                        choice.setMargin(new Insets(0, 0, 0, 0));
                        choice.setText(card.getSet() == null ? "?" : card.getSet().toUpperCase()
                                + " #" + Objects.toString(card.getCollectorNumber(), "?"));
                        choice.setHorizontalTextPosition(SwingConstants.CENTER);
                        choice.setVerticalTextPosition(SwingConstants.BOTTOM);
                        if (artSet.favoriteScryfallId()
                                .filter(id -> id.equals(card.getId())).isPresent()) {
                            choice.setBorder(BorderFactory.createLineBorder(
                                    AppColors.color("App.accent", new Color(0xD6A84B)), 2, true));
                        }
                        choice.addActionListener(event -> {
                            alternateArtResolver.favorite(identity, card);
                            showCandidates();
                            coordinator.restart();
                            dialog.dispose();
                        });
                        cards.add(choice);

                        CompletableFuture<Optional<BufferedImage>> imageFuture;
                        try {
                            imageFuture = printingImageLoader.apply(card);
                        } catch (RuntimeException loadError) {
                            imageFuture = CompletableFuture.completedFuture(Optional.empty());
                        }
                        if (imageFuture != null) {
                            imageFuture.whenComplete((image, loadError) ->
                                    SwingUtilities.invokeLater(() -> {
                                        if (!dialog.isDisplayable() || loadError != null
                                                || image == null || image.isEmpty()) return;
                                        Image scaled = image.get().getScaledInstance(
                                                cardWidth - 8, cardHeight - 28, Image.SCALE_SMOOTH);
                                        choice.setIcon(new ImageIcon(scaled));
                                        choice.setText("");
                                    }));
                        }
                    }

                    JScrollPane scroll = new JScrollPane(cards,
                            ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER,
                            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                    scroll.setBorder(BorderFactory.createEmptyBorder());
                    scroll.getHorizontalScrollBar().setUI(new AppScrollBarUI());
                    scroll.getHorizontalScrollBar().setUnitIncrement(48);
                    dialog.setContentPane(scroll);
                    int width = Math.min(900, Math.max(cardWidth + 12,
                            artSet.printings().size() * (cardWidth + 6) + 12));
                    dialog.setSize(width, cardHeight + 34);
                    dialog.setLocationRelativeTo(this);
                    dialog.revalidate();
                    dialog.repaint();
                }));
    }

    private static String printingTooltip(CardInfo card) {
        String set = card.getSet() == null ? "?" : card.getSet().toUpperCase();
        String collector = Objects.toString(card.getCollectorNumber(), "?");
        String artist = card.getArtist() == null ? "Unknown artist" : card.getArtist();
        return set + " #" + collector + " — " + artist;
    }


    private static JPanel centeredRail(JButton button) {
        JPanel rail = new JPanel(new GridBagLayout());
        rail.setOpaque(false);
        rail.setPreferredSize(new Dimension(26, 10));
        rail.add(button);
        return rail;
    }

    private void positionWorkspaceToggles(JLayeredPane layer, JButton filterToggle,
                                          JButton candidateToggle) {
        int diameter = 28;
        int y = Math.max(4, (layer.getHeight() - diameter) / 2);
        int filterX = filterScroll.isVisible() ? filterRegion.getWidth() - diameter / 2 : 0;
        int candidateX = layer.getWidth() - candidateRegion.getWidth() - diameter / 2;
        filterToggle.setBounds(Math.max(0, Math.min(layer.getWidth() - diameter, filterX)),
                y, diameter, diameter);
        candidateToggle.setBounds(Math.max(0, Math.min(layer.getWidth() - diameter, candidateX)),
                y, diameter, diameter);
    }

    private static JButton overlayToggle(Icon icon, String tooltip) {
        JButton button = new JButton(icon) {
            @Override public boolean contains(int x, int y) {
                double radius = Math.min(getWidth(), getHeight()) / 2.0;
                double dx = x - getWidth() / 2.0;
                double dy = y - getHeight() / 2.0;
                return dx * dx + dy * dy <= radius * radius;
            }

            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    ButtonModel model = getModel();
                    if (model.isPressed() || model.isRollover()) {
                        Color rollover = AppColors.color("Button.background", new Color(0x343941));
                        int alpha = model.isPressed() ? 180 : 110;
                        g.setColor(new Color(rollover.getRed(), rollover.getGreen(),
                                rollover.getBlue(), alpha));
                        g.fillOval(1, 1, Math.max(0, getWidth() - 3),
                                Math.max(0, getHeight() - 3));
                    }
                    Icon current = getIcon();
                    if (current != null) {
                        int x = (getWidth() - current.getIconWidth()) / 2;
                        int y = (getHeight() - current.getIconHeight()) / 2;
                        current.paintIcon(this, g, x, y);
                    }
                    g.setColor(AppColors.color("Separator.foreground", new Color(0x6B7078)));
                    g.drawOval(1, 1, Math.max(0, getWidth() - 3),
                            Math.max(0, getHeight() - 3));
                } finally {
                    g.dispose();
                }
            }

            @Override protected void paintBorder(Graphics graphics) {
                // The complete control chrome is painted in paintComponent so the
                // look-and-feel cannot add a rectangular rollover/border.
            }
        };
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(true);
        button.setOpaque(false);
        button.setContentAreaFilled(false);
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setRolloverEnabled(true);
        button.setPreferredSize(new Dimension(28, 28));
        return button;
    }

    private static JButton splitButton(String text, String tooltip) {
        JButton button = new JButton(text) {
            @Override protected void paintComponent(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                            RenderingHints.VALUE_ANTIALIAS_ON);
                    Color bg = AppColors.color("Button.background", new Color(0x343941));
                    g.setColor(bg);
                    g.fillOval(1, 1, getWidth() - 3, getHeight() - 3);
                    g.setColor(AppColors.color("Button.foreground", Color.WHITE));
                    FontMetrics metrics = g.getFontMetrics();
                    String value = getText();
                    g.drawString(value, (getWidth() - metrics.stringWidth(value)) / 2,
                            (getHeight() + metrics.getAscent() - metrics.getDescent()) / 2);
                } finally {
                    g.dispose();
                }
            }
        };
        button.setToolTipText(tooltip);
        button.setPreferredSize(new Dimension(24, 24));
        button.setMinimumSize(new Dimension(24, 24));
        button.setMaximumSize(new Dimension(24, 24));
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        return button;
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
