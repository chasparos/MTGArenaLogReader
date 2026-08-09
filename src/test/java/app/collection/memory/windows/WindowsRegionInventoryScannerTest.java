package app.collection.memory.windows;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class WindowsRegionInventoryScannerTest {
    @Test
    void inventoriesOnlyAndAlwaysClosesTheProcessHandle() throws Exception {
        FakeApi api = new FakeApi(List.of(
                new WindowsProcessApi.MemoryRegion(0x1000, 4096, 0x1000, 0x04, 0x20000),
                new WindowsProcessApi.MemoryRegion(0x2000, 8192, 0x1000, 0x01, 0x20000),
                new WindowsProcessApi.MemoryRegion(0x4000, 2048, 0x1000, 0x104, 0x20000)));
        WindowsRegionInventoryScanner scanner = new WindowsRegionInventoryScanner(
                api, () -> Optional.of(
                        new WindowsRegionInventoryScanner.ProcessTarget(42, "C:\\Games\\MTGA.exe")));
        List<String> progress = new ArrayList<>();

        var result = scanner.scan(progress::add);

        assertFalse(result.complete());
        assertTrue(result.copies().isEmpty());
        assertTrue(result.output().contains("readableRegions=1"));
        assertTrue(result.output().contains("readableBytes=4096"));
        assertTrue(result.output().contains("collectionExtraction=NOT_ATTEMPTED"));
        assertTrue(api.closed);
        assertTrue(progress.get(progress.size() - 1).contains("handle closed"));
    }

    @Test
    void closesHandleWhenInventoryFails() {
        FakeApi api = new FakeApi(List.of());
        api.failure = new IllegalStateException("client exited");
        WindowsRegionInventoryScanner scanner = new WindowsRegionInventoryScanner(
                api, () -> Optional.of(new WindowsRegionInventoryScanner.ProcessTarget(7, "MTGA.exe")));

        assertThrows(IllegalStateException.class, () -> scanner.scan(ignored -> { }));
        assertTrue(api.closed);
    }

    @Test
    void reportsArenaNotRunningWithoutOpeningAHandle() {
        FakeApi api = new FakeApi(List.of());
        WindowsRegionInventoryScanner scanner = new WindowsRegionInventoryScanner(
                api, Optional::empty);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> scanner.scan(ignored -> { }));
        assertEquals("MTGA.exe is not running", error.getMessage());
        assertFalse(api.opened);
    }

    private static final class FakeApi implements WindowsProcessApi {
        private final List<MemoryRegion> regions;
        private RuntimeException failure;
        private boolean opened;
        private boolean closed;

        private FakeApi(List<MemoryRegion> regions) { this.regions = regions; }

        @Override public ProcessHandleRef open(int processId) {
            opened = true;
            return new ProcessHandleRef(new Object(), processId);
        }

        @Override public List<MemoryRegion> inventory(ProcessHandleRef handle, Progress progress) {
            if (failure != null) throw failure;
            return regions;
        }

        @Override public byte[] read(ProcessHandleRef handle, long address, int length) {
            return new byte[length];
        }

        @Override public void close(ProcessHandleRef handle) { closed = true; }
    }
}
