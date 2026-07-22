package app.deck.ui;

import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.deck.model.DeckGameState;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Diagnostic presentation of the active per-game deck configuration.
 */
public final class GameDeckFrame extends JFrame {
    private final JLabel title = new JLabel("Game deck");
    private final JTabbedPane tabs = new JTabbedPane();

    public GameDeckFrame() {
        super("Current Game Deck");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(520, 720);
        setLocationByPlatform(true);

        title.setBorder(new EmptyBorder(10, 12, 8, 12));
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        add(title, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    public void showState(DeckGameState state) {
        tabs.removeAll();
        if (state == null || state.deck() == null) {
            title.setText("No active game deck");
            tabs.addTab("Deck", textPanel("No active deck configuration has been observed."));
        } else {
            CachedDeck deck = state.deck();
            String name = deck.name() == null || deck.name().isBlank() ? "Current deck" : deck.name();
            title.setText(name + " — Game " + state.gameNumber());
            tabs.addTab("Main deck (" + deck.mainDeckSize() + ")", deckPanel(deck.mainDeck()));
            tabs.addTab("Sideboard (" + quantity(deck.sideboard()) + ")", deckPanel(deck.sideboard()));
        }
        setVisible(true);
        toFront();
    }

    private JComponent deckPanel(List<DeckEntry> entries) {
        JPanel list = new JPanel();
        list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
        list.setBorder(new EmptyBorder(6, 6, 6, 6));
        for (DeckEntry entry : sorted(entries)) {
            JLabel row = new JLabel(entry.quantity() + "x " + entry.displayName());
            row.setBorder(new EmptyBorder(5, 8, 5, 8));
            row.setAlignmentX(Component.LEFT_ALIGNMENT);
            list.add(row);
        }
        list.add(Box.createVerticalGlue());
        JScrollPane scroll = new JScrollPane(list);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JComponent textPanel(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        return new JScrollPane(area);
    }

    private List<DeckEntry> sorted(List<DeckEntry> entries) {
        List<DeckEntry> result = new ArrayList<>(entries == null ? List.of() : entries);
        result.sort(Comparator.comparing(DeckEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private int quantity(List<DeckEntry> entries) {
        return entries == null ? 0 : entries.stream().mapToInt(DeckEntry::quantity).sum();
    }
}
