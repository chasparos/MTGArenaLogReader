package app.collection.memory.windows;

import java.util.List;

/** Internal seam around Windows process handles and virtual-memory queries. */
interface WindowsProcessApi {
    ProcessHandleRef open(int processId);
    List<MemoryRegion> inventory(ProcessHandleRef handle, Progress progress) throws InterruptedException;
    byte[] read(ProcessHandleRef handle, long address, int length);
    void close(ProcessHandleRef handle);

    record ProcessHandleRef(Object nativeHandle, int processId) { }
    record MemoryRegion(long baseAddress, long size, int state, int protection, int type) {
        boolean committedReadable() {
            int access = protection & 0xff;
            boolean readable = access == 0x02 || access == 0x04 || access == 0x08
                    || access == 0x20 || access == 0x40 || access == 0x80;
            return state == 0x1000 && readable
                    && (protection & 0x100) == 0 && access != 0x01;
        }
    }
    @FunctionalInterface interface Progress { void publish(String message); }
}
