package app.deckplanner.ui;

import app.deckplanner.candidate.CandidateModel;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppColors;
import app.ui.CardCollectionSurface;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Ordered DP-06 candidate workspace backed by the project-owned {@link CardCollectionSurface}.
 *
 * <p>Candidate cards are grouped into stable planning categories and rendered as ordinary
 * components. Ordering remains authoritative in {@link CandidateModel}; category
 * layout is presentation only and therefore leaves room for future semantic planner groupings.</p>
 */
public final class CandidatePanel extends JPanel {
    private final CardCollectionSurface surface = new CardCollectionSurface();
    private final JScrollPane scroll = surface.createScrollPane();
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear");
    private final JButton magicSort = new JButton("Normal MTG sort");
    private final JButton importDeck = new JButton("Import deck");
    private Runnable importAction = () -> { };
    private Runnable magicSortAction = () -> { };
    private CandidateModel model;
    private Consumer<Optional<String>> selectionAction = ignored -> { };

    public CandidatePanel() {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Candidates");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        surface.setMoveHandler(this::moveDisplayedCandidate);
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

    public void bind(CandidateModel model, ToIntFunction<CardInfo> ignoredQuantitySource) {
        this.model = Objects.requireNonNull(model);
    }

    private void moveDisplayedCandidate(String identity, int insertionIndex) {
        if (model == null || identity == null) return;
        ArrayList<String> order = new ArrayList<>(surface.identities());
        int from = order.indexOf(identity);
        if (from < 0) return;
        int target = Math.max(0, Math.min(order.size(), insertionIndex));
        order.remove(from);
        if (from < target) target--;
        target = Math.max(0, Math.min(order.size(), target));
        order.add(target, identity);
        model.reorder(order);
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

    public void setEntries(List<CandidateModel.Entry> entries) {
        assertEdt();
        List<CandidateRow> creatures = new ArrayList<>();
        List<CandidateRow> noncreatures = new ArrayList<>();
        List<CandidateRow> nonbasicLands = new ArrayList<>();
        List<CandidateRow> unavailable = new ArrayList<>();

        for (CandidateModel.Entry entry :
                entries == null ? List.<CandidateModel.Entry>of() : entries) {
            CandidateRow row = new CandidateRow(entry);
            switch (category(row.card(), row.stale())) {
                case CREATURES -> creatures.add(row);
                case NONCREATURES -> noncreatures.add(row);
                case NONBASIC_LANDS -> nonbasicLands.add(row);
                case UNAVAILABLE -> unavailable.add(row);
            }
        }

        List<CardCollectionSurface.Group> groups = new ArrayList<>();
        groups.add(group("creatures", "Creatures", creatures));
        groups.add(group("noncreatures", "Noncreatures", noncreatures));
        groups.add(group("nonbasic-lands", "Nonbasic Lands", nonbasicLands));
        if (!unavailable.isEmpty()) {
            groups.add(group("unavailable", "Unavailable", unavailable));
        }
        surface.setGroups(groups);
        updateActions();
        syncScrollbarEnabled(scroll.getVerticalScrollBar());
    }

    private static CardCollectionSurface.Group group(
            String id, String title, List<? extends CardCollectionSurface.Row> rows) {
        return new CardCollectionSurface.Group(
                id, title + " (" + rows.size() + ")", new ArrayList<>(rows));
    }

    private static CandidateCategory category(CardInfo card, boolean stale) {
        if (stale || card == null) return CandidateCategory.UNAVAILABLE;
        String typeLine = Optional.ofNullable(card.effectiveTypeLine())
                .orElse("").toLowerCase(Locale.ROOT);
        if (typeLine.contains("creature")) return CandidateCategory.CREATURES;
        if (typeLine.contains("land") && !typeLine.contains("basic land")) {
            return CandidateCategory.NONBASIC_LANDS;
        }
        return CandidateCategory.NONCREATURES;
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
            throw new IllegalStateException("Candidate workspace must be used on EDT");
        }
    }

    private enum CandidateCategory {
        CREATURES,
        NONCREATURES,
        NONBASIC_LANDS,
        UNAVAILABLE
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
