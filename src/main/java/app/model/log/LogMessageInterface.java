package app.model.log;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Defines LogMessageInterface within the app.model.log package.
 *
 * <p>It participates in the application's established processing architecture and keeps its responsibility within this package boundary.</p>
 *
 * <p>Callers should use it through its public API rather than duplicating its behavior in adjacent layers.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the normalized log-message boundary between framing/decoding and downstream processing.</p>
 */
public interface LogMessageInterface {
    long getSequence();
    Instant getTimestamp();
    String getCategory();
    String getDisplayText();
    String getRawText();
    Set<Long> getReferencedCardIds();
    CompletableFuture<ModelObject> getModelFuture();
}
