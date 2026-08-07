package devtools;

import app.deckplanner.consideration.DeckListImporter;
import app.deckplanner.ui.DeckPlannerFilterPanel;
import app.deckplanner.ui.DeckPlannerWorkspace;
import app.deckplanner.ui.UnderConsiderationPanel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerWorkspacePreviewTest {
    @TempDir Path tempDir;

    @Test
    void createsDp06HumanReviewSurfaceOnEdt() throws Exception {
        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> session = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                session.set(DeckPlannerWorkspacePreview.createSession(tempDir.resolve("review"))));
        try {
            assertNotNull(find(session.get().content(), DeckPlannerWorkspace.class));
            assertNotNull(find(session.get().content(), DeckPlannerFilterPanel.class));
            UnderConsiderationPanel consideration =
                    find(session.get().content(), UnderConsiderationPanel.class);
            assertNotNull(consideration);
            assertTrue(consideration.identities().contains("preview-stale-card"));
            assertEquals(72, DeckPlannerWorkspacePreview.sampleSnapshot(72).cardGroups().size());

            AtomicReference<DeckListImporter.Result> imported = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> imported.set(
                    session.get().workspace().importDeckText(
                            DeckPlannerWorkspacePreview.sampleArenaDeck())));
            assertEquals(4, imported.get().resolvedCards());
            assertEquals(java.util.List.of("Card That Does Not Exist"),
                    imported.get().unresolvedNames());
        } finally {
            SwingUtilities.invokeAndWait(session.get()::close);
        }
    }

    @Test
    void acceptanceHarnessPersistsCandidateStateAcrossRelaunch() throws Exception {
        Path database = tempDir.resolve("restart-review");
        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                first.set(DeckPlannerWorkspacePreview.createSession(database)));
        try {
            SwingUtilities.invokeAndWait(() ->
                    first.get().workspace().importDeckText("Deck\n1 Planner Card 15\n"));
        } finally {
            SwingUtilities.invokeAndWait(first.get()::close);
        }

        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> second = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                second.set(DeckPlannerWorkspacePreview.createSession(database)));
        try {
            UnderConsiderationPanel consideration =
                    find(second.get().content(), UnderConsiderationPanel.class);
            assertNotNull(consideration);
            assertTrue(consideration.identities().contains("oracle:preview-oracle-14"));
        } finally {
            SwingUtilities.invokeAndWait(second.get()::close);
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
