package app.deckplanner.ui;

import app.deckplanner.candidate.CandidateModel;
import app.deckplanner.candidate.CandidateWorkspaceState;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppColors;
import app.ui.CardCollectionSurface;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

/** DP-06 candidate workspace with editable persisted planning categories. */
public final class CandidatePanel extends JPanel {
    private final CardCollectionSurface surface = new CardCollectionSurface();
    private final JScrollPane scroll = surface.createScrollPane();
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear");
    private final JButton magicSort = new JButton("Normal MTG sort");
    private final JButton importDeck = new JButton("Import deck");
    private final JComboBox<String> candidateSets = new JComboBox<>();
    private final JButton saveSet = new JButton("Save set");
    private final JButton loadSet = new JButton("Load set");

    private Runnable importAction = () -> { };
    private Runnable magicSortAction = () -> { };
    private Consumer<Optional<String>> selectionAction = ignored -> { };
    private Supplier<List<String>> candidateSetNames = List::of;
    private Consumer<String> saveSetAction = ignored -> { };
    private Consumer<String> loadSetAction = ignored -> { };

    private CandidateModel model;
    private CandidateWorkspaceState workspaceState = CandidateWorkspaceState.transientState();
    private List<CandidateModel.Entry> currentEntries = List.of();

    public CandidatePanel() {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Candidates");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        surface.setTransferSource("candidates");
        surface.setDropHandler(this::handleDrop);
        surface.setDragImageProvider(this::dragImage);
        surface.setSelectionListener(selection -> {
            updateActions();
            selectionAction.accept(selection);
        });
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(4, 4));
        actions.add(importDeck, BorderLayout.NORTH);

        JPanel setActions = new JPanel(new BorderLayout(4, 4));
        candidateSets.setEditable(true);
        setActions.add(candidateSets, BorderLayout.CENTER);
        JPanel setButtons = new JPanel(new GridLayout(1, 2, 4, 4));
        setButtons.add(saveSet);
        setButtons.add(loadSet);
        setActions.add(setButtons, BorderLayout.EAST);
        actions.add(setActions, BorderLayout.CENTER);

        JPanel candidateActions = new JPanel(new GridLayout(1, 3, 4, 4));
        candidateActions.add(magicSort);
        candidateActions.add(remove);
        candidateActions.add(clear);
        actions.add(candidateActions, BorderLayout.SOUTH);
        add(actions, BorderLayout.SOUTH);

