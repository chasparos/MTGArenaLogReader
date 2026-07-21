package app.deck.ui;


import app.deck.model.CachedDeck;
import app.deck.model.DeckEntry;
import app.deck.model.DeckGameState;
import app.model.card.CardInfo;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * Represents or implements DeckTrackerFrame in the optional live deck-tracking subsystem.
 *
 * <p>The deck subsystem consumes routed Arena observations alongside cached deck metadata while remaining separate from replay reconstruction.</p>
 *
 * <p>It must not become a second source of canonical game state for the replay pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the deck-tracker Swing presentation layer and does not own deck parsing or persistence.</p>
 */
public final class DeckTrackerFrame extends JFrame {
    private final JLabel title = new JLabel("Deck");
    private final JLabel totals = new JLabel();
    private final JPanel listPanel = new JPanel();

    public DeckTrackerFrame() {
        super("Arena Deck Tracker");
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(420, 720);
        setLocationByPlatform(true);

        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(10, 12, 8, 12));
        header.add(title, BorderLayout.WEST);
        header.add(totals, BorderLayout.EAST);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        JScrollPane scroll = new JScrollPane(listPanel);
        scroll.getVerticalScrollBar().setUnitIncrement(18);

        add(header, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
    }

    public void updateState(DeckGameState state) {
        if (state == null || state.complete() || state.deck() == null) {
            setVisible(false);
            return;
        }

        CachedDeck deck = state.deck();
        title.setText(deck.name() == null || deck.name().isBlank() ? "Current deck" : deck.name());
        totals.setText("Library " + state.libraryCount()
                + "   Graveyard " + state.graveyardCount()
                + (state.exileCount() > 0 ? "   Exile " + state.exileCount() : ""));

        listPanel.removeAll();
        for (DeckEntry entry : sorted(deck.mainDeck())) {
            listPanel.add(cardRow(entry, state));
        }
        listPanel.add(Box.createVerticalGlue());
        listPanel.revalidate();
        listPanel.repaint();

        if (!isVisible()) setVisible(true);
        toFront();
    }

    private List<DeckEntry> sorted(List<DeckEntry> entries) {
        List<DeckEntry> out = new ArrayList<>(entries == null ? List.of() : entries);
        out.sort(Comparator
                .comparingDouble((DeckEntry e) -> e.card() == null || e.card().getCmc() == null ? 99 : e.card().getCmc())
                .thenComparing(DeckEntry::displayName));
        return out;
    }

    private JComponent cardRow(DeckEntry entry, DeckGameState state) {
        CardInfo card = entry.card();
        int remaining = state.remainingCopies(entry.arenaId(), entry.quantity());
        double pct = state.drawPercent(entry.arenaId(), entry.quantity());

        JPanel row = new JPanel(new BorderLayout(8, 0));
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 42));
        row.setBorder(new CompoundBorder(
                new MatteBorder(0, 0, 1, 0, new Color(0,0,0,45)),
                new EmptyBorder(5, 8, 5, 8)));
        row.setBackground(identityColor(card));
        row.setOpaque(true);

        JLabel name = new JLabel(entry.quantity() + "x " + entry.displayName());
        name.setFont(name.getFont().deriveFont(Font.BOLD));
        row.add(name, BorderLayout.CENTER);

        JLabel chance = new JLabel(remaining + " left  •  " + String.format(Locale.ROOT, "%.1f%%", pct));
        chance.setHorizontalAlignment(SwingConstants.RIGHT);
        row.add(chance, BorderLayout.EAST);

        String type = card == null ? "" : card.effectiveTypeLine();
        row.setToolTipText((type == null ? "" : type + " — ")
                + remaining + " known copies remaining in library");
        return row;
    }

    private Color identityColor(CardInfo card) {
        if (card == null || card.getColorIdentity() == null || card.getColorIdentity().isEmpty())
            return new Color(218, 216, 205);
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
