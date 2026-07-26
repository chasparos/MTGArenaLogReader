package app.draft.ui;

import app.draft.analysis.DraftCardOrdering;
import app.draft.analysis.DraftDeckAnalysis;
import app.draft.analysis.DraftSetResolver;
import app.draft.catalog.DraftSetCatalogService;
import app.draft.export.DraftAiExporter;
import app.draft.export.DraftSetRankingExporter;
import app.draft.model.DraftCardCount;
import app.draft.model.DraftCardRating;
import app.draft.model.DraftPickState;
import app.draft.model.DraftSet;
import app.draft.model.DraftTier;
import app.draft.model.DraftUiModel;
import app.draft.ranking.DraftRankingParser;
import app.draft.ranking.DraftRankingRepository;
import app.enrichment.CardImageCache;
import app.model.card.CardInfo;
import app.ui.SvgIcon;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Draft-pick workspace. Domain analysis, set loading and ranking parsing live
 * outside Swing; this frame coordinates and renders their immutable results.
 */
public final class DraftAssistantFrame extends JFrame {
    private final DraftUiModel model;
    private final DraftAiExporter pickExporter;
    private final DraftSetCatalogService catalog;
    private final DraftSetRankingExporter rankingExporter;
    private final DraftRankingParser rankingParser;
    private final DraftRankingRepository rankingRepository;
    private final DraftCardOrdering ordering = new DraftCardOrdering();
    private final DraftDeckAnalysis deckAnalysis = new DraftDeckAnalysis();
    private final DraftSetResolver setResolver = new DraftSetResolver();
    private final DraftCardPreview cardPreview;

    private final JLabel position = new JLabel("No draft loaded");
    private final JLabel status = new JLabel("Set catalog not loaded");
    private final SetAutocompleteComboBox setSelector =
            new SetAutocompleteComboBox();
    private final JPanel packCards = cardGrid();
    private final JPanel poolCards = cardGrid();
    private final JPanel deckCards = cardGrid();
    private final JPanel sideboardCards = cardGrid();
    private final JLabel poolMetrics = new JLabel("No drafted cards");
    private final JLabel poolPipMetrics = new JLabel("Pips: -");
    private final ManaCurvePanel poolManaCurve = new ManaCurvePanel();
    private final JLabel deckMetrics = new JLabel("No deck observed");
    private final JLabel pipMetrics = new JLabel("Pips: —");
    private final ManaCurvePanel manaCurve = new ManaCurvePanel();
    private final JButton previousPack = navigationButton(
            "/ui/chevrons-left.svg", "Previous pack");
    private final JButton previous = navigationButton(
            "/ui/chevron-left.svg", "Previous pick");
    private final JButton next = navigationButton(
            "/ui/chevron-right.svg", "Next pick");
    private final JButton nextPack = navigationButton(
            "/ui/chevrons-right.svg", "Next pack");
    private final JButton copyRankingRequest =
            new JButton("Copy set ranking request");
    private final JButton importRanking =
            new JButton("Import ranking from clipboard");

    private DraftSet activeSet;
    private List<CardInfo> activeSetCards = List.of();
    private Map<Long, CardInfo> activeSetCardsByArenaId = Map.of();
    private Map<Long, DraftCardRating> ratingsByArenaId = Map.of();
    private Map<String, DraftCardRating> ratingsByName = Map.of();
    private String activeDraftId = "";
    private boolean changingSet;

    public DraftAssistantFrame(
            DraftUiModel model,
            DraftAiExporter pickExporter,
            DraftSetCatalogService catalog,
            DraftSetRankingExporter rankingExporter,
            DraftRankingParser rankingParser,
            DraftRankingRepository rankingRepository,
            CardImageCache cardImageCache) {
        super("Draft assistant");
        this.model = model;
        this.pickExporter = pickExporter;
        this.catalog = catalog;
        this.rankingExporter = rankingExporter;
        this.rankingParser = rankingParser;
        this.rankingRepository = rankingRepository;
        this.cardPreview = new DraftCardPreview(cardImageCache);
        initialize();
        model.addListener(ignored ->
                SwingUtilities.invokeLater(this::refresh));
        loadSets();
    }

