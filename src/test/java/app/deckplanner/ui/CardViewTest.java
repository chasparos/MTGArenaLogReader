package app.deckplanner.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardViewTest {
    @Test
    void rendersStablePlaceholderAndDoesNotMutateCachedImage() throws Exception {
        BufferedImage cached = new BufferedImage(20, 28, BufferedImage.TYPE_INT_ARGB);
        cached.setRGB(5, 5, 0xff123456);
        int original = cached.getRGB(5, 5);

        SwingUtilities.invokeAndWait(() -> {
            CardView view = new CardView();
            view.setSize(100, 160);

            BufferedImage placeholder = new BufferedImage(100, 160, BufferedImage.TYPE_INT_ARGB);
            view.configure("A very long card name for placeholder rendering", null, false, false, false, false);
            Graphics2D placeholderGraphics = placeholder.createGraphics();
            view.paint(placeholderGraphics);
            placeholderGraphics.dispose();
            assertNotEquals(0, placeholder.getRGB(50, 80));

            BufferedImage rendered = new BufferedImage(100, 160, BufferedImage.TYPE_INT_ARGB);
            view.configure("Card", cached, true, true, true, true);
            Graphics2D renderedGraphics = rendered.createGraphics();
            view.paint(renderedGraphics);
            renderedGraphics.dispose();
        });

        assertEquals(original, cached.getRGB(5, 5), "overlays must not mutate cached images");
    }

    @Test
    void selectedCardUsesGoldenOutlineInsteadOfLargeBadge() throws Exception {
        BufferedImage rendered = new BufferedImage(120, 168, BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            CardView view = new CardView();
            view.setSize(120, 168);
            view.configure("Card", null, false, true, false, false);
            Graphics2D graphics = rendered.createGraphics();
            view.paint(graphics);
            graphics.dispose();
        });

        Color edge = new Color(rendered.getRGB(2, 84), true);
        assertEquals(new Color(214, 168, 75).getRed(), edge.getRed());
        assertEquals(new Color(214, 168, 75).getGreen(), edge.getGreen());
    }

    @Test
    void alternateArtBadgeHasStableClickableBounds() {
        Rectangle bounds = CardView.alternateArtBadgeBounds(300);
        assertTrue(bounds.width > 0);
        assertTrue(bounds.height > 0);
        assertTrue(bounds.x < 50);
    }

}
