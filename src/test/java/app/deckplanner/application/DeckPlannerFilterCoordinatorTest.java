package app.deckplanner.application;

import app.deckplanner.filter.DeckPlannerFilterModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerFilterCoordinatorTest {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @AfterEach void closeExecutors() {
        scheduler.shutdownNow();
        worker.shutdownNow();
    }

    @Test void rapidChangesPublishOnlyNewestGenerationOnEdt() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        AtomicInteger calls = new AtomicInteger();
        CountDownLatch releaseFirst = new CountDownLatch(1);
        DeckPlannerFilterCoordinator coordinator = new DeckPlannerFilterCoordinator(model, state -> {
            int call = calls.incrementAndGet();
            if (call == 1) releaseFirst.await(2, TimeUnit.SECONDS);
            return new DeckPlannerFilterCoordinator.Result(List.of(), Map.of());
        }, scheduler, worker, Duration.ZERO);
        AtomicInteger terminalStates = new AtomicInteger();
        AtomicReference<Boolean> deliveredOnEdt = new AtomicReference<>(false);
        CountDownLatch done = new CountDownLatch(1);
        coordinator.setListener(state -> {
            if (!(state instanceof DeckPlannerFilterCoordinator.Loading)) {
                terminalStates.incrementAndGet();
                deliveredOnEdt.set(SwingUtilities.isEventDispatchThread());
                done.countDown();
            }
        });
        coordinator.start();
        while (calls.get() == 0) Thread.sleep(5);
        model.setFormat("historic");
        releaseFirst.countDown();
        assertTrue(done.await(2, TimeUnit.SECONDS));
        SwingUtilities.invokeAndWait(() -> { });
        assertEquals(1, terminalStates.get());
        assertTrue(deliveredOnEdt.get());
        coordinator.close();
    }

    @Test void completionQueuedBeforeNewGenerationIsSuppressedOnEdt() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        CountDownLatch computed = new CountDownLatch(1);
        DeckPlannerFilterCoordinator coordinator = new DeckPlannerFilterCoordinator(model, state -> {
            computed.countDown();
            return new DeckPlannerFilterCoordinator.Result(List.of(), Map.of());
        }, scheduler, worker, Duration.ZERO);
        AtomicInteger terminalStates = new AtomicInteger();
        coordinator.setListener(state -> {
            if (!(state instanceof DeckPlannerFilterCoordinator.Loading)) terminalStates.incrementAndGet();
        });

        SwingUtilities.invokeAndWait(() -> {
            coordinator.start();
            try { assertTrue(computed.await(2, TimeUnit.SECONDS)); }
            catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            model.setFormat("historic");
        });
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (terminalStates.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
        SwingUtilities.invokeAndWait(() -> { });

        assertEquals(1, terminalStates.get());
        coordinator.close();
    }

    @Test void reportsFailureAndOfflineAvailability() throws Exception {
        DeckPlannerFilterModel model = new DeckPlannerFilterModel("standard");
        DeckPlannerFilterCoordinator coordinator = new DeckPlannerFilterCoordinator(model,
                state -> { throw new IllegalStateException("catalog unavailable"); },
                scheduler, worker, Duration.ZERO);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<DeckPlannerFilterCoordinator.ViewState> terminal = new AtomicReference<>();
        coordinator.setListener(state -> {
            if (state instanceof DeckPlannerFilterCoordinator.Failed) {
                terminal.set(state); done.countDown();
            }
        });
        coordinator.setAvailability(DeckPlannerFilterCoordinator.Availability.OFFLINE);
        assertTrue(done.await(2, TimeUnit.SECONDS));
        var failed = assertInstanceOf(DeckPlannerFilterCoordinator.Failed.class, terminal.get());
        assertEquals(DeckPlannerFilterCoordinator.Availability.OFFLINE, failed.availability());
        assertTrue(failed.message().contains("catalog unavailable"));
        coordinator.close();
    }
}
