package app.deckplanner.application;

import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.filter.IndexedCatalogCard;
import app.deckplanner.filter.SemanticTag;

import javax.swing.SwingUtilities;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Restarts expensive filtering/tag-count work when interaction state changes and suppresses stale
 * completions. Computation always runs off the EDT; listener delivery always runs on the EDT.
 */
public final class DeckPlannerFilterCoordinator implements AutoCloseable {
    public enum Availability { READY, PARTIAL_CACHE, OFFLINE }

    public sealed interface ViewState permits Loading, Content, Empty, Failed {
        Availability availability();
    }
    public record Loading(Availability availability) implements ViewState { }
    public record Content(List<IndexedCatalogCard> cards, Map<SemanticTag, Long> tagCloud,
                          Availability availability) implements ViewState {
        public Content {
            cards = List.copyOf(cards);
            tagCloud = Map.copyOf(tagCloud);
        }
    }
    public record Empty(Map<SemanticTag, Long> tagCloud, Availability availability) implements ViewState {
        public Empty { tagCloud = Map.copyOf(tagCloud); }
    }
    public record Failed(String message, Availability availability) implements ViewState {
        public Failed { message = message == null || message.isBlank() ? "Could not filter cards." : message; }
    }
    public record Result(List<IndexedCatalogCard> cards, Map<SemanticTag, Long> tagCloud) {
        public Result {
            cards = List.copyOf(cards);
            tagCloud = Map.copyOf(tagCloud);
        }
    }

    @FunctionalInterface
    public interface FilterWork {
        Result compute(DeckPlannerFilterModel.State state) throws Exception;
    }

    private final DeckPlannerFilterModel model;
    private final FilterWork work;
    private final ScheduledExecutorService scheduler;
    private final Executor worker;
    private final long debounceMillis;
    private final AtomicLong generation = new AtomicLong();
    private final Consumer<DeckPlannerFilterModel.State> modelListener = ignored -> restart();
    private volatile Consumer<ViewState> listener = ignored -> { };
    private volatile ScheduledFuture<?> scheduled;
    private volatile CompletableFuture<?> running;
    private volatile Availability availability = Availability.READY;
    private volatile boolean closed;

    public DeckPlannerFilterCoordinator(DeckPlannerFilterModel model, FilterWork work,
                                        ScheduledExecutorService scheduler, Executor worker,
                                        Duration debounce) {
        this.model = Objects.requireNonNull(model);
        this.work = Objects.requireNonNull(work);
        this.scheduler = Objects.requireNonNull(scheduler);
        this.worker = Objects.requireNonNull(worker);
        debounceMillis = Math.max(0L, Objects.requireNonNull(debounce).toMillis());
        model.addListener(modelListener);
    }

    public void setListener(Consumer<ViewState> listener) {
        this.listener = Objects.requireNonNull(listener);
    }

    public void setAvailability(Availability availability) {
        this.availability = Objects.requireNonNull(availability);
        restart();
    }

    public void start() { restart(); }

    public synchronized void restart() {
        if (closed) return;
        long requestedGeneration = generation.incrementAndGet();
        if (scheduled != null) scheduled.cancel(false);
        if (running != null) running.cancel(true);
        publish(requestedGeneration, new Loading(availability));
        DeckPlannerFilterModel.State snapshot = model.state();
        scheduled = scheduler.schedule(() -> begin(snapshot, requestedGeneration),
                debounceMillis, TimeUnit.MILLISECONDS);
    }

    private synchronized void begin(DeckPlannerFilterModel.State snapshot, long requestedGeneration) {
        if (closed || requestedGeneration != generation.get()) return;
        running = CompletableFuture.supplyAsync(() -> {
            try {
                return work.compute(snapshot);
            } catch (Exception error) {
                throw new RuntimeException(error);
            }
        }, worker).whenComplete((result, error) -> finish(requestedGeneration, result, error));
    }

    private void finish(long requestedGeneration, Result result, Throwable error) {
        if (closed || requestedGeneration != generation.get()) return;
        if (error != null) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            publish(requestedGeneration, new Failed(cause.getMessage(), availability));
        } else if (result.cards().isEmpty()) {
            publish(requestedGeneration, new Empty(result.tagCloud(), availability));
        } else {
            publish(requestedGeneration, new Content(result.cards(), result.tagCloud(), availability));
        }
    }

    private void publish(long requestedGeneration, ViewState state) {
        Consumer<ViewState> target = listener;
        Runnable delivery = () -> {
            if (!closed && requestedGeneration == generation.get()) target.accept(state);
        };
        if (SwingUtilities.isEventDispatchThread()) delivery.run();
        else SwingUtilities.invokeLater(delivery);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        generation.incrementAndGet();
        model.removeListener(modelListener);
        if (scheduled != null) scheduled.cancel(false);
        if (running != null) running.cancel(true);
    }
}
