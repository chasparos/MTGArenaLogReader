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
 * Presents retained per-turn deck-tracker snapshots for the game selected in replay.
 *
 * <p>The frame is reused across game selection changes. It owns only presentation
 * selection; parsing, reconstruction, and snapshot retention remain in DeckTracker.</p>
 */
public final class DeckTrackerFrame extends JFrame {
    private final JLabel title = new JLabel("Deck tracker");
    private final JLabel totals = new JLabel();
    private final JLabel turnLabel = new JLabel("No turn");
    private final JButton previousTurn = new JButton("◀ Previous turn");
    private final JButton nextTurn = new JButton("Next turn ▶");
    private final JTabbedPane tabs = new JTabbedPane();

    private List<DeckGameState> timeline = List.of();
    private int timelineIndex = -1;

    public DeckTrackerFrame() {
        super("Arena Deck Tracker");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(560, 760);
        setLocationByPlatform(true);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JPanel header = new JPanel(new BorderLayout(12, 0));
        header.setBorder(new EmptyBorder(10, 12, 6, 12));
        header.add(title, BorderLayout.WEST);
        header.add(totals, BorderLayout.EAST);

        previousTurn.addActionListener(event -> selectIndex(timelineIndex - 1));
        nextTurn.addActionListener(event -> selectIndex(timelineIndex + 1));
        JPanel browser = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
        browser.setBorder(new EmptyBorder(0, 8, 6, 8));
        browser.add(previousTurn);
        browser.add(turnLabel);
        browser.add(nextTurn);

        JPanel north = new JPanel(new BorderLayout());
        north.add(header, BorderLayout.NORTH);
        north.add(browser, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);
        updateNavigation();
    }

    /**
     * Replaces the displayed game with its complete retained turn timeline.
     * The most recent turn is selected by default.
     */
    public void showTimeline(List<DeckGameState> states) {
        setTimeline(states);
        setVisible(true);
        toFront();
    }

    /**
     * Updates the frame after the selected replay tab changes without reopening it.
     */
    public void selectTimeline(List<DeckGameState> states) {
        if (!isVisible()) return;
        setTimeline(states);
    }

    /**
     * Incorporates live updates only when they belong to the game already shown.
     * The frame no longer jumps away from the replay tab selected by the user.
     */
    public void updateState(DeckGameState state) {
        if (state == null) return;
        if (timeline.isEmpty()) {
            if (!isVisible()) return;
            setTimeline(List.of(state));
            return;
        }
        DeckGameState shown = timeline.get(Math.max(0, timelineIndex));
        if (!sameGame(shown, state)) return;

        List<DeckGameState> updated = new ArrayList<>(timeline);
        int existing = indexOfTurn(updated, state.turnNumber());
        boolean followingLatest = timelineIndex == timeline.size() - 1;
        if (existing >= 0) updated.set(existing, state);
        else updated.add(state);
        updated.sort(Comparator.comparingInt(DeckGameState::turnNumber));
        timeline = List.copyOf(updated);
        timelineIndex = followingLatest ? timeline.size() - 1
                : Math.min(timelineIndex, timeline.size() - 1);
        renderSelected();
    }

    private void setTimeline(List<DeckGameState> states) {
        List<DeckGameState> ordered = new ArrayList<>(states == null ? List.of() : states);
        ordered.sort(Comparator.comparingInt(DeckGameState::turnNumber));
        timeline = List.copyOf(ordered);
        timelineIndex = timeline.isEmpty() ? -1 : timeline.size() - 1;
        renderSelected();
    }

    private void selectIndex(int index) {
        if (index < 0 || index >= timeline.size()) return;
        timelineIndex = index;
        renderSelected();
    }

    private void renderSelected() {
        DeckGameState state = timelineIndex < 0 || timelineIndex >= timeline.size()
                ? null : timeline.get(timelineIndex);
        render(state);
        updateNavigation();
    }

