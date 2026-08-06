package app.deckplanner.ui;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;

import static org.junit.jupiter.api.Assertions.*;

class CardGridLayoutTest {
    private final CardGridLayout layout = new CardGridLayout(160, 240, 16, 20, 24);

    @Test void responsiveColumnsPreserveAspectRatioAndHitTesting() {
        CardGridLayout.Result narrow = layout.layout(7, 420);
        CardGridLayout.Result normal = layout.layout(7, 900);
        CardGridLayout.Result wide = layout.layout(7, 1440);

        assertTrue(narrow.columns() < normal.columns());
        assertTrue(normal.columns() <= wide.columns());
        assertEquals(CardGridLayout.CARD_ASPECT,
                (double) normal.cardWidth() / normal.cardHeight(), 0.01);
        Rectangle third = normal.bounds().get(2);
        assertEquals(2, normal.indexAt(third.x + third.width / 2, third.y + third.height / 2));
        assertEquals(-1, normal.indexAt(0, 0));
    }

    @Test void resizeKeepsLogicalItemOrderAndProducesStableRows() {
        CardGridLayout.Result before = layout.layout(10, 800);
        CardGridLayout.Result after = layout.layout(10, 510);
        assertEquals(10, before.bounds().size());
        assertEquals(10, after.bounds().size());
        for (int index = 1; index < after.bounds().size(); index++) {
            Rectangle previous = after.bounds().get(index - 1);
            Rectangle current = after.bounds().get(index);
            assertTrue(current.y > previous.y || current.x > previous.x);
        }
        assertTrue(after.preferredSize().height > before.preferredSize().height);
    }
}