    public void open() {
        refresh();
        setVisible(true);
        toFront();
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(1320, 820);
        setLocationByPlatform(true);

        JPanel header = new JPanel(new BorderLayout(10, 6));
        header.setBorder(new EmptyBorder(8, 10, 8, 10));
        position.setFont(position.getFont().deriveFont(Font.BOLD));
        header.add(position, BorderLayout.WEST);
        header.add(setControls(), BorderLayout.CENTER);
        header.add(navigation(), BorderLayout.EAST);

        JTabbedPane collection = new JTabbedPane();
        collection.addTab("Drafted collection", analyzedWorkspace(
                poolCards, poolMetrics, poolPipMetrics, poolManaCurve));
        collection.addTab("Current deck", analyzedWorkspace(
                deckCards, deckMetrics, pipMetrics, manaCurve));
        collection.addTab("Sideboard", cardScroll(sideboardCards));

        JSplitPane split = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                titled("Current pick", cardScroll(packCards)),
                collection);
        split.setResizeWeight(0.43);
        split.setDividerLocation(545);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(split, BorderLayout.CENTER);
        status.setBorder(new EmptyBorder(5, 10, 7, 10));
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel setControls() {
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        controls.add(new JLabel("Draft set:"));
        setSelector.setPreferredSize(new Dimension(280, 28));
        setSelector.addActionListener(event -> {
            if (changingSet || setSelector.isUpdatingModel()) return;
            Object selected = setSelector.getSelectedItem();
            if (selected instanceof DraftSet set) activateSet(set, true);
        });
        controls.add(setSelector);
        controls.add(copyRankingRequest);
        controls.add(importRanking);
        copyRankingRequest.setEnabled(false);
        importRanking.setEnabled(false);
        copyRankingRequest.addActionListener(event -> copyRankingRequest());
        importRanking.addActionListener(event -> importRanking());
        return controls;
    }

    private JPanel navigation() {
        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        previousPack.addActionListener(event -> model.previousPack());
        previous.addActionListener(event -> model.previous());
        next.addActionListener(event -> model.next());
        nextPack.addActionListener(event -> model.nextPack());
        JButton copyPick = new JButton("Copy AI pick request");
        copyPick.addActionListener(event -> copyPickRequest());
        navigation.add(previousPack);
        navigation.add(previous);
        navigation.add(next);
        navigation.add(nextPack);
        navigation.add(copyPick);
        return navigation;
    }

    private static JButton navigationButton(
            String resource, String description) {
        JButton button = new JButton(new SvgIcon(resource, 16));
        button.setToolTipText(description);
        button.getAccessibleContext().setAccessibleName(description);
        button.setPreferredSize(new Dimension(36, 30));
        return button;
    }

    private JComponent analyzedWorkspace(
            JPanel cards, JLabel metrics, JLabel pips, ManaCurvePanel curve) {
        JPanel analysis = new JPanel(new BorderLayout(18, 0));
        analysis.setBorder(new EmptyBorder(7, 9, 7, 9));
        analysis.setPreferredSize(new Dimension(100, 108));
        JPanel labels = new JPanel(new GridLayout(2, 1, 0, 2));
        labels.setPreferredSize(new Dimension(290, 76));
        labels.add(metrics);
        labels.add(pips);
        analysis.add(labels, BorderLayout.WEST);
        analysis.add(curve, BorderLayout.CENTER);

        JPanel workspace = new JPanel(new BorderLayout());
        workspace.add(analysis, BorderLayout.NORTH);
        workspace.add(cardScroll(cards), BorderLayout.CENTER);
        return workspace;
    }

