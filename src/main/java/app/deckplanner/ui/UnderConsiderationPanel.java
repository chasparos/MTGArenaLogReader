package app.deckplanner.ui;

import app.deckplanner.consideration.UnderConsiderationModel;
import app.deckplanner.collection.CollectionQuantity;
import app.model.card.CardInfo;
import app.ui.AppColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Ordered DP-06 candidate workspace with recoverable stale entries. */
public final class UnderConsiderationPanel extends JPanel {
    private final DefaultListModel<Row> rows = new DefaultListModel<>();
    private final JList<Row> list = new JList<>(rows);
    private final JButton remove = new JButton("Remove");
    private final JButton clear = new JButton("Clear");
    private final JButton up = new JButton("Up");
    private final JButton down = new JButton("Down");
    private final JButton importDeck = new JButton("Import deck");
    private Runnable importAction = () -> { };
    private UnderConsiderationModel model;
    private ToIntFunction<CardInfo> quantitySource = ignored -> CollectionQuantity.UNKNOWN;

    public UnderConsiderationPanel() {
        super(new BorderLayout(6, 6));
        setBorder(new EmptyBorder(8, 8, 8, 8));
        JLabel title = new JLabel("Under consideration");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        add(title, BorderLayout.NORTH);

        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        JScrollPane scroll = new JScrollPane(list);
        add(scroll, BorderLayout.CENTER);

        JPanel actions = new JPanel(new BorderLayout(4, 4));
        actions.add(importDeck, BorderLayout.NORTH);
        JPanel candidateActions = new JPanel(new GridLayout(2, 2, 4, 4));
        candidateActions.add(up);
        candidateActions.add(down);
        candidateActions.add(remove);
        candidateActions.add(clear);
        actions.add(candidateActions, BorderLayout.CENTER);
        add(actions, BorderLayout.SOUTH);

        remove.addActionListener(event -> selectedIdentity().ifPresent(model::remove));
        clear.addActionListener(event -> { if (model != null) model.clear(); });
        up.addActionListener(event -> selectedIdentity().ifPresent(identity -> model.move(identity, -1)));
        down.addActionListener(event -> selectedIdentity().ifPresent(identity -> model.move(identity, 1)));
        importDeck.addActionListener(event -> importAction.run());
        list.addListSelectionListener(event -> updateActions());
        updateActions();
        refreshTheme();
    }

    public void bind(UnderConsiderationModel model, ToIntFunction<CardInfo> quantitySource) {
        this.model = Objects.requireNonNull(model);
        this.quantitySource = quantitySource == null ? ignored -> CollectionQuantity.UNKNOWN : quantitySource;
    }

    public void setImportAction(Runnable importAction) {
        this.importAction = importAction == null ? () -> { } : importAction;
    }

    public void setEntries(List<UnderConsiderationModel.Entry> entries) {
        assertEdt();
        String selected = selectedIdentity().orElse(null);
        rows.clear();
        for (UnderConsiderationModel.Entry entry : entries) {
            if (entry.card().isPresent()) {
                CardInfo card = entry.card().get().group().preferredPrinting();
                rows.addElement(new Row(entry.identity(), card.getName(), quantitySource.applyAsInt(card), false));
            } else {
                rows.addElement(new Row(entry.identity(), "Unavailable card", CollectionQuantity.UNKNOWN, true));
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
        up.setEnabled(selected && list.getSelectedIndex() > 0);
        down.setEnabled(selected && list.getSelectedIndex() + 1 < rows.size());
        clear.setEnabled(!rows.isEmpty());
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

    private record Row(String identity, String name, int quantity, boolean stale) {
        @Override public String toString() {
            if (stale) return name + " — stale; keep or remove";
            String owned = quantity < 0 ? "owned: unknown" : "owned: " + quantity;
            return name + " — " + owned;
        }
    }

    private static final class Renderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                boolean selected, boolean focus) {
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, value, index, selected, focus);
            label.setBorder(new EmptyBorder(6, 6, 6, 6));
            return label;
        }
    }
}
