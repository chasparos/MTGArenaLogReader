package app.collection.memory.windows;

import java.util.Objects;

/** Enforces the bounded-read policy before any native process read occurs. */
final class BoundedMemoryWindowReader {
    static final int MAX_WINDOW_BYTES = 8 * 1024 * 1024;

    private final WindowsProcessApi api;

    BoundedMemoryWindowReader(WindowsProcessApi api) {
        this.api = Objects.requireNonNull(api);
    }

    byte[] read(WindowsProcessApi.ProcessHandleRef handle,
                WindowsProcessApi.MemoryRegion region, long address, int length) {
        Objects.requireNonNull(handle);
        Objects.requireNonNull(region);
        if (!region.committedReadable()) {
            throw new IllegalArgumentException("Read window must be inside a committed readable region");
        }
        if (length <= 0 || length > MAX_WINDOW_BYTES) {
            throw new IllegalArgumentException("Read window must be between 1 and "
                    + MAX_WINDOW_BYTES + " bytes");
        }
        long regionEnd = exactEnd(region.baseAddress(), region.size(), "Region address overflow");
        long readEnd = exactEnd(address, length, "Read address overflow");
        if (address < region.baseAddress() || readEnd > regionEnd) {
            throw new IllegalArgumentException("Read window crosses its inventoried region boundary");
        }
        return api.read(handle, address, length);
    }

    private static long exactEnd(long start, long length, String message) {
        try {
            return Math.addExact(start, length);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException(message, error);
        }
    }
}
