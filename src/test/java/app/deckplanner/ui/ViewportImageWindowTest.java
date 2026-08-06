package app.deckplanner.ui;

import org.junit.jupiter.api.Test;

import java.awt.Rectangle;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ViewportImageWindowTest {
    private final List<Rectangle> bounds = List.of(
            new Rectangle(0, 0, 100, 140),
            new Rectangle(0, 160, 100, 140),
            new Rectangle(0, 320, 100, 140),
            new Rectangle(0, 480, 100, 140));

    @Test void requestsVisibleCardsPlusDirectionalMargin() {
        ViewportImageWindow selector = new ViewportImageWindow(180);
        var down = selector.select(bounds, new Rectangle(0, 150, 120, 200), 1);
        assertEquals(java.util.Set.of(1, 2), down.visibleIndices());
        assertEquals(List.of(1, 2, 3), List.copyOf(down.requestedIndices()));

        var up = selector.select(bounds, new Rectangle(0, 310, 120, 200), -1);
        assertEquals(java.util.Set.of(2, 3), up.visibleIndices());
        assertEquals(List.of(2, 3, 1), List.copyOf(up.requestedIndices()));
    }

    @Test void zeroDirectionUsesSymmetricSmallMargin() {
        var window = new ViewportImageWindow(160)
                .select(bounds, new Rectangle(0, 200, 120, 100), 0);
        assertEquals(java.util.Set.of(1), window.visibleIndices());
        assertEquals(List.of(1, 2, 0), List.copyOf(window.requestedIndices()));
    }
}
