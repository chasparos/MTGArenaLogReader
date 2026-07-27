package app.log;

import app.model.log.RawLogEntry;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Frames and filters raw Arena text pasted by a user, then injects the resulting
 * records at the same ingestion boundary used by Player.log tailing.
 *
 * <p>The scanner deliberately does not parse game semantics. Pretty-printed JSON,
 * one-line JSON, and prefixed ClientToGRE records are handled by
 * {@link LogRecordFramer}; downstream parsing, enrichment, and projection remain
 * unchanged.</p>
 */
public final class PastedLogScanner {
    private static final long INITIAL_SEQUENCE = 50_000_000L;

    private final BlockingQueue<RawLogEntry> output;
    private final AtomicLong sequence = new AtomicLong(INITIAL_SEQUENCE);

    public PastedLogScanner(BlockingQueue<RawLogEntry> output) {
        this.output = Objects.requireNonNull(output, "output");
    }

    public ScanResult scan(String text) throws InterruptedException {
        if (text == null || text.isBlank()) {
            return new ScanResult(0, 0, 0);
        }

        LogRecordFramer framer = new LogRecordFramer();
        int physicalLines = 0;
        int framedRecords = 0;
        int queuedRecords = 0;
        Instant timestamp = Instant.now();

        // \R accepts Windows CRLF as well as Unix line endings while preserving
        // empty physical lines that can occur in copied log excerpts.
        for (String line : text.split("\\R", -1)) {
            physicalLines++;
            for (String record : framer.accept(line)) {
                framedRecords++;
                if (!LogLineFilter.isInteresting(record)) continue;
                output.put(new RawLogEntry(
                        sequence.incrementAndGet(), timestamp, record));
                queuedRecords++;
            }
        }

        return new ScanResult(physicalLines, framedRecords, queuedRecords);
    }

    public record ScanResult(int physicalLines, int framedRecords, int queuedRecords) {
    }
}
