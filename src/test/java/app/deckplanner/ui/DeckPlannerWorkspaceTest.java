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
        assertTrue(onEdt(() -> visibleProgressBar(workspaceRef.get()) != null),
                "refresh progress should live in the fixed content strip");

        await(() -> onEdt(() -> workspaceRef.get().browser().cards().size() == 1));
        assertNull(onEdt(() -> visibleProgressBar(workspaceRef.get())));
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

    private static JProgressBar visibleProgressBar(Container root) {
        for (Component component : root.getComponents()) {
            if (component instanceof JProgressBar progress && progress.isVisible()) return progress;
            if (component instanceof Container container) {
                JProgressBar nested = visibleProgressBar(container);
                if (nested != null) return nested;
            }
        }
        return null;
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
}
