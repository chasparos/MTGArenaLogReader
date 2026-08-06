package devtools;

import app.deckplanner.ui.DeckPlannerFilterPanel;
import app.deckplanner.ui.DeckPlannerWorkspace;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerWorkspacePreviewTest {
    @Test
    void createsComposedReviewSurfaceOnEdt() throws Exception {
        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> session = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> session.set(DeckPlannerWorkspacePreview.createSession()));
        try {
            assertNotNull(find(session.get().content(), DeckPlannerWorkspace.class));
            assertNotNull(find(session.get().content(), DeckPlannerFilterPanel.class));
            assertEquals(72, DeckPlannerWorkspacePreview.sampleSnapshot(72).cardGroups().size());
        } finally {
            SwingUtilities.invokeAndWait(session.get()::close);
        }
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