    private void refresh() {
        DraftPickState state = model.selected();
        int index = model.selectedIndex();
        int count = model.size();
        previous.setEnabled(index > 0);
        next.setEnabled(index >= 0 && index + 1 < count);
        previousPack.setEnabled(model.hasPreviousPack());
        nextPack.setEnabled(model.hasNextPack());
        clearCards();
        if (state == null) {
            position.setText("No draft loaded");
            showMessage(packCards, "Waiting for an Arena draft pack.");
            renderAnalyzed(poolCards, List.of(), Map.of(),
                    poolMetrics, poolPipMetrics, poolManaCurve,
                    "No drafted cards observed yet.");
            renderAnalyzed(deckCards, List.of(), Map.of(),
                    deckMetrics, pipMetrics, manaCurve,
                    "No submitted draft deck has been observed yet.");
            return;
        }
        if (!state.draftId().equals(activeDraftId)) {
            activeDraftId = state.draftId();
            activeSet = null;
            activeSetCards = List.of();
            activeSetCardsByArenaId = Map.of();
            ratingsByArenaId = Map.of();
            ratingsByName = Map.of();
        }

        Map<Long, CardInfo> cards = availableCards(state.cards());
        position.setText(state.positionLabel()
                + "   (" + (index + 1) + " of " + count + ")"
                + (state.selectedCardId() == null
                ? "   awaiting pick"
                : "   picked " + cardName(
                        state.selectedCardId(), cards)));
        inferSet(state);
        renderPack(state, cards);
        renderAnalyzed(poolCards, state.draftedPool(), cards,
                poolMetrics, poolPipMetrics, poolManaCurve,
                "No drafted cards observed yet.");
        renderAnalyzed(deckCards, state.mainDeck(), cards,
                deckMetrics, pipMetrics, manaCurve,
                "No submitted draft deck has been observed yet.");
        renderGroupedCounts(sideboardCards, state.sideboard(), cards, 2);
        refreshColumns();
    }

    private void renderPack(
            DraftPickState state,
            Map<Long, CardInfo> cards) {
        if (state.offeredCardIds().isEmpty()) {
            showMessage(packCards, "No offered cards observed.");
            return;
        }
        List<Long> ids = state.offeredCardIds().stream()
                .sorted(java.util.Comparator
                        .comparingInt((Long id) -> {
                            DraftTier value = tier(cards.get(id), id);
                            return value == null ? Integer.MAX_VALUE
                                    : value.ordinal();
                        })
                        .thenComparing(id -> cardName(id, cards),
                                String.CASE_INSENSITIVE_ORDER))
                .toList();
        boolean grouped = ids.stream()
                .anyMatch(id -> tier(cards.get(id), id) != null);
        DraftTier lastTier = null;
        int index = 0;
        for (long arenaId : ids) {
            CardInfo card = cards.get(arenaId);
            DraftTier cardTier = tier(card, arenaId);
            if (grouped && cardTier != lastTier) {
                index = ((index + 1) / 2) * 2;
                addHeader(packCards,
                        cardTier == null ? "Unranked" : cardTier + " tier",
                        2, index);
                index += 2;
                lastTier = cardTier;
            }
            addGridCard(packCards, new DraftCardChip(
                    card,
                    arenaId,
                    1,
                    cardTier,
                    state.selectedCardId() != null
                            && state.selectedCardId() == arenaId,
                    cardPreview), 2, index++);
        }
        addGridEnd(packCards, 2, index);
    }

    private void renderAnalyzed(
            JPanel target,
            List<DraftCardCount> counts,
            Map<Long, CardInfo> cards,
            JLabel metrics,
            JLabel pips,
            ManaCurvePanel curve,
            String emptyMessage) {
        List<DraftCardCount> ordered = ordering.sort(counts, cards);
        renderGroupedCounts(target, ordered, cards, 3);
        DraftDeckAnalysis.Summary summary =
                deckAnalysis.analyze(counts, cards);
        metrics.setText("<html><b>" + summary.totalCards()
                + " cards</b>&nbsp;&nbsp; • &nbsp;&nbsp;"
                + summary.creatures() + " creatures"
                + "&nbsp;&nbsp; • &nbsp;&nbsp;"
                + summary.removal() + " removal</html>");
        pips.setText("Color pips:  "
                + pipLabel(summary.colorPips()));
        curve.setCurve(summary.manaCurve());
        if (counts == null || counts.isEmpty()) {
            showMessage(target, emptyMessage);
        }
    }

    private String pipLabel(Map<String, Integer> pips) {
        return List.of("W", "U", "B", "R", "G").stream()
                .map(color -> color + " " + pips.getOrDefault(color, 0))
                .collect(java.util.stream.Collectors.joining("   "));
    }

