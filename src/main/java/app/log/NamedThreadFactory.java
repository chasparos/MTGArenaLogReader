package app.log;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Provides the NamedThreadFactory part of the Player.log ingestion pipeline.
 *
 * <p>It sits before enrichment and game projection, converting the mixed log stream into ordered records or coordinating the threads that do so.</p>
 *
 * <p>It must not mutate canonical game state or interpret gameplay semantics.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the Player.log ingestion boundary before enrichment, routing, and canonical game-state projection.</p>
 */
public final class NamedThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger();

    public NamedThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable runnable) {
        Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    }
}
