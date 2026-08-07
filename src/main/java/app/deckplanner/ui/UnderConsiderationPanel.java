package app.deckplanner.ui;

import app.deckplanner.consideration.UnderConsiderationModel;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppColors;
import app.ui.AppScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Ordered DP-06 candidate workspace with a custom panel surface.
 *
 * <p>The surface deliberately avoids {@link JList}: candidate rows are ordinary components so
 * future grouping/category/mana affordances can be composed without fighting a list-cell renderer.
 * Ordering remains authoritative in {@link UnderConsiderationModel}.</p>
 */
public final class UnderConsiderationPanel extends JPanel {
    private final CandidateSurface surface = new CandidateSurface();
    private final JScrollPane scroll = new JScrollPane(surface,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear");
    private final JButton magicSort = new JButton("Normal MTG sort");
    private final JButton importDeck = new JButton("Import deck");
    private Runnable importAction = () -> { };
    private Runnable magicSortAction = () -> { };
    private UnderConsiderationModel model;
    private String selectedIdentity;

    public UnderConsiderationPanel() {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Under consideration");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(true);
        scroll.getVerticalScrollBar().setUI(new AppScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(48);
        scroll.getVerticalScrollBar().getModel().addChangeListener(
                event -> syncScrollbarEnabled(scroll.getVerticalScrollBar()));
        surface.setTransferHandler(new CandidateTransferHandler());
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
        String previousSelection = selectedIdentity;
        surface.removeAll();
        surface.rows.clear();

        for (UnderConsiderationModel.Entry entry : entries) {
            CandidateRow row = new CandidateRow(entry);
            surface.rows.add(row);
            surface.add(row);
        }

        selectedIdentity = surface.rows.stream()
                .map(CandidateRow::identity)
                .filter(identity -> Objects.equals(identity, previousSelection))
                .findFirst().orElse(null);
        refreshSelection();
        updateActions();
        surface.revalidate();
        surface.repaint();
        syncScrollbarEnabled(scroll.getVerticalScrollBar());
    }

    public List<String> identities() {
        return surface.rows.stream().map(CandidateRow::identity).toList();
    }

    Optional<String> selectedIdentity() {
        return Optional.ofNullable(selectedIdentity);
    }

    JComponent candidateSurface() {
        return surface;
    }

    List<JComponent> candidateRows() {
        return List.copyOf(surface.rows);
    }

    JScrollPane candidateScrollPane() {
        return scroll;
    }

    private void select(String identity) {
        selectedIdentity = Objects.equals(selectedIdentity, identity) ? null : identity;
        refreshSelection();
        updateActions();
    }

    private void refreshSelection() {
        for (CandidateRow row : surface.rows) {
            row.setSelected(Objects.equals(row.identity(), selectedIdentity));
        }
    }

    private void updateActions() {
        remove.setEnabled(selectedIdentity != null);
        clear.setEnabled(!surface.rows.isEmpty());
        magicSort.setEnabled(surface.rows.size() > 1);
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
            throw new IllegalStateException("Consideration workspace must be used on EDT");
        }
    }

    private final class CandidateSurface extends JPanel {
        private final List<CandidateRow> rows = new ArrayList<>();

        CandidateSurface() {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setOpaque(true);
        }

        int insertionIndex(Point point) {
            int y = point == null ? Integer.MAX_VALUE : point.y;
            for (int index = 0; index < rows.size(); index++) {
                Rectangle bounds = rows.get(index).getBounds();
                if (y < bounds.y + bounds.height / 2) return index;
            }
            return rows.size();
        }
    }

    private final class CandidateRow extends JPanel {
        private final String identity;
        private final CardInfo card;
        private final boolean stale;
        private final JComponent content;
        private boolean selected;

        CandidateRow(UnderConsiderationModel.Entry entry) {
            super(new BorderLayout());
            identity = entry.identity();
            stale = entry.card().isEmpty();
            card = stale ? null : entry.card().get().group().preferredPrinting();
            setAlignmentX(Component.LEFT_ALIGNMENT);
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
            setPreferredSize(new Dimension(220, 44));
            setBorder(new EmptyBorder(2, 2, 2, 2));
            setOpaque(true);

            if (stale) {
                JLabel label = new JLabel("Unavailable card — stale; keep or remove");
                label.setBorder(new EmptyBorder(6, 8, 6, 8));
                content = label;
            } else {
                content = new ReplayCardChip(card, false);
            }
            add(content, BorderLayout.CENTER);

            MouseAdapter mouse = new MouseAdapter() {
                private Point pressed;

                @Override public void mousePressed(MouseEvent event) {
                    pressed = event.getPoint();
                    select(identity);
                }

                @Override public void mouseDragged(MouseEvent event) {
                    if (pressed == null || pressed.distance(event.getPoint()) < 4) return;
                    getTransferHandler().exportAsDrag(CandidateRow.this, event, TransferHandler.MOVE);
                    pressed = null;
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            content.addMouseListener(mouse);
            content.addMouseMotionListener(mouse);
            setTransferHandler(new CandidateTransferHandler());
            setSelected(false);
        }

        String identity() {
            return identity;
        }

        CardInfo card() {
            return card;
        }

        boolean stale() {
            return stale;
        }

        void setSelected(boolean selected) {
            this.selected = selected;
            Color base = AppColors.color("Panel.background", new Color(0x202328));
            Color selectedBackground = AppColors.color("List.selectionBackground", new Color(0x3B4554));
            setBackground(selected ? selectedBackground : base);
            if (content instanceof ReplayCardChip chip) {
                chip.setSelected(selected);
            } else {
                content.setBackground(selected ? selectedBackground : base);
                content.setForeground(AppColors.color("Label.foreground", Color.WHITE));
                content.setOpaque(true);
            }
            repaint();
        }
    }

    private final class CandidateTransferHandler extends TransferHandler {
        @Override protected Transferable createTransferable(JComponent component) {
            if (component instanceof CandidateRow row) {
                return new StringSelection(row.identity());
            }
            return selectedIdentity().map(StringSelection::new).orElse(null);
        }

        @Override public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override public boolean canImport(TransferSupport support) {
            return model != null && support.isDrop()
                    && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                String identity = (String) support.getTransferable()
                        .getTransferData(DataFlavor.stringFlavor);
                Point point = support.getDropLocation().getDropPoint();
                Point surfacePoint = SwingUtilities.convertPoint(
                        support.getComponent(), point, surface);
                model.moveToIndex(identity, surface.insertionIndex(surfacePoint));
                selectedIdentity = identity;
                return true;
            } catch (Exception error) {
                return false;
            }
        }
    }
}
