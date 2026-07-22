package app.deck.ui;

import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.deck.model.DeckGameState;
import app.model.card.CardInfo;

import javax.swing.*;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Presents both live deck-tracker state and retained per-game deck snapshots.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck
 * metadata while remaining separate from replay reconstruction.</p>
 *
 * <p><strong>Architectural role:</strong> This type belongs to the deck-tracker
 * Swing presentation layer and does not own deck parsing or persistence.</p>
 */
public final class DeckTrackerFrame extends JFrame {
    private final JLabel title = new JLabel("Deck tracker");
    private final JLabel totals = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();

    public DeckTrackerFrame() {
        super("Arena Deck Tracker");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(520, 720);
        setLocationByPlatform(true);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(new EmptyBorder(10, 12, 8, 12));
        header.add(title, BorderLayout.WEST);
        header.add(totals, BorderLayout.EAST);

        add(header, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
    }

    /**
     * Updates the live tracker. Completed games continue to be available through
     * {@link #showState(DeckGameState)} but do not keep the live window open.
     */
    public void updateState(DeckGameState state) {
        if (state == null || state.complete() || state.deck() == null) {
            setVisible(false);
            return;
        }

        render(state);
        if (!isVisible()) setVisible(true);
    }

    /**
     * Shows the retained snapshot for the game selected in the replay UI.
     */
    public void showState(DeckGameState state) {
        render(state);
        setVisible(true);
        toFront();
    }

    private void render(DeckGameState state) {
        tabs.removeAll();
        if (state == null || state.deck() == null) {
            title.setText("Deck tracker");
            totals.setText("");
            tabs.addTab("Deck", messagePanel(
                    "No deck configuration has been observed for the selected game."));
            return;
        }

        CachedDeck deck = state.deck();
        String deckName = deck.name() == null || deck.name().isBlank()
                ? "Game deck"
                : deck.name();
        title.setText(deckName + " — Game " + state.gameNumber());
        totals.setText("Library " + state.libraryCount()
                + "   Graveyard " + state.graveyardCount()
                + (state.exileCount() > 0 ? "   Exile " + state.exileCount() : ""));

        tabs.addTab("Main deck (" + deck.mainDeckSize() + ")",
                deckPanel(deck.mainDeck(), state, true));
        tabs.addTab("Sideboard (" + quantity(deck.sideboard()) + ")",
                deckPanel(deck.sideboard(), state, false));
    }

    private JComponent deckPanel(List<DeckEntry> entries, DeckGameState state, boolean showTracking) {
        JPanel listPanel = new JPanel();
        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));

        for (DeckEntry entry : sorted(entries)) {
            listPanel.add(showTracking ? trackedCardRow(entry, state) : sideboardCardRow(entry));
        }
        listPanel.add(Box.createVerticalGlue());

        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        return scroll;
    }

    private JComponent trackedCardRow(DeckEntry entry, DeckGameState state) {
        CardInfo card = entry.card();
        int remaining = state.remainingCopies(entry.arenaId(), entry.quantity());
        double percent = state.drawPercent(entry.arenaId(), entry.quantity());

        JPanel row = baseRow(card);
        JLabel name = new JLabel(entry.quantity() + "x " + entry.displayName());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        row.add(name, BorderLayout.CENTER);

        JLabel chance = new JLabel(remaining + " left  •  "
                + String.format(Locale.ROOT, "%.1f%%", percent));
        chance.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(chance, BorderLayout.EAST);

        String type = card == null ? "" : card.effectiveTypeLine();
        row.setToolTipText((type == null || type.isBlank() ? "" : type + " — ")
                + remaining + " known copies remaining in library");
        return row;
    }

    private JComponent sideboardCardRow(DeckEntry entry) {
        CardInfo card = entry.card();
        JPanel row = baseRow(card);
        JLabel name = new JLabel(entry.quantity() + "x " + entry.displayName());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        row.add(name, BorderLayout.CENTER);
        if (card != null) row.setToolTipText(card.effectiveTypeLine());
        return row;
    }

    private JPanel baseRow(CardInfo card) {
        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(0, 0, 0, 45)),
                new EmptyBorder(5, 8, 5, 8)));
        row.setBackground(identityColor(card));
        row.setOpaque(true);
        return row;
    }

    private JComponent messagePanel(String text) {
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setBorder(new EmptyBorder(12, 12, 12, 12));
        return new JScrollPane(area);
    }

    private List<DeckEntry> sorted(List<DeckEntry> entries) {
        List<DeckEntry> result = new ArrayList<>(entries == null ? List.of() : entries);
        result.sort(Comparator
                .comparingDouble((DeckEntry entry) ->
                        entry.card() == null || entry.card().getCmc() == null
                                ? 99
                                : entry.card().getCmc())
                .thenComparing(DeckEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private int quantity(List<DeckEntry> entries) {
        return entries == null ? 0 : entries.stream().mapToInt(DeckEntry::quantity).sum();
    }

    private Color identityColor(CardInfo card) {
        if (card == null || card.getColorIdentity() == null || card.getColorIdentity().isEmpty()) {
            return new Color(218, 216, 205);
        }

        Set<String> colors = new HashSet<>(card.getColorIdentity());
        if (colors.size() > 1) return new Color(224, 201, 111);
        return switch (colors.iterator().next()) {
            case "W" -> new Color(245, 239, 210);
            case "U" -> new Color(168, 211, 234);
            case "B" -> new Color(170, 164, 176);
            case "R" -> new Color(235, 166, 146);
            case "G" -> new Color(168, 207, 170);
            default -> new Color(218, 216, 205);
        };
    }
}
