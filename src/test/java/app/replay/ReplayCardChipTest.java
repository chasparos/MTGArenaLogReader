package app.replay;

import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.awt.Image;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReplayCardChipTest {
    @Test void createsScaledReplayChipDragGhostForMultipleCards() {
        CardInfo first = new CardInfo();
        first.setName("Alpha");
        CardInfo second = new CardInfo();
        second.setName("Beta");

        Image image = ReplayCardChip.createDragImage(List.of(first, second));

        assertNotNull(image);
        assertTrue(image.getWidth(null) > 0);
        assertTrue(image.getHeight(null) > 0);
        assertTrue(image.getWidth(null) < 320,
                "drag ghost should be scaled down from the normal replay chip");
    }
}
