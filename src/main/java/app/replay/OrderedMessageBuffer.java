package app.replay;

import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Preserves source order while message enrichment completes asynchronously.
 *
 * <p>A completed later message remains buffered until every earlier message has
 * completed. Failed messages unblock the sequence but are not returned for
 * projection, matching the replay's existing failure behavior.</p>
 */
final class OrderedMessageBuffer {
    record CompletedMessage(LogMessageInterface message, ModelObject modelObject) {}

    private final Deque<PendingMessage> pending = new ArrayDeque<>();

    void add(LogMessageInterface message, Runnable completionCallback) {
        PendingMessage entry = new PendingMessage(message);
        synchronized (pending) {
            pending.addLast(entry);
        }
        message.getModelFuture().whenComplete((modelObject, error) -> {
            synchronized (pending) {
                entry.complete(modelObject, error);
            }
            completionCallback.run();
        });
    }

    List<CompletedMessage> drainReady() {
        List<CompletedMessage> completed = new ArrayList<>();
        synchronized (pending) {
            while (!pending.isEmpty() && pending.peekFirst().completed) {
                PendingMessage entry = pending.removeFirst();
                if (entry.error == null) {
                    completed.add(new CompletedMessage(
                            entry.message, entry.modelObject));
                }
            }
        }
        return completed;
    }

    void clear() {
        synchronized (pending) {
            pending.clear();
        }
    }

    private static final class PendingMessage {
        private final LogMessageInterface message;
        private ModelObject modelObject;
        private Throwable error;
        private boolean completed;

        private PendingMessage(LogMessageInterface message) {
            this.message = message;
        }

        private void complete(ModelObject modelObject, Throwable error) {
            this.modelObject = modelObject;
            this.error = error;
            this.completed = true;
        }
    }
}