    private void renderGroupedCounts(
            JPanel target,
            List<DraftCardCount> counts,
            Map<Long, CardInfo> cards,
            int columns) {
        String lastGroup = null;
        int index = 0;
        for (DraftCardCount count : ordering.sort(counts, cards)) {
            CardInfo card = cards.get(count.arenaId());
            String group = ordering.typeGroup(card);
            if (!group.equals(lastGroup)) {
                index = ((index + columns - 1) / columns) * columns;
                addHeader(target, group, columns, index);
                index += columns;
                lastGroup = group;
            }
            addGridCard(target, new DraftCardChip(
                    card,
                    count.arenaId(),
                    count.quantity(),
                    tier(card, count.arenaId()),
                    false,
                    cardPreview), columns, index++);
        }
        addGridEnd(target, columns, index);
    }

    private void inferSet(DraftPickState state) {
        if (activeSet != null) return;
        List<CardInfo> offered = state.offeredCardIds().stream()
                .map(state.cards()::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        setResolver.infer(offered).ifPresent(code -> {
            DraftSet set = setSelector.findByCode(code);
            if (set != null) activateSet(set, false);
        });
    }

    private void addHeader(
            JPanel target, String text, int columns, int index) {
        GridBagConstraints layout = constraints(0, index / columns, columns);
        layout.fill = GridBagConstraints.HORIZONTAL;
        JLabel header = new JLabel(text);
        header.setFont(header.getFont().deriveFont(Font.BOLD, 14f));
        header.setBorder(new EmptyBorder(8, 3, 3, 3));
        target.add(header, layout);
    }

    private void addGridCard(
            JPanel target, JComponent card, int columns, int index) {
        GridBagConstraints layout = constraints(
                index % columns, index / columns, 1);
        layout.weightx = 1;
        layout.fill = GridBagConstraints.HORIZONTAL;
        layout.insets = new Insets(3, 3, 3, 3);
        target.add(card, layout);
    }

    private GridBagConstraints constraints(int x, int y, int width) {
        GridBagConstraints result = new GridBagConstraints();
        result.gridx = x;
        result.gridy = y;
        result.gridwidth = width;
        result.anchor = GridBagConstraints.NORTHWEST;
        return result;
    }

    private void addGridEnd(JPanel target, int columns, int index) {
        GridBagConstraints layout = constraints(
                0, index / columns + 1, columns);
        layout.weighty = 1;
        layout.fill = GridBagConstraints.VERTICAL;
        target.add(Box.createVerticalGlue(), layout);
    }

    private void loadSets() {
        status.setText("Loading Scryfall set catalog…");
        catalog.sets().whenComplete((sets, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (error != null) {
                        status.setText("Could not load set catalog: "
                                + rootMessage(error));
                        return;
                    }
                    changingSet = true;
                    try {
                        setSelector.setSets(sets);
                        setSelector.setSelectedItem(null);
                    } finally {
                        changingSet = false;
                    }
                    status.setText("Loaded " + sets.size()
                            + " Scryfall sets; waiting to infer the draft set");
                    DraftPickState state = model.selected();
                    if (state != null) inferSet(state);
                }));
    }

    private void activateSet(DraftSet set, boolean manual) {
        if (set == null) return;
        activeSet = set;
        changingSet = true;
        try {
            setSelector.setSelectedItem(set);
        } finally {
            changingSet = false;
        }
        activeSetCards = List.of();
        loadRatings(set.code());
        copyRankingRequest.setEnabled(false);
        importRanking.setEnabled(false);
        status.setText((manual ? "Selected " : "Inferred ")
                + set.displayName() + "; loading cards…");
        catalog.cards(set.code()).whenComplete((cards, error) ->
                SwingUtilities.invokeLater(() -> {
                    if (activeSet == null
                            || !activeSet.code().equals(set.code())) return;
                    if (error != null) {
                        status.setText("Could not load "
                                + set.displayName() + ": " + rootMessage(error));
                        return;
                    }
                    activeSetCards = cards;
                    Map<Long, CardInfo> byArenaId = new LinkedHashMap<>();
                    for (CardInfo card : cards) {
                        if (card.getArenaId() != null && card.getArenaId() > 0) {
                            byArenaId.put(card.getArenaId(), card);
                        }
                    }
                    activeSetCardsByArenaId = Map.copyOf(byArenaId);
                    copyRankingRequest.setEnabled(true);
                    importRanking.setEnabled(true);
                    status.setText(set.displayName() + ": "
                            + cards.size() + " Arena cards loaded; "
                            + ratingsByArenaId.size() + " ratings available");
                    refresh();
                }));
    }

