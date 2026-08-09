package app.collection.memory.windows;

import app.collection.memory.extraction.CandidateBlockExtractor;
import app.collection.memory.extraction.ScanEvidenceConfigLoader;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WindowsAnchorDiscoveryScannerTest {
    @Test
    void findsConfiguredAnchorWithoutCompletingOrPublishing() throws Exception {
        byte[] memory = new byte[128];
        ByteBuffer.wrap(memory).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(37, 67692).putInt(41, 2);
        FakeApi api = new FakeApi(memory);
        var config = new ScanEvidenceConfigLoader.Config("fixture", Set.of(67692L, 104942L),
                List.of(new CandidateBlockExtractor.Anchor(67692, 2),
                        new CandidateBlockExtractor.Anchor(104942, 4)));
        var scanner = new WindowsAnchorDiscoveryScanner(api,
                () -> Optional.of(new WindowsRegionInventoryScanner.ProcessTarget(42, "MTGA.exe")),
                () -> config);

        var result = scanner.scan(ignored -> { });

        assertFalse(result.complete());
        assertTrue(result.copies().isEmpty());
        assertTrue(result.output().contains("anchorHits=1"));
        assertTrue(result.output().contains("hit=67692x2 @ 0x1025"));
        assertTrue(result.output().contains("globalCandidateOutcome=REJECTED"));
        assertTrue(result.output().contains("collectionPublication=DISABLED"));
        assertTrue(api.closed);
    }

    private static final class FakeApi implements WindowsProcessApi {
        private final byte[] memory;
        private boolean closed;
        FakeApi(byte[] memory) { this.memory = memory; }
        @Override public ProcessHandleRef open(int processId) {
            return new ProcessHandleRef(new Object(), processId);
        }
        @Override public List<MemoryRegion> inventory(ProcessHandleRef handle, Progress progress) {
            return List.of(new MemoryRegion(0x1000, memory.length, 0x1000, 0x04, 0x20000));
        }
        @Override public byte[] read(ProcessHandleRef handle, long address, int length) {
            int offset = Math.toIntExact(address - 0x1000);
            return Arrays.copyOfRange(memory, offset, offset + length);
        }
        @Override public void close(ProcessHandleRef handle) { closed = true; }
    }
}
