package app.log;

import app.model.log.LogMessageInterface;
import app.model.log.RawLogEntry;
import com.google.gson.Gson;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * Provides the LogMessageReader part of the Player.log ingestion pipeline.
 *
 * <p>It sits before enrichment and game projection, converting the mixed log stream into ordered records or coordinating the threads that do so.</p>
 *
 * <p>It must not mutate canonical game state or interpret gameplay semantics.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the Player.log ingestion boundary before enrichment, routing, and canonical game-state projection.</p>
 */
public final class LogMessageReader implements Runnable, AutoCloseable {
    private final BlockingQueue<RawLogEntry> input;
    private final BlockingQueue<LogMessageInterface> enrichmentQueue;
    private final LogMessageParser parser;
    private final Consumer<Throwable> errorHandler;
    private final AtomicBoolean running = new AtomicBoolean(true);

    public LogMessageReader(BlockingQueue<RawLogEntry> input,
                            BlockingQueue<LogMessageInterface> enrichmentQueue,
                            Gson gson,
                            Consumer<Throwable> errorHandler) {
        this.input = input;
        this.enrichmentQueue = enrichmentQueue;
        this.parser = new LogMessageParser(gson);
        this.errorHandler = errorHandler;
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                RawLogEntry raw = input.take();
                enrichmentQueue.put(parser.parse(raw));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                running.set(false);
            } catch (Throwable error) {
                errorHandler.accept(error);
            }
        }
    }

    @Override
    public void close() {
        running.set(false);
    }
}
