package app.log;

import java.util.ArrayList;
import java.util.List;

/**
 * Reassembles pretty-printed JSON records from a stream of physical log lines.
 * Arena emits both one-line and multi-line JSON, mixed with ordinary diagnostics.
 * <p><strong>Architectural role:</strong> This type belongs to the Player.log ingestion boundary before enrichment, routing, and canonical game-state projection.</p>
 */
public final class LogRecordFramer {
    private static final int MAX_RECORD_CHARS = 16 * 1024 * 1024;

    private final StringBuilder buffer = new StringBuilder();
    private boolean collectingJson;
    private boolean inString;
    private boolean escaped;
    private int objectDepth;
    private int arrayDepth;

    public List<String> accept(String line) {
        List<String> completed = new ArrayList<>(1);
        if (line == null) {
            return completed;
        }

        if (!collectingJson) {
            String stripped = line.stripLeading();

            /*
             * Outgoing ClientToGRE records are often prefixed by Arena's
             * Unity logger instead of beginning directly with JSON.  Preserve
             * ordinary diagnostics, but peel off the JSON portion for the
             * gameplay client-message records needed by pending-action
             * correlation.
             */
            if (!startsJson(stripped) && isPrefixedClientGreRecord(line)) {
                int jsonStart = line.indexOf('{');
                if (jsonStart >= 0) stripped = line.substring(jsonStart);
            }

            if (!startsJson(stripped)) {
                completed.add(line);
                return completed;
            }
            line = stripped;
            reset();
            collectingJson = true;
        }

        if (!buffer.isEmpty()) {
            buffer.append('\n');
        }
        buffer.append(line);
        scan(line);

        if (buffer.length() > MAX_RECORD_CHARS) {
            String oversized = buffer.toString();
            reset();
            completed.add(oversized);
            return completed;
        }

        if (collectingJson && objectDepth == 0 && arrayDepth == 0 && !inString) {
            completed.add(buffer.toString());
            reset();
        }
        return completed;
    }

    private boolean startsJson(String line) {
        return line.startsWith("{");
    }

    private boolean isPrefixedClientGreRecord(String line) {
        return line.contains("ClientToGremessage")
                || line.contains("ClientToGREMessage")
                || line.contains("ClientMessageType_");
    }

    private void scan(String line) {
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);

            if (inString) {
                if (escaped) {
                    escaped = false;
                } else if (character == '\\') {
                    escaped = true;
                } else if (character == '"') {
                    inString = false;
                }
                continue;
            }

            if (character == '"') {
                inString = true;
                continue;
            }

            switch (character) {
                case '{' -> objectDepth++;
                case '}' -> objectDepth--;
                case '[' -> arrayDepth++;
                case ']' -> arrayDepth--;
                default -> {
                }
            }
        }
    }

    /** True while a multi-line JSON record is being assembled. */
    public boolean isCollectingJson() {
        return collectingJson;
    }

    public void reset() {
        buffer.setLength(0);
        collectingJson = false;
        inString = false;
        escaped = false;
        objectDepth = 0;
        arrayDepth = 0;
    }
}
