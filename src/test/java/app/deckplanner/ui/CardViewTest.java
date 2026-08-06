package app.deckplanner.ui;

import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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
            view.configure("A very long card name for placeholder rendering", null, false, false, false);
            Graphics2D placeholderGraphics = placeholder.createGraphics();
            view.paint(placeholderGraphics);
            placeholderGraphics.dispose();
            assertNotEquals(0, placeholder.getRGB(50, 80));

            BufferedImage rendered = new BufferedImage(100, 160, BufferedImage.TYPE_INT_ARGB);
            view.configure("Card", cached, true, true, true);
            Graphics2D renderedGraphics = rendered.createGraphics();
            view.paint(renderedGraphics);
            renderedGraphics.dispose();
        });

        assertEquals(original, cached.getRGB(5, 5), "overlays must not mutate cached images");
    }
}
