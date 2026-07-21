package app.log;

import app.model.log.RawLogEntry;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Provides the LogTailReader part of the Player.log ingestion pipeline.
 *
 * <p>It sits before enrichment and game projection, converting the mixed log stream into ordered records or coordinating the threads that do so.</p>
 *
 * <p>It must not mutate canonical game state or interpret gameplay semantics.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the Player.log ingestion boundary before enrichment, routing, and canonical game-state projection.</p>
 */
public final class LogTailReader implements Runnable, AutoCloseable {
    private final Path path;
    private final BlockingQueue<RawLogEntry> output;
    private final Duration pollInterval;
    private final boolean startAtEnd;
    private final Consumer<Throwable> errorHandler;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicLong sequence = new AtomicLong();

    private long position;
    private boolean initialized;
    private final LogRecordFramer recordFramer = new LogRecordFramer();

    public LogTailReader(Path path,
                         BlockingQueue<RawLogEntry> output,
                         Duration pollInterval,
                         boolean startAtEnd,
                         Consumer<Throwable> errorHandler) {
        this.path = Objects.requireNonNull(path);
        this.output = Objects.requireNonNull(output);
        this.pollInterval = Objects.requireNonNull(pollInterval);
        this.startAtEnd = startAtEnd;
        this.errorHandler = Objects.requireNonNull(errorHandler);
    }

    @Override
    public void run() {
        while (running.get()) {
            try {
                pollOnce();
            } catch (Throwable error) {
                errorHandler.accept(error);
            }
            sleep();
        }
    }

    private void pollOnce() throws IOException, InterruptedException {
        if (!Files.exists(path)) {
            return;
        }

        try (RandomAccessFile file = new RandomAccessFile(path.toFile(), "r")) {
            long length = file.length();
            if (!initialized) {
                position = startAtEnd ? length : 0L;
                initialized = true;
            }
            if (length < position) {
                position = 0L; // truncation/replacement after Arena restart
            }

            file.seek(position);
            String encoded;
            while (running.get() && (encoded = file.readLine()) != null) {
                String line = new String(encoded.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8);
                for (String record : recordFramer.accept(line)) {
                    if (LogLineFilter.isInteresting(record)) {
                        output.put(new RawLogEntry(sequence.incrementAndGet(), Instant.now(), record));
                    }
                }
            }
            position = file.getFilePointer();
        }
    }

    private void sleep() {
        try {
            Thread.sleep(pollInterval.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    @Override
    public void close() {
        running.set(false);
    }
}
