package devtools;

import app.deckplanner.ui.CardBrowserScrollPane;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerCardBrowserPreviewTest {
    @Test
    void createsInteractiveReviewSurfaceOnEdt() throws Exception {
        AtomicReference<JComponent> content = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> content.set(DeckPlannerCardBrowserPreview.createContent()));

        assertNotNull(content.get());
        assertNotNull(find(content.get(), CardBrowserScrollPane.class));
        assertEquals(80, DeckPlannerCardBrowserPreview.sampleCards(80).size());
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T nested = find(container, type);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
