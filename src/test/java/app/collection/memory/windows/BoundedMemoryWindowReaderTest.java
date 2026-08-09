package app.collection.memory.windows;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoundedMemoryWindowReaderTest {
    private static final WindowsProcessApi.ProcessHandleRef HANDLE =
            new WindowsProcessApi.ProcessHandleRef(new Object(), 42);
    private static final WindowsProcessApi.MemoryRegion READABLE =
            new WindowsProcessApi.MemoryRegion(0x1000, 0x4000, 0x1000, 0x04, 0x20000);

    @Test
    void delegatesAContainedBoundedRead() {
        FakeApi api = new FakeApi();
        byte[] bytes = new BoundedMemoryWindowReader(api).read(HANDLE, READABLE, 0x1800, 512);

        assertEquals(512, bytes.length);
        assertEquals(0x1800, api.address);
        assertEquals(512, api.length);
    }

    @Test
    void rejectsUnreadableCrossRegionAndOversizedWindowsBeforeNativeRead() {
        FakeApi api = new FakeApi();
        BoundedMemoryWindowReader reader = new BoundedMemoryWindowReader(api);
        WindowsProcessApi.MemoryRegion unreadable =
                new WindowsProcessApi.MemoryRegion(0x1000, 0x4000, 0x1000, 0x01, 0x20000);

        assertThrows(IllegalArgumentException.class,
                () -> reader.read(HANDLE, unreadable, 0x1000, 16));
        assertThrows(IllegalArgumentException.class,
                () -> reader.read(HANDLE, READABLE, 0x4ff0, 32));
        assertThrows(IllegalArgumentException.class,
                () -> reader.read(HANDLE, READABLE, 0x1000,
                        BoundedMemoryWindowReader.MAX_WINDOW_BYTES + 1));
        assertEquals(0, api.calls);
    }

    private static final class FakeApi implements WindowsProcessApi {
        private int calls;
        private long address;
        private int length;

        @Override public ProcessHandleRef open(int processId) { return HANDLE; }
        @Override public List<MemoryRegion> inventory(ProcessHandleRef handle, Progress progress) {
            return List.of();
        }
        @Override public byte[] read(ProcessHandleRef handle, long address, int length) {
            calls++;
            this.address = address;
            this.length = length;
            return new byte[length];
        }
        @Override public void close(ProcessHandleRef handle) { }
    }
}
