package app.replay;

import app.model.InformationBundle;
import app.model.log.LogMessage;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderedMessageBufferTest {
    @Test
    void waitsForEarlierMessagesBeforeDrainingLaterCompletions() {
        OrderedMessageBuffer buffer = new OrderedMessageBuffer();
        LogMessage first = message(1);
        LogMessage second = message(2);
        AtomicInteger callbacks = new AtomicInteger();
        buffer.add(first, callbacks::incrementAndGet);
        buffer.add(second, callbacks::incrementAndGet);

        second.getModelFuture().complete(new InformationBundle());
        assertTrue(buffer.drainReady().isEmpty());

        first.getModelFuture().complete(new InformationBundle());
        var completed = buffer.drainReady();
        assertEquals(2, completed.size());
        assertEquals(1, completed.get(0).message().getSequence());
        assertEquals(2, completed.get(1).message().getSequence());
        assertEquals(2, callbacks.get());
    }

    @Test
    void failedMessagesUnblockFollowingSuccessfulMessages() {
        OrderedMessageBuffer buffer = new OrderedMessageBuffer();
        LogMessage failed = message(1);
        LogMessage successful = message(2);
        buffer.add(failed, () -> { });
        buffer.add(successful, () -> { });

        successful.getModelFuture().complete(new InformationBundle());
        failed.getModelFuture().completeExceptionally(
                new IllegalStateException("enrichment failed"));

        var completed = buffer.drainReady();
        assertEquals(1, completed.size());
        assertEquals(2, completed.get(0).message().getSequence());
    }

    private LogMessage message(long sequence) {
        LogMessage message = new LogMessage();
        message.setSequence(sequence);
        return message;
    }
}
