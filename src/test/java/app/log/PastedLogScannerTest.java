package app.log;

import app.model.log.RawLogEntry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ArrayBlockingQueue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PastedLogScannerTest {
    @Test
    void framesPrettyPrintedJsonAndQueuesOnlyCompletedRecord() throws Exception {
        ArrayBlockingQueue<RawLogEntry> queue = new ArrayBlockingQueue<>(10);
        PastedLogScanner scanner = new PastedLogScanner(queue);

        PastedLogScanner.ScanResult result = scanner.scan("""
                {
                  "greToClientEvent": {
                    "greToClientMessages": []
                  }
                }
                """);

        assertEquals(1, result.framedRecords());
        assertEquals(1, result.queuedRecords());
        assertEquals(1, queue.size());
        assertTrue(queue.remove().getText().contains("greToClientEvent"));
    }

    @Test
    void ignoresBlankAndDiagnosticLinesButKeepsMultipleJsonRecords() throws Exception {
        ArrayBlockingQueue<RawLogEntry> queue = new ArrayBlockingQueue<>(10);
        PastedLogScanner scanner = new PastedLogScanner(queue);

        PastedLogScanner.ScanResult result = scanner.scan("""
                ordinary Unity diagnostic

                {"matchGameRoomStateChangedEvent":{}}
                {"greToClientEvent":{"greToClientMessages":[]}}
                """);

        assertEquals(3, result.framedRecords());
        assertEquals(2, result.queuedRecords());
        assertEquals(2, queue.size());
    }

    @Test
    void emptyPasteQueuesNothing() throws Exception {
        ArrayBlockingQueue<RawLogEntry> queue = new ArrayBlockingQueue<>(2);
        PastedLogScanner.ScanResult result = new PastedLogScanner(queue).scan("  \r\n");

        assertEquals(0, result.physicalLines());
        assertEquals(0, result.queuedRecords());
        assertTrue(queue.isEmpty());
    }
}