        remove.addActionListener(event -> {
            if (model != null) model.remove(selectedIdentities());
        });
        clear.addActionListener(event -> { if (model != null) model.clear(); });
        magicSort.addActionListener(event -> magicSortAction.run());
        importDeck.addActionListener(event -> importAction.run());
        saveSet.addActionListener(event -> selectedSetName().ifPresent(saveSetAction));
        loadSet.addActionListener(event -> selectedSetName().ifPresent(loadSetAction));
        updateActions();
        refreshTheme();
    }

    public void bind(CandidateModel model, ToIntFunction<CardInfo> ignoredQuantitySource) {
        bind(model, CandidateWorkspaceState.transientState(), ignoredQuantitySource);
    }

    public void bind(CandidateModel model, CandidateWorkspaceState workspaceState,
                     ToIntFunction<CardInfo> ignoredQuantitySource) {
        this.model = Objects.requireNonNull(model);
        this.workspaceState = Objects.requireNonNull(workspaceState);
        this.workspaceState.addListener(() -> {
            if (SwingUtilities.isEventDispatchThread()) rebuild();
            else SwingUtilities.invokeLater(this::rebuild);
        });
    }

    private void handleDrop(String source, List<String> identities,
                            int insertionIndex, String groupId) {
        if (model == null || identities == null || identities.isEmpty()) return;
        if ("candidates".equals(source)) moveDisplayedCandidates(identities, insertionIndex);
        else model.addAt(identities, insertionIndex);

        if (groupId != null && !CandidateWorkspaceState.UNAVAILABLE.equals(groupId)) {
            workspaceState.assign(identities, groupId);
        }
    }

    private void moveDisplayedCandidates(List<String> identities, int insertionIndex) {
        if (model == null || identities == null || identities.isEmpty()) return;
        ArrayList<String> displayed = new ArrayList<>(surface.identities());
        LinkedHashSet<String> moving = new LinkedHashSet<>(identities);
        List<String> orderedMoving = displayed.stream().filter(moving::contains).toList();
        int bounded = Math.max(0, Math.min(displayed.size(), insertionIndex));
        int removedBeforeTarget = 0;
        for (int index = 0; index < bounded; index++) {
            if (moving.contains(displayed.get(index))) removedBeforeTarget++;
        }
        displayed.removeIf(moving::contains);
        int target = Math.max(0, Math.min(displayed.size(), bounded - removedBeforeTarget));
        displayed.addAll(target, orderedMoving);
        model.reorder(displayed);
    }

    private Image dragImage(List<String> identities) {
        if (identities == null || identities.isEmpty()) return null;
        LinkedHashSet<String> wanted = new LinkedHashSet<>(identities);
        List<CardInfo> cards = currentEntries.stream()
                .filter(entry -> wanted.contains(entry.identity()) && entry.card().isPresent())
                .map(entry -> entry.card().orElseThrow().group().preferredPrinting())
                .toList();
        return ReplayCardChip.createDragImage(cards);
    }

    public void setImportAction(Runnable importAction) {
        this.importAction = importAction == null ? () -> { } : importAction;
    }

    public void setMagicSortAction(Runnable magicSortAction) {
        this.magicSortAction = magicSortAction == null ? () -> { } : magicSortAction;
    }

    public void setSelectionAction(Consumer<Optional<String>> selectionAction) {
        this.selectionAction = selectionAction == null ? ignored -> { } : selectionAction;
    }

    public void setCandidateSetActions(Supplier<List<String>> names,
                                       Consumer<String> save,
                                       Consumer<String> load) {
        candidateSetNames = names == null ? List::of : names;
        saveSetAction = save == null ? ignored -> { } : save;
        loadSetAction = load == null ? ignored -> { } : load;
        refreshCandidateSetNames();
    }

    public void refreshCandidateSetNames() {
        Object selected = candidateSets.getEditor().getItem();
        candidateSets.removeAllItems();
        for (String name : candidateSetNames.get()) candidateSets.addItem(name);
        if (selected != null) candidateSets.getEditor().setItem(selected);
        updateActions();
    }

    private Optional<String> selectedSetName() {
        Object value = candidateSets.isEditable()
                ? candidateSets.getEditor().getItem() : candidateSets.getSelectedItem();
        if (value == null || value.toString().isBlank()) return Optional.empty();
        return Optional.of(value.toString().strip());
    }

    public void setEntries(List<CandidateModel.Entry> entries) {
        assertEdt();
        currentEntries = List.copyOf(entries == null ? List.of() : entries);
        workspaceState.synchronize(currentEntries);
        rebuild();
    }

    private void rebuild() {
        assertEdt();
        LinkedHashMap<String, List<CandidateRow>> rowsByCategory = new LinkedHashMap<>();
        for (CandidateWorkspaceState.Category category : workspaceState.categories()) {
            rowsByCategory.put(category.id(), new ArrayList<>());
        }
        rowsByCategory.put(CandidateWorkspaceState.UNCATEGORIZED, new ArrayList<>());
        rowsByCategory.put(CandidateWorkspaceState.UNAVAILABLE, new ArrayList<>());

        for (CandidateModel.Entry entry : currentEntries) {
            CandidateRow row = new CandidateRow(entry);
            String category = workspaceState.categoryFor(entry);
            rowsByCategory.computeIfAbsent(category, ignored -> new ArrayList<>()).add(row);
        }

        List<CardCollectionSurface.Group> groups = new ArrayList<>();
        List<CandidateWorkspaceState.Category> categories = workspaceState.categories();
        for (int i = 0; i < categories.size(); i++) {
            CandidateWorkspaceState.Category category = categories.get(i);
            List<CandidateRow> rows = rowsByCategory.getOrDefault(category.id(), List.of());
            if (rows.isEmpty()) continue;
            groups.add(group(category, rows, i, categories.size()));
        }
        List<CandidateRow> uncategorized =
                rowsByCategory.getOrDefault(CandidateWorkspaceState.UNCATEGORIZED, List.of());
        if (!uncategorized.isEmpty()) {
            groups.add(new CardCollectionSurface.Group(
                    CandidateWorkspaceState.UNCATEGORIZED,
                    "Uncategorized (" + uncategorized.size() + ")", uncategorized));
        }
        List<CandidateRow> unavailable =
                rowsByCategory.getOrDefault(CandidateWorkspaceState.UNAVAILABLE, List.of());
        if (!unavailable.isEmpty()) {
            groups.add(new CardCollectionSurface.Group(
                    CandidateWorkspaceState.UNAVAILABLE,
                    "Unavailable (" + unavailable.size() + ")", unavailable));
        }

        JButton addCategory = circularButton("+", "Add category");
        addCategory.addActionListener(event -> {
            String name = JOptionPane.showInputDialog(this, "Category name:", "Add candidate category",
                    JOptionPane.PLAIN_MESSAGE);
            if (name != null && !name.isBlank()) workspaceState.addCategory(name);
        });
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 3));
        footer.setOpaque(false);
        footer.add(addCategory);
        surface.setFooter(footer);
        surface.setGroups(groups);
        updateActions();
        syncScrollbarEnabled(scroll.getVerticalScrollBar());
    }

    private CardCollectionSurface.Group group(CandidateWorkspaceState.Category category,
                                               List<CandidateRow> rows,
                                               int categoryIndex, int categoryCount) {
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 3, 2));
        buttons.setOpaque(false);
        JButton up = circularButton("↑", "Move category up");
        JButton down = circularButton("↓", "Move category down");
        JButton removeCategory = circularButton("−", "Remove category");
        up.setEnabled(categoryIndex > 0);
        down.setEnabled(categoryIndex + 1 < categoryCount);
        up.addActionListener(event -> workspaceState.moveCategory(category.id(), -1));
        down.addActionListener(event -> workspaceState.moveCategory(category.id(), 1));
        removeCategory.addActionListener(event ->
                workspaceState.removeCategory(category.id(), currentEntries));
        buttons.add(up);
        buttons.add(down);
        buttons.add(removeCategory);
        return new CardCollectionSurface.Group(
                category.id(), category.name() + " (" + rows.size() + ")", rows, buttons);
    }

    private static JButton circularButton(String text, String tooltip) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setPreferredSize(new Dimension(24, 24));
        button.setMinimumSize(new Dimension(24, 24));
        button.setMaximumSize(new Dimension(24, 24));
        button.setFocusable(true);
        return button;
    }

    public List<String> identities() { return surface.identities(); }
    Optional<String> selectedIdentity() { return surface.selectedIdentity(); }
    List<String> selectedIdentities() { return surface.selectedIdentities(); }
    JComponent candidateSurface() { return surface; }
    List<JComponent> candidateRows() { return surface.rowComponents(); }
    JScrollPane candidateScrollPane() { return scroll; }

    private void updateActions() {
        remove.setEnabled(!surface.selectedIdentities().isEmpty());
        clear.setEnabled(!surface.identities().isEmpty());
        magicSort.setEnabled(surface.identities().size() > 1);
        loadSet.setEnabled(candidateSets.getItemCount() > 0
                || (candidateSets.getEditor().getItem() != null
                    && !candidateSets.getEditor().getItem().toString().isBlank()));
    }

    private void refreshTheme() {
        Color background = AppColors.color("Panel.background", new Color(0x202328));
        setBackground(background);
        setForeground(AppColors.color("Label.foreground", Color.WHITE));
        surface.setBackground(background);
        scroll.getViewport().setBackground(background);
    }

    private static void syncScrollbarEnabled(JScrollBar scrollBar) {
        BoundedRangeModel range = scrollBar.getModel();
        scrollBar.setEnabled(range.getExtent() < range.getMaximum() - range.getMinimum());
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Candidate workspace must be used on EDT");
        }
    }

    private final class CandidateRow extends JPanel implements CardCollectionSurface.Row {
        private final String identity;
        private final CardInfo card;
        private final boolean stale;
        private final JComponent content;

        CandidateRow(CandidateModel.Entry entry) {
            super(new BorderLayout());
            identity = entry.identity();
            stale = entry.card().isEmpty();
            card = stale ? null : entry.card().get().group().preferredPrinting();
            setPreferredSize(new Dimension(330, 60));
            setMinimumSize(new Dimension(270, 60));
            setMaximumSize(new Dimension(460, 60));
            setBorder(new EmptyBorder(3, 3, 3, 3));
            setOpaque(false);
            if (stale) {
                JLabel label = new JLabel("Unavailable card — stale; keep or remove");
                label.setBorder(new EmptyBorder(6, 8, 6, 8));
                content = label;
            } else {
                content = new ReplayCardChip(card, false, 1.35f);
            }
            add(content, BorderLayout.CENTER);
            setSelected(false);
        }

        @Override public String identity() { return identity; }
        @Override public JComponent component() { return this; }
        CardInfo card() { return card; }
        boolean stale() { return stale; }

        @Override public void setSelected(boolean selected) {
            Color base = AppColors.color("Panel.background", new Color(0x202328));
            Color outline = AppColors.color("App.accent", new Color(0xD6A84B));
            setBackground(base);
            if (content instanceof ReplayCardChip chip) {
                chip.setSelected(false);
                chip.paintColoredOutline(selected ? outline : null);
            } else {
                content.setBackground(base);
                content.setForeground(AppColors.color("Label.foreground", Color.WHITE));
                content.setOpaque(true);
                setBorder(selected
                        ? BorderFactory.createLineBorder(outline, 2, true)
                        : new EmptyBorder(3, 3, 3, 3));
            }
            repaint();
        }
    }
}
