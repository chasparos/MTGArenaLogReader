package devtools;

import app.deckplanner.ui.CardBrowserPanel;
import app.deckplanner.ui.CardGridLayout;
import app.deckplanner.ui.ViewportImageWindow;

import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Writes deterministic placeholder fixtures for human DP-04 responsive-layout review. */
public final class DeckPlannerCardBrowserFixtures {
    private static final List<CardBrowserPanel.BrowserCard> CARDS = List.of(
            card("alpha", "Alpha Example"), card("beta", "Beta Example"),
            card("gamma", "Gamma Example"), card("delta", "Delta Example"),
            card("epsilon", "Epsilon Example"), card("zeta", "Zeta Example"),
            card("eta", "Eta Example"), card("theta", "Theta Example"),
            card("iota", "Iota Example"), card("kappa", "Kappa Example"));

    private DeckPlannerCardBrowserFixtures() { }

    public static void main(String[] args) throws Exception {
        Path output = args.length == 0
                ? Path.of("target", "rendered-fixtures", "deck-planner")
                : Path.of(args[0]);
        writeStandardFixtures(output);
        System.out.println("Wrote DP-04 fixtures to " + output.toAbsolutePath());
    }

    public static void writeStandardFixtures(Path output) throws Exception {
        Files.createDirectories(output);
        write(output.resolve("card-browser-narrow.png"), render(360, 640));
        write(output.resolve("card-browser-normal.png"), render(760, 640));
        write(output.resolve("card-browser-wide.png"), render(1280, 640));
    }

    public static BufferedImage render(int width, int height) throws Exception {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("positive dimensions required");
        BufferedImage[] result = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            CardBrowserPanel panel = new CardBrowserPanel(
                    new CardGridLayout(140, 210, 16, 18, 18),
                    new ViewportImageWindow(240),
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()));
            panel.setSize(width, height);
            panel.setCards(CARDS);
            panel.updateViewport(new Rectangle(0, 0, width, height));
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            Graphics2D graphics = image.createGraphics();
            try {
                panel.paint(graphics);
            } finally {
                graphics.dispose();
            }
            result[0] = image;
        });
        return result[0];
    }

    private static CardBrowserPanel.BrowserCard card(String identity, String name) {
        return new CardBrowserPanel.BrowserCard(identity, name);
    }

    private static void write(Path path, BufferedImage image) throws IOException {
        if (!ImageIO.write(image, "png", path.toFile())) {
            throw new IOException("No PNG writer available for " + path);
        }
    }
}
