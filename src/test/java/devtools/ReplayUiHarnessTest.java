package devtools;

import app.model.log.LogMessageInterface;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayUiHarnessTest {
    @Test
    void framesPastedMultilineJsonAndCompletesOfflineEnrichment() throws Exception {
        List<LogMessageInterface> messages = new ArrayList<>();
        String input = """
                diagnostic line
                {
                  "greToClientEvent": {"greToClientMessages": []}
                }
                """;

        int count = ReplayUiHarness.decode(
                new BufferedReader(new StringReader(input)), messages::add);

        assertEquals(2, count);
        assertEquals(2, messages.size());
        assertTrue(messages.stream().allMatch(message -> message.getModelFuture().isDone()));
    }
}
