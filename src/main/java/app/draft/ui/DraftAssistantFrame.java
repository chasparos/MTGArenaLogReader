package app.draft.ui;

import app.draft.export.DraftAiExporter;
import app.draft.model.DraftCardCount;
import app.draft.model.DraftPickState;
import app.draft.model.DraftUiModel;
import app.model.card.CardInfo;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.List;
import java.util.Map;

public final class DraftAssistantFrame extends JFrame {
    private final DraftUiModel model;
    private final DraftAiExporter exporter;
    private final JLabel position = new JLabel("No draft loaded");
    private final JButton previous = new JButton("Previous");
    private final JButton next = new JButton("Next");
    private final DefaultListModel<String> packRows = new DefaultListModel<>();
    private final DefaultListModel<String> poolRows = new DefaultListModel<>();
    private final DefaultListModel<String> deckRows = new DefaultListModel<>();
    private final DefaultListModel<String> sideboardRows = new DefaultListModel<>();

    public DraftAssistantFrame(DraftUiModel model, DraftAiExporter exporter) {
        super("Draft assistant");
        this.model = model;
        this.exporter = exporter;
        initialize();
        model.addListener(ignored -> SwingUtilities.invokeLater(this::refresh));
    }

    public void open() {
        refresh();
        setVisible(true);
        toFront();
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(1040, 720);
        setLocationByPlatform(true);

        JPanel header = new JPanel(new BorderLayout(8, 0));
        header.setBorder(new EmptyBorder(8, 10, 8, 10));
        position.setFont(position.getFont().deriveFont(Font.BOLD));
        header.add(position, BorderLayout.WEST);

        JPanel navigation = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        previous.addActionListener(event -> model.previous());
        next.addActionListener(event -> model.next());
        JButton copy = new JButton("Copy AI pick request");
        copy.addActionListener(event -> copyRequest());
        navigation.add(previous);
        navigation.add(next);
        navigation.add(copy);
        header.add(navigation, BorderLayout.EAST);

        JSplitPane upper = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                titledList("Current pack", packRows),
                titledList("Current collection", poolRows));
        upper.setResizeWeight(0.5);

        JSplitPane lower = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                titledList("Current main deck", deckRows),
                titledList("Current sideboard", sideboardRows));
        lower.setResizeWeight(0.5);

        JSplitPane content = new JSplitPane(JSplitPane.VERTICAL_SPLIT, upper, lower);
        content.setResizeWeight(0.62);

        add(header, BorderLayout.NORTH);
        add(content, BorderLayout.CENTER);
    }

    private JScrollPane titledList(String title, DefaultListModel<String> rows) {
        JList<String> list = new JList<>(rows);
        list.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(BorderFactory.createTitledBorder(title));
        return scroll;
    }

    private void refresh() {
        DraftPickState state = model.selected();
        int index = model.selectedIndex();
        int count = model.size();
        previous.setEnabled(index > 0);
        next.setEnabled(index >= 0 && index + 1 < count);
        if (state == null) {
            position.setText("No draft loaded");
            clearRows();
            return;
        }

        position.setText(state.positionLabel() + "   (" + (index + 1) + " of " + count + ")"
                + (state.selectedCardId() == null ? "   awaiting pick" : "   picked " + cardName(state.selectedCardId(), state.cards())));
        fillIds(packRows, state.offeredCardIds(), state.cards(), state.selectedCardId());
        fillCounts(poolRows, state.draftedPool(), state.cards());
        fillCounts(deckRows, state.mainDeck(), state.cards());
        fillCounts(sideboardRows, state.sideboard(), state.cards());
    }

    private void clearRows() {
        packRows.clear();
        poolRows.clear();
        deckRows.clear();
        sideboardRows.clear();
    }

    private void fillIds(DefaultListModel<String> target, List<Long> ids, Map<Long, CardInfo> cards, Long selected) {
        target.clear();
        for (long id : ids) {
            target.addElement((selected != null && selected == id ? "▶ " : "  ") + cardName(id, cards) + "  [" + id + "]");
        }
    }

    private void fillCounts(DefaultListModel<String> target, List<DraftCardCount> counts, Map<Long, CardInfo> cards) {
        target.clear();
        for (DraftCardCount count : counts) {
            target.addElement(String.format("%2dx  %s  [%d]", count.quantity(), cardName(count.arenaId(), cards), count.arenaId()));
        }
    }

    private String cardName(long id, Map<Long, CardInfo> cards) {
        CardInfo card = cards.get(id);
        return card == null || card.getName() == null || card.getName().isBlank()
                ? "Arena card " + id
                : card.getName();
    }

    private void copyRequest() {
        DraftPickState state = model.selected();
        if (state == null) return;
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(exporter.export(state)), null);
    }
}
