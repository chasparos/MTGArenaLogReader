package app.deckplanner.ui;

import app.deckplanner.consideration.UnderConsiderationModel;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Ordered DP-06 candidate workspace with recoverable stale entries. */
public final class UnderConsiderationPanel extends JPanel {
    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private final JList<Row> list = new JList<>(rows);
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear");
    private final JButton magicSort = new JButton("Normal MTG sort");
    private final JButton importDeck = new JButton("Import deck");
    private Runnable importAction = () -> { };
    private Runnable magicSortAction = () -> { };
    private UnderConsiderationModel model;

    public UnderConsiderationPanel() {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Under consideration");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.setFixedCellHeight(42);
        JScrollPane scroll = new JScrollPane(list);
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(4, 4));
        actions.add(importDeck, BorderLayout.NORTH);
        JPanel candidateActions = new JPanel(new GridLayout(1, 3, 4, 4));
        candidateActions.add(magicSort);
        candidateActions.add(remove);
        candidateActions.add(clear);
        actions.add(candidateActions, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        remove.addActionListener(event -> selectedIdentity().ifPresent(model::remove));
        clear.addActionListener(event -> { if (model != null) model.clear(); });
        magicSort.addActionListener(event -> magicSortAction.run());
        importDeck.addActionListener(event -> importAction.run());
        list.setDropMode(DropMode.INSERT);
        list.setTransferHandler(new CandidateTransferHandler());
        if (!GraphicsEnvironment.isHeadless()) list.setDragEnabled(true);
        list.addListSelectionListener(event -> updateActions());
        updateActions();
        refreshTheme();
    }

    public void bind(UnderConsiderationModel model, ToIntFunction<CardInfo> ignoredQuantitySource) {
        this.model = Objects.requireNonNull(model);
    }

    public void setImportAction(Runnable importAction) {
        this.importAction = importAction == null ? () -> { } : importAction;
    }

    public void setMagicSortAction(Runnable magicSortAction) {
        this.magicSortAction = magicSortAction == null ? () -> { } : magicSortAction;
    }

    public void setEntries(List<UnderConsiderationModel.Entry> entries) {
        assertEdt();
        String selected = selectedIdentity().orElse(null);
        rows.clear();
        for (UnderConsiderationModel.Entry entry : entries) {
            if (entry.card().isPresent()) {
                CardInfo card = entry.card().get().group().preferredPrinting();
                rows.addElement(new Row(entry.identity(), card, false));
            } else {
                rows.addElement(new Row(entry.identity(), null, true));
            }
        }
        if (selected != null) {
            for (int index = 0; index < rows.size(); index++) {
                if (selected.equals(rows.get(index).identity())) {
                    list.setSelectedIndex(index);
                    break;
                }
            }
        }
        updateActions();
    }

    public List<String> identities() {
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> rows.get(index).identity()).toList();
    }

    private java.util.Optional<String> selectedIdentity() {
        Row row = list.getSelectedValue();
        return row == null ? java.util.Optional.empty() : java.util.Optional.of(row.identity());
    }

    private void updateActions() {
        boolean selected = list.getSelectedIndex() >= 0;
        remove.setEnabled(selected);
        clear.setEnabled(!rows.isEmpty());
        magicSort.setEnabled(rows.size() > 1);
    }

    private void refreshTheme() {
        setBackground(AppColors.color("Panel.background", new Color(0x202328)));
        setForeground(AppColors.color("Label.foreground", Color.WHITE));
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Consideration workspace must be used on EDT");
        }
    }

    private final class CandidateTransferHandler extends TransferHandler {
        @Override protected Transferable createTransferable(JComponent component) {
            return selectedIdentity().map(StringSelection::new).orElse(null);
        }

        @Override public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override public boolean canImport(TransferSupport support) {
            return model != null && support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor)
                    && support.getDropLocation() instanceof JList.DropLocation;
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                String identity = (String) support.getTransferable().getTransferData(DataFlavor.stringFlavor);
                JList.DropLocation location = (JList.DropLocation) support.getDropLocation();
                model.moveToIndex(identity, location.getIndex());
                return true;
            } catch (Exception error) {
                return false;
            }
        }
    }

    private record Row(String identity, CardInfo card, boolean stale) {
    }

    private static final class Renderer implements ListCellRenderer<Row> {
        @Override
        public Component getListCellRendererComponent(JList<? extends Row> list, Row row, int index,
                                                      boolean selected, boolean focus) {
            if (row == null || row.stale()) {
                JLabel label = new JLabel("Unavailable card — stale; keep or remove");
                label.setOpaque(true);
                label.setBorder(new EmptyBorder(6, 8, 6, 8));
                label.setFont(list.getFont());
                label.setBackground(selected ? list.getSelectionBackground() : list.getBackground());
                label.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
                return label;
            }
            ReplayCardChip chip = new ReplayCardChip(row.card(), selected);
            chip.setFont(list.getFont());
            chip.setForeground(selected ? list.getSelectionForeground() : list.getForeground());
            return chip;
        }
    }
}