    private void updateNavigation() {
        previousTurn.setEnabled(timelineIndex > 0);
        nextTurn.setEnabled(timelineIndex >= 0 && timelineIndex < timeline.size() - 1);
        if (timelineIndex < 0 || timelineIndex >= timeline.size()) {
            turnLabel.setText("No turn");
        } else {
            int turn = timeline.get(timelineIndex).turnNumber();
            turnLabel.setText(turn <= 0 ? "Opening state" : "Turn " + turn);
        }
    }

    private void render(DeckGameState state) {
        tabs.removeAll();
        if (state == null || state.deck() == null) {
            title.setText("Deck tracker");
            totals.setText("");
            tabs.addTab("Deck", messagePanel(
                    "No authoritative deck configuration has been observed for the selected game."));
            return;
        }

        CachedDeck deck = state.deck();
        String deckName = deck.name() == null || deck.name().isBlank()
                ? "Observed game deck"
                : deck.name();
        title.setText(deckName + " — Game " + state.gameNumber());
        totals.setText("Library " + state.libraryCount()
                + "   Hand " + state.handCount()
                + "   Graveyard " + state.graveyardCount()
                + (state.exileCount() > 0 ? "   Exile " + state.exileCount() : ""));

        tabs.addTab("Main deck (" + deck.mainDeckSize() + ")",
                deckPanel(deck.mainDeck(), state, true));
        tabs.addTab("Hand (" + state.handCount() + ")", handPanel(state));
        tabs.addTab("Sideboard (" + quantity(deck.sideboard()) + ")",
                deckPanel(deck.sideboard(), state, false));
    }

    private JComponent handPanel(DeckGameState state) {
        if (state.handCards().isEmpty()) {
            return messagePanel("No visible cards are currently in the local player's hand.");
        }
        return deckPanel(handEntries(state), state, false);
    }

    private List<DeckEntry> handEntries(DeckGameState state) {
        List<DeckEntry> result = new ArrayList<>();
        for (var entry : state.handCards().entrySet()) {
            result.add(new DeckEntry(entry.getKey(), entry.getValue(),
                    cardForArenaId(state.deck(), entry.getKey())));
        }
        return result;
    }

    private CardInfo cardForArenaId(CachedDeck deck, long arenaId) {
        return java.util.stream.Stream.concat(
                        deck.mainDeck().stream(),
                        deck.sideboard().stream())
                .filter(entry -> entry.arenaId() == arenaId)
                .map(DeckEntry::card)
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
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

    /**
     * Magic-oriented ordering: rising mana value, then card color, then name.
     */
    private List<DeckEntry> sorted(List<DeckEntry> entries) {
        List<DeckEntry> result = new ArrayList<>(entries == null ? List.of() : entries);
        result.sort(Comparator
                .comparingDouble(this::manaValue)
                .thenComparingInt(this::colorOrder)
                .thenComparing(DeckEntry::displayName, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private double manaValue(DeckEntry entry) {
        CardInfo card = entry.card();
        return card == null || card.getCmc() == null ? Double.MAX_VALUE : card.getCmc();
    }

    private int colorOrder(DeckEntry entry) {
        CardInfo card = entry.card();
        if (card == null) return 7;
        List<String> colors = card.getColors();
        if (colors == null || colors.isEmpty()) colors = card.getColorIdentity();
        if (colors == null || colors.isEmpty()) return 6;
        Set<String> distinct = new HashSet<>(colors);
        if (distinct.size() > 1) return 5;
        return switch (distinct.iterator().next()) {
            case "W" -> 0;
            case "U" -> 1;
            case "B" -> 2;
            case "R" -> 3;
            case "G" -> 4;
            default -> 6;
        };
    }

    private int quantity(List<DeckEntry> entries) {
        return entries == null ? 0 : entries.stream().mapToInt(DeckEntry::quantity).sum();
    }

    private boolean sameGame(DeckGameState left, DeckGameState right) {
        return left.gameNumber() == right.gameNumber()
                && java.util.Objects.equals(left.matchId(), right.matchId());
    }

    private int indexOfTurn(List<DeckGameState> states, int turnNumber) {
        for (int i = 0; i < states.size(); i++) {
            if (states.get(i).turnNumber() == turnNumber) return i;
        }
        return -1;
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
