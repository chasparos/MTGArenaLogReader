package app.deckplanner.ui;

import app.deckplanner.candidate.CandidateModel;
import app.deckplanner.candidate.CandidateWorkspaceState;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppColors;
import app.ui.CardCollectionSurface;
import app.ui.CardDragTransfer;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.IOException;
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
    private final JProgressBar busy = new JProgressBar();
    private final JLabel operationStatus = new JLabel(" ");
    private final JComboBox<String> candidateSets = new JComboBox<>();
    private final JButton saveSet = new JButton("Save set");
    private final JButton loadSet = new JButton("Load set");
    private final JButton editNote = new JButton("Edit note");
    private final JButton exportAi = new JButton("AI export");

    private Runnable importAction = () -> { };
    private Runnable magicSortAction = () -> { };
    private Consumer<Optional<String>> selectionAction = ignored -> { };
    private Consumer<String> alternateArtAction = ignored -> { };
    private java.util.function.Function<String, CardInfo> preferredPrinting = ignored -> null;
    private Supplier<List<String>> candidateSetNames = List::of;
    private Consumer<String> saveSetAction = ignored -> { };
    private Consumer<String> loadSetAction = ignored -> { };
    private Runnable exportAiAction = () -> { };
    private String noteSetName = "";
    private String candidateSetNote = "";

    private CandidateModel model;
    private CandidateWorkspaceState workspaceState = CandidateWorkspaceState.transientState();
    private List<CandidateModel.Entry> currentEntries = List.of();
    private final Set<String> collapsedCategories = new LinkedHashSet<>();
    private static final DataFlavor CATEGORY_FLAVOR = categoryFlavor();

    public CandidatePanel() {
        super(new BorderLayout(3, 3));
        setBorder(new EmptyBorder(2, 2, 2, 2));
        JLabel title = new JLabel("Candidates");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        surface.setTransferSource("candidates");
        surface.setWrapGaps(1, 1);
        surface.setDropHandler(this::handleDrop);
        surface.setGroupMoveHandler((source, target, after) ->
                workspaceState.moveCategoryRelative(source, target, after));
        surface.setDragImageProvider(this::dragImage);
        surface.setSelectionListener(selection -> {
            updateActions();
            selectionAction.accept(selection);
        });
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(4, 4));
        JPanel importLine = new JPanel(new BorderLayout(6, 2));
        importLine.add(importDeck, BorderLayout.WEST);
        busy.setIndeterminate(true);
        busy.setBorderPainted(false);
        busy.setVisible(false);
        importLine.add(busy, BorderLayout.CENTER);
        operationStatus.setFont(operationStatus.getFont().deriveFont(Font.PLAIN, 10f));
        importLine.add(operationStatus, BorderLayout.SOUTH);
        actions.add(importLine, BorderLayout.NORTH);

        JPanel setActions = new JPanel(new BorderLayout(4, 4));
        candidateSets.setEditable(true);
        setActions.add(candidateSets, BorderLayout.CENTER);
        JPanel setButtons = new JPanel(new GridLayout(1, 3, 4, 4));
        setButtons.add(saveSet);
        setButtons.add(loadSet);
        setButtons.add(editNote);
        setButtons.add(exportAi);
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
        editNote.addActionListener(event -> editCandidateSetNote());
        exportAi.addActionListener(event -> exportAiAction.run());
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

    public void setBusy(boolean active, String message) {
        assertEdt();
        busy.setVisible(active);
        importDeck.setEnabled(!active);
        operationStatus.setText(message == null || message.isBlank() ? " " : message);
        revalidate();
        repaint();
    }

    public String operationStatus() { return operationStatus.getText(); }

    public void setMagicSortAction(Runnable magicSortAction) {
        this.magicSortAction = magicSortAction == null ? () -> { } : magicSortAction;
    }

    public void setSelectionAction(Consumer<Optional<String>> selectionAction) {
        this.selectionAction = selectionAction == null ? ignored -> { } : selectionAction;
    }
    public void setAlternateArtAction(Consumer<String> alternateArtAction,
                                      java.util.function.Function<String, CardInfo> preferredPrinting) {
        this.alternateArtAction = alternateArtAction == null ? ignored -> { } : alternateArtAction;
        this.preferredPrinting = preferredPrinting == null ? ignored -> null : preferredPrinting;
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

    public String candidateSetNoteFor(String setName) {
        if (setName == null || !setName.strip().equals(noteSetName)) return "";
        return candidateSetNote;
    }

    public void setCandidateSetNote(String setName, String note) {
        noteSetName = setName == null ? "" : setName.strip();
        candidateSetNote = note == null ? "" : note;
    }

    private void editCandidateSetNote() {
        selectedSetName().ifPresent(name -> {
            JTextArea editor = new JTextArea(candidateSetNoteFor(name), 14, 52);
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);

            JDialog dialog = new JDialog(
                    SwingUtilities.getWindowAncestor(this),
                    "Candidate Set note — " + name,
                    Dialog.ModalityType.MODELESS);
            dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            JPanel content = new JPanel(new BorderLayout(6, 6));
            content.setBorder(new EmptyBorder(8, 8, 8, 8));
            content.add(new JScrollPane(editor), BorderLayout.CENTER);
            JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
            JButton cancel = new JButton("Cancel");
            JButton save = new JButton("Save note");
            buttons.add(cancel);
            buttons.add(save);
            content.add(buttons, BorderLayout.SOUTH);
            cancel.addActionListener(event -> dialog.dispose());
            save.addActionListener(event -> {
                setCandidateSetNote(name, editor.getText());
                saveSetAction.accept(name);
                refreshCandidateSetNames();
                dialog.dispose();
            });
            dialog.setContentPane(content);
            dialog.pack();
            dialog.setLocationRelativeTo(this);
            dialog.setVisible(true);
            SwingUtilities.invokeLater(editor::requestFocusInWindow);
        });
    }

    public void setAiExportAction(Runnable action) {
        exportAiAction = action == null ? () -> { } : action;
    }

    public Optional<String> currentCandidateSetName() {
        return selectedSetName();
    }

    public String currentCandidateSetNote() {
        return selectedSetName().map(this::candidateSetNoteFor).orElse("");
    }

    public CandidateWorkspaceState.Snapshot workspaceSnapshot() {
        return workspaceState.snapshot();
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
                    "Uncategorized (" + uncategorized.size() + ")", uncategorized, null,
                    collapsedCategories.contains(CandidateWorkspaceState.UNCATEGORIZED)));
        }
        List<CandidateRow> unavailable =
                rowsByCategory.getOrDefault(CandidateWorkspaceState.UNAVAILABLE, List.of());
        if (!unavailable.isEmpty()) {
            groups.add(new CardCollectionSurface.Group(
                    CandidateWorkspaceState.UNAVAILABLE,
                    "Unavailable (" + unavailable.size() + ")", unavailable, null,
                    collapsedCategories.contains(CandidateWorkspaceState.UNAVAILABLE)));
        }

        JButton addCategory = circularButton("+", "Add category");
        addCategory.addActionListener(event -> {
            List<String> selected = selectedIdentities();
            if (selected.isEmpty()) {
                operationStatus.setText("Select cards or drop cards here to create a category.");
                return;
            }
            createCategoryFor(selected);
        });
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 1));
        footer.setName("candidate-new-category-drop-zone");
        footer.setOpaque(false);
        footer.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        NewCategoryTransferHandler newCategoryTransfer = new NewCategoryTransferHandler();
        footer.setTransferHandler(newCategoryTransfer);
        addCategory.setTransferHandler(newCategoryTransfer);
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

        JButton collapse = circularButton(
                collapsedCategories.contains(category.id()) ? "▸" : "▾",
                collapsedCategories.contains(category.id()) ? "Expand category" : "Collapse category");
        collapse.addActionListener(event -> {
            if (!collapsedCategories.add(category.id())) collapsedCategories.remove(category.id());
            rebuild();
        });

        JLabel dragHandle = new JLabel("⋮⋮");
        dragHandle.setToolTipText("Drag to reorder category");
        dragHandle.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        CategoryTransferHandler categoryTransfer = new CategoryTransferHandler(category.id());
        dragHandle.setTransferHandler(categoryTransfer);
        buttons.setTransferHandler(categoryTransfer);
        dragHandle.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent event) {
                dragHandle.getTransferHandler().exportAsDrag(
                        dragHandle, event, TransferHandler.MOVE);
            }
        });

        JButton removeCategory = circularButton("−", "Remove category");
        removeCategory.addActionListener(event ->
                workspaceState.removeCategory(category.id(), currentEntries));

        buttons.add(collapse);
        buttons.add(dragHandle);
        buttons.add(removeCategory);
        return new CardCollectionSurface.Group(
                category.id(), category.name() + " (" + rows.size() + ")", rows, buttons,
                collapsedCategories.contains(category.id()));
    }

    private void createCategoryFor(List<String> identities) {
        if (identities == null || identities.isEmpty()) return;
        String name = JOptionPane.showInputDialog(this, "Category name:", "New candidate category",
                JOptionPane.PLAIN_MESSAGE);
        if (name == null || name.isBlank()) return;
        CandidateWorkspaceState.Category created = workspaceState.addCategory(name);
        workspaceState.assign(identities, created.id());
    }

    private static DataFlavor categoryFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=" + String.class.getName());
        } catch (ClassNotFoundException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private final class CategoryTransferHandler extends TransferHandler {
        private final String categoryId;
        CategoryTransferHandler(String categoryId) { this.categoryId = categoryId; }

        @Override protected Transferable createTransferable(JComponent component) {
            return new java.awt.datatransfer.StringSelection(categoryId) {
                @Override public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { CATEGORY_FLAVOR };
                }
                @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return CATEGORY_FLAVOR.equals(flavor);
                }
                @Override public Object getTransferData(DataFlavor flavor)
                        throws java.awt.datatransfer.UnsupportedFlavorException {
                    if (!CATEGORY_FLAVOR.equals(flavor)) {
                        throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                    }
                    return categoryId;
                }
            };
        }

        @Override public int getSourceActions(JComponent component) { return MOVE; }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(CATEGORY_FLAVOR);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                String sourceId = (String) support.getTransferable().getTransferData(CATEGORY_FLAVOR);
                workspaceState.moveCategoryBefore(sourceId, categoryId);
                return true;
            } catch (Exception error) {
                return false;
            }
        }
    }

    private final class NewCategoryTransferHandler extends TransferHandler {
        @Override public boolean canImport(TransferSupport support) {
            return support.isDataFlavorSupported(CardDragTransfer.FLAVOR);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                CardDragTransfer.Payload payload = CardDragTransfer.read(support.getTransferable());
                if (payload.identities().isEmpty()) return false;
                if (!"candidates".equals(payload.source()) && model != null) {
                    model.add(payload.identities());
                }
                createCategoryFor(payload.identities());
                return true;
            } catch (Exception error) {
                return false;
            }
        }
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
        boolean hasSetName = candidateSets.getEditor().getItem() != null
                && !candidateSets.getEditor().getItem().toString().isBlank();
        loadSet.setEnabled(candidateSets.getItemCount() > 0 || hasSetName);
        editNote.setEnabled(hasSetName);
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
            stale = entry.stale();
            CardInfo favorite = preferredPrinting.apply(identity);
            card = stale ? null : (favorite != null ? favorite : entry.resolvedCard().orElse(null));
            setBorder(new EmptyBorder(0, 0, 0, 0));
            setOpaque(false);
            if (stale) {
                JLabel label = new JLabel("Unavailable card — stale; keep or remove");
                label.setBorder(new EmptyBorder(6, 8, 6, 8));
                content = label;
            } else {
                ReplayCardChip chip = new ReplayCardChip(card, false, 1.35f);
                chip.compactToContentWidth();
                chip.setToolTipText((entry.legal() ? "" : "Illegal in selected format — ")
                        + (card.getName() == null ? "Unknown card" : card.getName()));
                content = chip;
            }
            add(content, BorderLayout.CENTER);
            if (!stale && !entry.legal()) {
                JLabel illegal = new JLabel("ILLEGAL");
                illegal.setForeground(new Color(0xF06A63));
                illegal.setFont(illegal.getFont().deriveFont(Font.BOLD, 10f));
                illegal.setBorder(new EmptyBorder(0, 5, 0, 3));
                add(illegal, BorderLayout.EAST);
            }
            Dimension contentSize = content.getPreferredSize();
            int extraWidth = !stale && !entry.legal() ? 54 : 0;
            Dimension rowSize = new Dimension(
                    Math.max(1, contentSize.width + extraWidth),
                    Math.max(1, contentSize.height));
            setPreferredSize(rowSize);
            setMinimumSize(rowSize);
            setMaximumSize(rowSize);
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
                        : new EmptyBorder(1, 1, 1, 1));
            }
            repaint();
        }
    }
}