    private void loadRatings(String setCode) {
        List<DraftCardRating> ratings = rankingRepository.load(setCode);
        Map<Long, DraftCardRating> byId = new LinkedHashMap<>();
        Map<String, DraftCardRating> byName = new LinkedHashMap<>();
        for (DraftCardRating rating : ratings) {
            if (rating.arenaId() > 0) byId.put(rating.arenaId(), rating);
            if (!rating.cardName().isBlank()) {
                byName.put(rating.cardName().toLowerCase(Locale.ROOT), rating);
            }
        }
        ratingsByArenaId = Map.copyOf(byId);
        ratingsByName = Map.copyOf(byName);
    }

    private DraftTier tier(CardInfo card, long arenaId) {
        DraftCardRating rating = ratingsByArenaId.get(arenaId);
        if (rating == null && card != null && card.getName() != null) {
            rating = ratingsByName.get(card.getName().toLowerCase(Locale.ROOT));
        }
        return rating == null ? null : rating.tier();
    }

    private void copyPickRequest() {
        DraftPickState state = model.selected();
        if (state == null) return;
        clipboard(pickExporter.export(state, ratingsByArenaId));
        status.setText("Draft-pick AI-speak copied to clipboard");
    }

    private void copyRankingRequest() {
        if (activeSet == null || activeSetCards.isEmpty()) return;
        clipboard(rankingExporter.export(activeSet, activeSetCards));
        status.setText("Complete " + activeSet.code().toUpperCase()
                + " ranking request copied; paste it into ChatGPT");
    }

    private void importRanking() {
        if (activeSet == null || activeSetCards.isEmpty()) return;
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            List<DraftCardRating> ratings =
                    rankingParser.parse(value == null ? "" : value.toString());
            int expected = (int) activeSetCards.stream()
                    .filter(card -> card.getArenaId() != null
                            && card.getArenaId() > 0)
                    .count();
            long covered = ratings.stream()
                    .map(DraftCardRating::arenaId)
                    .filter(id -> id > 0)
                    .distinct().count();
            if (covered < expected) {
                throw new IllegalArgumentException(
                        "Ranking is incomplete: " + covered
                                + " of " + expected + " Arena cards");
            }
            rankingRepository.save(activeSet.code(), ratings);
            loadRatings(activeSet.code());
            refresh();
            status.setText("Imported and saved " + ratings.size()
                    + " rankings for " + activeSet.displayName());
        } catch (Exception error) {
            status.setText("Could not import ranking: " + rootMessage(error));
        }
    }

    private void clipboard(String value) {
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(value), null);
    }

    private String cardName(long id, Map<Long, CardInfo> cards) {
        CardInfo card = cards.get(id);
        return card == null || card.getName() == null
                || card.getName().isBlank()
                ? "Arena card " + id : card.getName();
    }

    private Map<Long, CardInfo> availableCards(
            Map<Long, CardInfo> observed) {
        if (activeSetCardsByArenaId.isEmpty()) return observed;
        Map<Long, CardInfo> result = new LinkedHashMap<>(activeSetCardsByArenaId);
        result.putAll(observed);
        return Map.copyOf(result);
    }

    private void clearCards() {
        packCards.removeAll();
        poolCards.removeAll();
        deckCards.removeAll();
        sideboardCards.removeAll();
    }

    private void refreshColumns() {
        for (JPanel panel : List.of(
                packCards, poolCards, deckCards, sideboardCards)) {
            panel.revalidate();
            panel.repaint();
        }
    }

    private void showMessage(JPanel panel, String text) {
        JLabel label = new JLabel(text);
        label.setBorder(new EmptyBorder(12, 10, 12, 10));
        panel.add(label, constraints(0, 0, 3));
    }

    private JComponent titled(String title, JComponent content) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    private JScrollPane cardScroll(JPanel cards) {
        JScrollPane scroll = new JScrollPane(cards);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private static JPanel cardGrid() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(new EmptyBorder(7, 7, 7, 7));
        return panel;
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null
                ? current.getClass().getSimpleName()
                : current.getMessage();
    }
}
