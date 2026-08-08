package app.deckplanner.ui;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.filter.*;
import app.model.card.CardInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerWorkspaceTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    @AfterEach void closeScheduler() { scheduler.shutdownNow(); }

    @Test void composesControlsCoordinatorCountsAndBrowserOnTheEdt() throws Exception {
        CatalogFilterIndex index = index(
                card("mill", "U", "Sorcery", "Target player mills two cards."),
                card("flying", "W", "Creature — Bird", "Flying"));
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(model, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY);
            workspaceRef.set(workspace);
            workspace.start();
        });

        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));
        assertTrue(onEdt(() -> {
            AbstractButton mill = findButton(workspaceRef.get().filters(), "Mill");
            return mill != null && "1 matching card".equals(mill.getToolTipText());
        }));

        SwingUtilities.invokeAndWait(() -> model.toggleColor(CardColor.BLUE));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));
        assertEquals("mill", onEdt(() -> workspaceRef.get().browser().cards().getFirst().name()));
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }

    @Test void refreshKeepsPublishedCardsVisibleWhileWorkIsDebounced() throws Exception {
        CatalogFilterIndex index = index(
                card("mill", "U", "Sorcery", "Target player mills two cards."),
                card("flying", "W", "Creature — Bird", "Flying"));
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(model, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ofMillis(150),
                    DeckPlannerFilterCoordinator.Availability.READY);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));

        SwingUtilities.invokeAndWait(() -> model.toggleColor(CardColor.BLUE));
        assertEquals(2, onEdt(() -> workspaceRef.get().browser().cards().size()),
                "published cards should remain stable while replacement results are pending");
        assertEquals(1, onEdt(() -> visibleProgressBarCount(workspaceRef.get())),
                "refresh should animate only the fixed content-strip indicator");

        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));
        assertEquals(0, onEdt(() -> visibleProgressBarCount(workspaceRef.get())));
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }

    @Test void offlineContentRemainsBrowsableAndEmptyFiltersShowExplicitState() throws Exception {
        CatalogFilterIndex index = index(card("white", "W", "Creature — Human", "Vigilance"));
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(model, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.OFFLINE);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));

        SwingUtilities.invokeAndWait(() -> model.toggleColor(CardColor.BLACK));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().isEmpty()));
        assertTrue(onEdt(() -> containsLabel(workspaceRef.get(), "No cards match these filters")));
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }


    @Test void candidateStateOutlivesBrowserFilteringAndRestoresOverlay() throws Exception {
        CatalogFilterIndex index = index(
                card("mill", "U", "Sorcery", "Target player mills two cards."),
                card("flying", "W", "Creature — Bird", "Flying"));
        DeckPlannerFilterModel filterModel = new DeckPlannerFilterModel("standard");
        app.deckplanner.candidate.CandidateModel candidates =
                new app.deckplanner.candidate.CandidateModel(List.of(), ignored -> { });
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(filterModel, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY,
                    candidates, ignored -> app.deckplanner.collection.CollectionQuantity.UNKNOWN);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));

        SwingUtilities.invokeAndWait(() ->
                workspaceRef.get().browser().addCandidateIdentities(List.of("oracle:mill")));
        assertEquals(List.of("oracle:mill"), candidates.identities());
        assertEquals(List.of("oracle:mill"), onEdt(() -> workspaceRef.get().candidates().identities()));

        SwingUtilities.invokeAndWait(() -> filterModel.toggleColor(CardColor.WHITE));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));
        assertEquals(List.of("oracle:mill"), candidates.identities(),
                "filtering must not remove hidden candidates from the workspace");
        assertTrue(onEdt(() -> workspaceRef.get().browser().candidateIdentities().isEmpty()));
        assertEquals(List.of("oracle:mill"), onEdt(() -> workspaceRef.get().candidates().identities()));

        SwingUtilities.invokeAndWait(filterModel::resetFilters);
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));
        assertEquals(Set.of("oracle:mill"),
                onEdt(() -> workspaceRef.get().browser().candidateIdentities()));
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }

    @Test void importsExistingDeckIntoCandidatesWithoutResettingActiveFilters() throws Exception {
        CatalogFilterIndex index = index(
                card("mill", "U", "Sorcery", "Target player mills two cards."),
                card("flying", "W", "Creature — Bird", "Flying"));
        DeckPlannerFilterModel filterModel = new DeckPlannerFilterModel("standard");
        app.deckplanner.candidate.CandidateModel candidates =
                new app.deckplanner.candidate.CandidateModel(List.of(), ignored -> { });
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(filterModel, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY,
                    candidates, ignored -> app.deckplanner.collection.CollectionQuantity.UNKNOWN);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));

        SwingUtilities.invokeAndWait(() -> filterModel.toggleColor(CardColor.BLUE));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));

        AtomicReference<app.deckplanner.candidate.DeckListImporter.Result> result = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> result.set(workspaceRef.get().importDeckText("""
                Deck
                4 flying
                4 mill
                """)));

        assertEquals(List.of("oracle:flying", "oracle:mill"), candidates.identities());
        assertEquals(List.of(), result.get().unresolvedNames());
        assertEquals(Set.of("oracle:mill"), onEdt(() -> workspaceRef.get().browser().candidateIdentities()),
                "only imported cards visible under the active filter should paint browser overlays");
        assertEquals(Set.of(CardColor.BLUE), filterModel.state().filters().colors(),
                "deck import must not reset browser filters");
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }



    @Test void selectingCandidateEnablesCandidateLayerWithoutChangingNormalFilters() throws Exception {
        CatalogFilterIndex index = index(
                card("mill", "U", "Sorcery", "Target player mills two cards."),
                card("flying", "W", "Creature — Bird", "Flying"),
                card("burn", "R", "Instant", "Deal three damage."));
        DeckPlannerFilterModel filterModel = new DeckPlannerFilterModel("standard");
        app.deckplanner.candidate.CandidateModel candidates =
                new app.deckplanner.candidate.CandidateModel(
                        List.of("oracle:mill", "oracle:flying"), ignored -> { });
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(filterModel, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY,
                    candidates, ignored -> app.deckplanner.collection.CollectionQuantity.UNKNOWN);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 3));

        SwingUtilities.invokeAndWait(() -> {
            JComponent row = workspaceRef.get().candidates().candidateRows().getFirst();
            row.dispatchEvent(new java.awt.event.MouseEvent(row,
                    java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 4, 4, 1, false));
        });

        await(() -> filterModel.state().candidateOnly());
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));
        assertEquals(Set.of("oracle:mill", "oracle:flying"),
                onEdt(() -> workspaceRef.get().browser().cards().stream()
                        .map(CardBrowserPanel.BrowserCard::identity)
                        .collect(java.util.stream.Collectors.toSet())));
        assertEquals(CardFilterState.empty(), filterModel.state().filters(),
                "automatic layer activation must not mutate normal structured/tag filters");

        SwingUtilities.invokeAndWait(() -> filterModel.setCandidateOnly(false));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 3));
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }

    @Test void candidateLayerControlAndMembershipChangesRefreshImmediately() throws Exception {
        CatalogFilterIndex index = index(
                card("mill", "U", "Sorcery", "Target player mills two cards."),
                card("flying", "W", "Creature — Bird", "Flying"),
                card("burn", "R", "Instant", "Deal three damage."));
        DeckPlannerFilterModel filterModel = new DeckPlannerFilterModel("standard");
        app.deckplanner.candidate.CandidateModel candidates =
                new app.deckplanner.candidate.CandidateModel(
                        List.of("oracle:mill"), ignored -> { });
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(filterModel, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY,
                    candidates, ignored -> app.deckplanner.collection.CollectionQuantity.UNKNOWN);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 3));

        SwingUtilities.invokeAndWait(() -> {
            AbstractButton layer = findButton(workspaceRef.get().filters(), "Candidates only");
            assertNotNull(layer);
            layer.doClick();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));
        assertTrue(filterModel.state().candidateOnly());

        SwingUtilities.invokeAndWait(() -> candidates.add(List.of("oracle:flying")));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 2));

        SwingUtilities.invokeAndWait(() -> candidates.remove("oracle:mill"));
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));
        assertEquals("flying", onEdt(() -> workspaceRef.get().browser().cards().getFirst().name()));

        SwingUtilities.invokeAndWait(() -> {
            AbstractButton layer = findButton(workspaceRef.get().filters(), "Candidates only");
            layer.doClick();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 3));
        assertFalse(filterModel.state().candidateOnly());
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }


    @Test void catalogUsesSharedNormalMtgOrderingBeforePresentation() throws Exception {
        CatalogFilterIndex index = index(
                card("zspell", "U", "Sorcery", "Draw a card."),
                card("acreature", "G", "Creature — Elf", "Reach"),
                card("aland", "", "Land", ""));
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicReference<DeckPlannerWorkspace> workspaceRef = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(model, index,
                    ignored -> java.util.concurrent.CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY);
            workspaceRef.set(workspace);
            workspace.start();
        });
        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 3));
        assertEquals(List.of("acreature", "zspell", "aland"),
                onEdt(() -> workspaceRef.get().browser().cards().stream()
                        .map(CardBrowserPanel.BrowserCard::name).toList()));
        SwingUtilities.invokeAndWait(workspaceRef.get()::close);
    }

    @Test void workspaceBoundaryControlsHideFiltersAndExpandCandidates() throws Exception {
        CatalogFilterIndex index = index(card("mill", "U", "Sorcery", "Mill two."));
        AtomicReference<DeckPlannerWorkspace> ref = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerWorkspace workspace = new DeckPlannerWorkspace(
                    new DeckPlannerFilterModel("standard"), index,
                    ignored -> CompletableFuture.completedFuture(Optional.empty()),
                    scheduler, Runnable::run, Duration.ZERO,
                    DeckPlannerFilterCoordinator.Availability.READY);
            ref.set(workspace);
        });

        SwingUtilities.invokeAndWait(() -> {
            ref.get().setSize(1400, 800);
            ref.get().doLayout();
            JLayeredPane layer = findComponent(ref.get(), JLayeredPane.class);
            assertNotNull(layer);
            layer.doLayout();

            AbstractButton expand = findButtonByTooltip(ref.get(), "Expand candidates");
            assertNotNull(expand);
            assertFalse(expand.isOpaque());
            assertFalse(expand.isContentAreaFilled());
            int collapsedX = expand.getX();
            expand.doClick();
            assertEquals(760, ref.get().candidates().getPreferredSize().width);
            assertTrue(expand.getX() < collapsedX,
                    "candidate boundary control should move immediately with the resized panel");

            AbstractButton hide = findButtonByTooltip(ref.get(), "Hide filters");
            assertNotNull(hide);
            assertFalse(hide.isOpaque());
            int shownX = hide.getX();
            hide.doClick();
            assertEquals("Show filters", hide.getToolTipText());
            assertTrue(hide.getX() <= shownX,
                    "filter boundary control should reposition immediately when filters hide");
        });
        SwingUtilities.invokeAndWait(ref.get()::close);
    }

    private static CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = java.util.Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null)).toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot("run", "standard", 1,
                Instant.EPOCH, Instant.EPOCH, outcomes));
    }

    private static CardInfo card(String name, String color, String type, String oracle) {
        CardInfo card = new CardInfo();
        card.setId(name); card.setOracleId(name); card.setName(name);
        card.setArenaId((long) Math.abs(name.hashCode()) + 1);
        card.setColors(List.of(color)); card.setColorIdentity(List.of(color));
        card.setTypeLine(type); card.setCmc(2.0); card.setOracleText(oracle); card.setKeywords(List.of());
        return card;
    }

    private static <T extends Component> T findComponent(Container root, Class<T> type) {
        for (Component component : root.getComponents()) {
            if (type.isInstance(component)) return type.cast(component);
            if (component instanceof Container container) {
                T nested = findComponent(container, type);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static AbstractButton findButton(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button && text.equals(button.getText())) return button;
            if (component instanceof Container container) {
                AbstractButton nested = findButton(container, text);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static int visibleProgressBarCount(Container root) {
        int count = 0;
        for (Component component : root.getComponents()) {
            if (component instanceof JProgressBar progress && progress.isVisible()) count++;
            if (component instanceof Container container) count += visibleProgressBarCount(container);
        }
        return count;
    }

    private static boolean containsLabel(Container root, String text) {
        for (Component component : root.getComponents()) {
            if (component instanceof JLabel label && text.equals(label.getText())) return true;
            if (component instanceof Container container && containsLabel(container, text)) return true;
        }
        return false;
    }

    private static <T> T onEdt(java.util.concurrent.Callable<T> call) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(call.call()); }
            catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }

    private static void await(CheckedBoolean condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (System.nanoTime() < deadline) {
            if (condition.get()) return;
            Thread.sleep(10);
        }
        fail("condition not reached");
    }

    @FunctionalInterface private interface CheckedBoolean { boolean get() throws Exception; }
    private static AbstractButton findButtonByTooltip(Container root, String tooltip) {
        for (Component component : root.getComponents()) {
            if (component instanceof AbstractButton button
                    && tooltip.equals(button.getToolTipText())) return button;
            if (component instanceof Container container) {
                AbstractButton nested = findButtonByTooltip(container, tooltip);
                if (nested != null) return nested;
            }
        }
        return null;
    }

}
