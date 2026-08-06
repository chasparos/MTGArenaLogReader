package devtools;

import app.deckplanner.ui.CardBrowserPanel;
import app.deckplanner.ui.CardBrowserScrollPane;
import app.deckplanner.ui.CardGridLayout;
import app.deckplanner.ui.ViewportImageWindow;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Standalone human-review harness for the DP-04 responsive card browser. */
public final class DeckPlannerCardBrowserPreview {
    private static final ScheduledExecutorService IMAGES = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "deck-planner-preview-images");
        thread.setDaemon(true);
        return thread;
    });

    private DeckPlannerCardBrowserPreview() { }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Deck Planner Card Browser Review");
            frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
            frame.setContentPane(createContent());
            frame.setSize(900, 700);
            frame.setLocationByPlatform(true);
            frame.setVisible(true);
        });
    }

    public static JComponent createContent() {
        assertEdt();
        CardBrowserPanel browser = new CardBrowserPanel(
                new CardGridLayout(140, 210, 16, 18, 18),
                new ViewportImageWindow(240),
                DeckPlannerCardBrowserPreview::requestPreviewImage);
        CardBrowserScrollPane scrollPane = new CardBrowserScrollPane(browser);
        scrollPane.setCards(sampleCards(80));

        JLabel instructions = new JLabel(
                "Resize narrow/wide; scroll quickly; click cards; use arrows + Space; verify stable placeholders and no jumps.");
        instructions.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));

        JPanel content = new JPanel(new BorderLayout());
        content.add(instructions, BorderLayout.NORTH);
        content.add(scrollPane, BorderLayout.CENTER);
        return content;
    }

    static List<CardBrowserPanel.BrowserCard> sampleCards(int count) {
        List<CardBrowserPanel.BrowserCard> cards = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            cards.add(new CardBrowserPanel.BrowserCard("preview-" + index, "Preview Card " + (index + 1)));
        }
        return List.copyOf(cards);
    }

    private static CompletableFuture<Optional<BufferedImage>> requestPreviewImage(CardBrowserPanel.BrowserCard card) {
        CompletableFuture<Optional<BufferedImage>> result = new CompletableFuture<>();
        int ordinal = Integer.parseInt(card.identity().substring(card.identity().lastIndexOf('-') + 1));
        IMAGES.schedule(() -> result.complete(Optional.of(makeImage(card.name(), ordinal))),
                80L + (ordinal % 7) * 45L, TimeUnit.MILLISECONDS);
        return result;
    }

    private static BufferedImage makeImage(String name, int ordinal) {
        BufferedImage image = new BufferedImage(280, 420, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.getHSBColor((ordinal % 24) / 24f, 0.45f, 0.72f));
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
            graphics.setColor(new Color(20, 20, 24, 190));
            graphics.fillRoundRect(18, 300, 244, 92, 18, 18);
            graphics.setColor(Color.WHITE);
            graphics.setFont(graphics.getFont().deriveFont(Font.BOLD, 18f));
            graphics.drawString(name, 32, 346);
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Preview content must be created on EDT");
        }
    }
}
