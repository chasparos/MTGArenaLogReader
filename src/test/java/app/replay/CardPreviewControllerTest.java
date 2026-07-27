package app.replay;

import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.awt.Dimension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CardPreviewControllerTest {
    @Test
    void recognizesHorizontalSplitLayouts() {
        CardInfo split = new CardInfo();
        split.setLayout("split");
        CardInfo aftermath = new CardInfo();
        aftermath.setLayout("aftermath");
        CardInfo normal = new CardInfo();
        normal.setLayout("normal");

        assertTrue(CardPreviewController.isSplitLayout(split));
        assertTrue(CardPreviewController.isSplitLayout(aftermath));
        assertFalse(CardPreviewController.isSplitLayout(normal));
        assertFalse(CardPreviewController.isSplitLayout(null));
    }

    @Test
    void choosesPreviewDimensionsFromLayoutAndFaceCount() {
        CardInfo split = new CardInfo();
        split.setLayout("split");
        CardInfo normal = new CardInfo();
        normal.setLayout("normal");

        assertEquals(new Dimension(340, 244),
                CardPreviewController.imageSize(split, 1));
        assertEquals(new Dimension(244, 340),
                CardPreviewController.imageSize(normal, 1));
        assertEquals(new Dimension(220, 307),
                CardPreviewController.imageSize(normal, 2));
    }
}
