package app.deckplanner.ui;

import app.deckplanner.consideration.UnderConsiderationModel;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppColors;
import app.ui.CardCollectionSurface;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Ordered DP-06 candidate workspace backed by the project-owned {@link CardCollectionSurface}.
 *
 * <p>Candidate rows are ordinary components so future grouping/category/mana affordances can be
 * composed without fighting a list-cell renderer. Ordering remains authoritative in
 * {@link UnderConsiderationModel}.</p>
 */
public final class UnderConsiderationPanel extends JPanel {
    private final CardCollectionSurface surface = new CardCollectionSurface();
    private final JScrollPane scroll = surface.createScrollPane();
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear");
    private final JButton magicSort = new JButton("Normal MTG sort");
    private final JButton importDeck = new JButton("Import deck");
    private Runnable importAction = () -> { };
    private Runnable magicSortAction = () -> { };
    private UnderConsiderationModel model;
    private Consumer<Optional<String>> selectionAction = ignored -> { };

    public UnderConsiderationPanel() {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Under consideration");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        surface.setMoveHandler((identity, insertionIndex) -> {
            if (model != null) model.moveToIndex(identity, insertionIndex);
        });
        surface.setSelectionListener(selection -> {
            updateActions();
            selectionAction.accept(selection);
        });
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

    public void setSelectionAction(Consumer<Optional<String>> selectionAction) {
        this.selectionAction = selectionAction == null ? ignored -> { } : selectionAction;
    }

    public void setEntries(List<UnderConsiderationModel.Entry> entries) {
        assertEdt();
        List<CandidateRow> rows = (entries == null ? List.<UnderConsiderationModel.Entry>of() : entries)
                .stream().map(CandidateRow::new).toList();
        surface.setRows(rows);
        updateActions();
        syncScrollbarEnabled(scroll.getVerticalScrollBar());
    }

    public List<String> identities() {
        return surface.identities();
    }

    Optional<String> selectedIdentity() {
        return surface.selectedIdentity();
    }

    JComponent candidateSurface() {
        return surface;
    }

    List<JComponent> candidateRows() {
        return surface.rowComponents();
    }

    JScrollPane candidateScrollPane() {
        return scroll;
    }

    private void updateActions() {
        remove.setEnabled(surface.selectedIdentity().isPresent());
        clear.setEnabled(!surface.identities().isEmpty());
        magicSort.setEnabled(surface.identities().size() > 1);
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

    private final class CandidateRow extends JPanel implements CardCollectionSurface.Row {
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

            setSelected(false);
        }

        @Override public String identity() {
            return identity;
        }

        @Override public JComponent component() {
            return this;
        }

        CardInfo card() {
            return card;
        }

        boolean stale() {
            return stale;
        }

        @Override public void setSelected(boolean selected) {
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

}
