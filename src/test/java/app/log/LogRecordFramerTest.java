package app.log;


import app.log.LogRecordFramer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the structural log boundary independently of Arena game semantics.
 */
final class LogRecordFramerTest {
    @Test
    void framesMultilineJsonAndPreservesDiagnosticLines() {
        LogRecordFramer framer = new LogRecordFramer();

        assertEquals(List.of("diagnostic"), framer.accept("diagnostic"));
        assertTrue(framer.accept("{").isEmpty());
        assertTrue(framer.accept("  \"value\": \"brace } in string\",").isEmpty());
        assertTrue(framer.accept("  \"items\": [1, 2]").isEmpty());
        assertEquals(List.of("{\n  \"value\": \"brace } in string\",\n  \"items\": [1, 2]\n}"),
                framer.accept("}"));
    }

    @Test
    void extractsJsonFromPrefixedClientGreRecord() {
        LogRecordFramer framer = new LogRecordFramer();

        assertEquals(List.of("{\"payload\":{\"type\":\"ClientMessageType_RespondToChoice\"}}"),
                framer.accept("[UnityCrossThreadLogger] ClientToGREMessage "
                        + "{\"payload\":{\"type\":\"ClientMessageType_RespondToChoice\"}}"));
    }
}
