package devtools;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.CardCatalogPage;
import app.deckplanner.catalog.CardCatalogSource;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.consideration.DeckListImporter;
import app.deckplanner.ui.DeckPlannerFilterPanel;
import app.deckplanner.ui.DeckPlannerWorkspace;
import app.deckplanner.ui.UnderConsiderationPanel;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerWorkspacePreviewTest {
    @TempDir Path tempDir;

    @Test
    void previewCatalogTestHookUsesProductionCatalogPipeline() {
        AtomicReference<String> requestedFormat = new AtomicReference<>();
        AtomicInteger enriched = new AtomicInteger();
        List<CardInfo> first = cards(0, 5);
        List<CardInfo> second = cards(5, 5);
        CardCatalogSource source = new CardCatalogSource() {
            @Override public CardCatalogPage firstPage(String normalizedFormat) {
                requestedFormat.set(normalizedFormat);
                return new CardCatalogPage(first, "page-2");
            }
            @Override public CardCatalogPage nextPage(String cursor) {
                assertEquals("page-2", cursor);
                return new CardCatalogPage(second, "page-3-never-needed");
            }
        };

        DeckPlannerStandardPreviewCatalog.LoadResult result =
                DeckPlannerStandardPreviewCatalog.load(
                        tempDir.resolve("catalog"), source,
                        ignored -> enriched.incrementAndGet(), 7);

        assertEquals("standard", requestedFormat.get());
        assertEquals(7, enriched.get());
        assertTrue(result.snapshot().isPresent());
        assertEquals(7, result.snapshot().get().cardGroups().size());
        assertEquals(DeckPlannerFilterCoordinator.Availability.READY, result.availability());
    }

    @Test
    void createsDp06HumanReviewSurfaceOnEdt() throws Exception {
        FormatCatalogRepository.Snapshot snapshot = snapshot(8);
        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> session = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                session.set(DeckPlannerWorkspacePreview.createSession(
                        tempDir.resolve("review"), snapshot,
                        DeckPlannerFilterCoordinator.Availability.READY,
                        ignored -> CompletableFuture.completedFuture(Optional.empty()))));
        try {
            assertNotNull(find(session.get().content(), DeckPlannerWorkspace.class));
            assertNotNull(find(session.get().content(), DeckPlannerFilterPanel.class));
            UnderConsiderationPanel consideration =
                    find(session.get().content(), UnderConsiderationPanel.class);
            assertNotNull(consideration);
            assertTrue(consideration.identities().contains("preview-stale-card"));
            assertTrue(consideration.identities().contains(snapshot.cardGroups().get(0).identity()));

            AtomicReference<DeckListImporter.Result> imported = new AtomicReference<>();
            SwingUtilities.invokeAndWait(() -> imported.set(
                    session.get().workspace().importDeckText(
                            DeckPlannerWorkspacePreview.sampleArenaDeck(snapshot))));
            assertEquals(4, imported.get().resolvedCards());
            assertEquals(List.of("Card That Does Not Exist"), imported.get().unresolvedNames());
        } finally {
            SwingUtilities.invokeAndWait(session.get()::close);
        }
    }

    @Test
    void acceptanceHarnessPersistsCandidateStateAcrossRelaunch() throws Exception {
        FormatCatalogRepository.Snapshot snapshot = snapshot(8);
        Path database = tempDir.resolve("restart-review");
        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> first = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                first.set(DeckPlannerWorkspacePreview.createSession(
                        database, snapshot, DeckPlannerFilterCoordinator.Availability.READY,
                        ignored -> CompletableFuture.completedFuture(Optional.empty()))));
        String importedIdentity = snapshot.cardGroups().get(3).identity();
        String importedName = snapshot.cardGroups().get(3).preferredPrinting().getName();
        try {
            SwingUtilities.invokeAndWait(() ->
                    first.get().workspace().importDeckText("Deck\n1 " + importedName + "\n"));
        } finally {
            SwingUtilities.invokeAndWait(first.get()::close);
        }

        AtomicReference<DeckPlannerWorkspacePreview.PreviewSession> second = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() ->
                second.set(DeckPlannerWorkspacePreview.createSession(
                        database, snapshot, DeckPlannerFilterCoordinator.Availability.READY,
                        ignored -> CompletableFuture.completedFuture(Optional.empty()))));
        try {
            UnderConsiderationPanel consideration =
                    find(second.get().content(), UnderConsiderationPanel.class);
            assertNotNull(consideration);
            assertTrue(consideration.identities().contains(importedIdentity));
        } finally {
            SwingUtilities.invokeAndWait(second.get()::close);
        }
    }

    @Test
    void acceptanceChecklistRequiresExplicitHumanCompletion() throws Exception {
        AtomicReference<JPanel> checklist = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> checklist.set(DeckPlannerWorkspacePreview.acceptanceChecklist()));

        List<JCheckBox> steps = findAll(checklist.get(), JCheckBox.class);
        JLabel status = findNamed(checklist.get(), JLabel.class, "dp06-acceptance-status");
        assertEquals(5, steps.size());
        assertNotNull(status);
        assertTrue(status.getText().contains("0/5 checked"));
        assertTrue(status.getText().contains("remains active"));

        SwingUtilities.invokeAndWait(() -> steps.forEach(AbstractButton::doClick));
        assertTrue(status.getText().contains("5/5 checked"));
        assertTrue(status.getText().contains("explicit ACCEPT"));
        assertTrue(status.getText().contains("does not close DP-06"));
    }

    private static FormatCatalogRepository.Snapshot snapshot(int count) {
        List<FormatCatalogRepository.CardOutcome> outcomes = cards(0, count).stream()
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null))
                .toList();
        java.time.Instant now = java.time.Instant.now();
        return new FormatCatalogRepository.Snapshot("preview-test", "standard",
                FormatCatalogRepository.SCHEMA_VERSION, now, now, outcomes);
    }

    private static List<CardInfo> cards(int start, int count) {
        List<CardInfo> result = new ArrayList<>();
        for (int index = start; index < start + count; index++) {
            CardInfo card = new CardInfo();
            card.setId("scryfall-printing-" + index);
            card.setOracleId("oracle-" + index);
            card.setArenaId(800000L + index);
            card.setName("Standard Test Card " + (index + 1));
            card.setTypeLine(index % 2 == 0 ? "Creature — Wizard" : "Instant");
            card.setCmc((double) (index % 6));
            card.setColors(index % 2 == 0 ? List.of("U") : List.of("R"));
            card.setColorIdentity(card.getColors());
            card.setGames(List.of("arena"));
            card.setLegalities(Map.of("standard", "legal"));
            result.add(card);
        }
        return List.copyOf(result);
    }

    private static <T extends Component> List<T> findAll(Container root, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) found.add(type.cast(child));
            if (child instanceof Container container) found.addAll(findAll(container, type));
        }
        return List.copyOf(found);
    }

    private static <T extends Component> T findNamed(Container root, Class<T> type, String name) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child) && name.equals(child.getName())) return type.cast(child);
            if (child instanceof Container container) {
                T nested = findNamed(container, type, name);
                if (nested != null) return nested;
            }
        }
        return null;
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
