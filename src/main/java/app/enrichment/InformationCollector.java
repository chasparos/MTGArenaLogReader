package app.enrichment;

import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Provides InformationCollector for card-data enrichment and ordered delivery of decoded log messages.
 *
 * <p>It sits between log decoding and game routing, adding optional external card information without replacing Arena-observed truth.</p>
 *
 * <p>Network and cache failures must not prevent the underlying Arena message from continuing through the pipeline.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the optional enrichment boundary; external metadata may supplement but never replace Arena-observed truth.</p>
 */
public final class InformationCollector implements Runnable, AutoCloseable {
    private final BlockingQueue<LogMessageInterface> input;
    private final BlockingQueue<LogMessageInterface> uiQueue;
    private final CardEnrichmentService cardEnrichment;
    private final ExecutorService restExecutor;
    private final Consumer<Throwable> errorHandler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public InformationCollector(BlockingQueue<LogMessageInterface> input,
                                BlockingQueue<LogMessageInterface> uiQueue,
                                CardEnrichmentService cardEnrichment,
                                ExecutorService restExecutor,
                                Consumer<Throwable> errorHandler) {
        this.input = input;
        this.uiQueue = uiQueue;
        this.cardEnrichment = cardEnrichment;
        this.restExecutor = restExecutor;
        this.errorHandler = errorHandler;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                LogMessageInterface message = input.take();
                uiQueue.put(message); // base message is immediately available
                attachInformationAsync(message);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Throwable error) {
                errorHandler.accept(error);
            }
        }
    }

    private void attachInformationAsync(LogMessageInterface message) {
        if (message.getReferencedCardIds().isEmpty()) {
            message.getModelFuture().complete(new InformationBundle());
            return;
        }

        restExecutor.submit(() -> {
            InformationBundle bundle = new InformationBundle();
            try {
                for (long arenaId : message.getReferencedCardIds()) {
                    var card = cardEnrichment.resolveArenaCard(arenaId);
                    card.ifPresent(value -> {
                        bundle.getCards().put(arenaId, value);
                        cardEnrichment.enrichBundle(value, bundle);
                    });
                }
                message.getModelFuture().complete(bundle);
            } catch (Throwable error) {
                message.getModelFuture().completeExceptionally(error);
                errorHandler.accept(error);
            }
        });
    }

    @Override
    public void close() {
        running.set(false);
    }
}
