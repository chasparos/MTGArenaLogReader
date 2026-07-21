package app.model.log;

import lombok.Data;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Data
/**
 * Defines LogMessage within the app.model.log package.
 *
 * <p>It participates in the application's established processing architecture and keeps its responsibility within this package boundary.</p>
 *
 * <p>Callers should use it through its public API rather than duplicating its behavior in adjacent layers.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the normalized log-message boundary between framing/decoding and downstream processing.</p>
 */
public class LogMessage implements LogMessageInterface {
    private long sequence;
    private Instant timestamp = Instant.now();
    private String category;
    private String displayText;
    private String rawText;
    private Set<Long> referencedCardIds = new LinkedHashSet<>();
    private CompletableFuture<ModelObject> modelFuture = new CompletableFuture<>();
}
