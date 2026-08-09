package app.collection.memory.windows;

import com.sun.jna.*;
import com.sun.jna.platform.win32.BaseTSD;
import com.sun.jna.platform.win32.WinNT;
import com.sun.jna.win32.StdCallLibrary;
import com.sun.jna.win32.W32APIOptions;

import java.util.ArrayList;
import java.util.List;

/** JNA bindings kept entirely inside the Windows memory module. */
final class JnaWindowsProcessApi implements WindowsProcessApi {
    private static final int PROCESS_VM_READ = 0x0010;
    private static final int PROCESS_QUERY_INFORMATION = 0x0400;
    private static final int MAX_REGIONS = 1_000_000;
    private final KernelMemory kernel;

    JnaWindowsProcessApi() {
        this(KernelMemory.INSTANCE);
    }

    JnaWindowsProcessApi(KernelMemory kernel) {
        this.kernel = kernel;
    }

    @Override
    public ProcessHandleRef open(int processId) {
        WinNT.HANDLE handle = kernel.OpenProcess(
                PROCESS_QUERY_INFORMATION | PROCESS_VM_READ, false, processId);
        if (handle == null || Pointer.nativeValue(handle.getPointer()) == 0) {
            throw new WindowsProcessAccessException(
                    "Could not open MTGA.exe (Win32 error " + Native.getLastError() + ")");
        }
        return new ProcessHandleRef(handle, processId);
    }

    @Override
    public List<MemoryRegion> inventory(ProcessHandleRef reference, Progress progress)
            throws InterruptedException {
        WinNT.HANDLE handle = (WinNT.HANDLE) reference.nativeHandle();
        List<MemoryRegion> regions = new ArrayList<>();
        long address = 0;
        for (int index = 0; index < MAX_REGIONS; index++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException("Region inventory cancelled");
            MemoryBasicInformation info = new MemoryBasicInformation();
            BaseTSD.SIZE_T returned = kernel.VirtualQueryEx(
                    handle, Pointer.createConstant(address), info,
                    new BaseTSD.SIZE_T(info.size()));
            if (returned == null || returned.longValue() == 0) break;
            info.read();
            long base = Pointer.nativeValue(info.baseAddress);
            long size = info.regionSize.longValue();
            if (size <= 0) break;
            regions.add(new MemoryRegion(base, size,
                    info.state.intValue(), info.protect.intValue(), info.type.intValue()));
            long next = base + size;
            if (next <= address || next < 0) break;
            address = next;
            if (index > 0 && index % 10_000 == 0) {
                progress.publish("Inventoried " + index + " virtual-memory regions");
            }
        }
        return List.copyOf(regions);
    }

    @Override
    public byte[] read(ProcessHandleRef reference, long address, int length) {
        if (length <= 0) throw new IllegalArgumentException("Read length must be positive");
        WinNT.HANDLE handle = (WinNT.HANDLE) reference.nativeHandle();
        byte[] buffer = new byte[length];
        Memory bytesRead = new Memory(Native.SIZE_T_SIZE);
        bytesRead.clear();
        boolean success = kernel.ReadProcessMemory(handle, Pointer.createConstant(address),
                buffer, length, bytesRead);
        long actual = Native.SIZE_T_SIZE == Long.BYTES
                ? bytesRead.getLong(0) : Integer.toUnsignedLong(bytesRead.getInt(0));
        if (!success || actual != length) {
            throw new WindowsProcessAccessException("Could not read MTGA.exe memory at 0x"
                    + Long.toHexString(address) + " (requested=" + length
                    + ", read=" + actual
                    + ", Win32 error " + Native.getLastError() + ")");
        }
        return buffer;
    }

    @Override
    public void close(ProcessHandleRef reference) {
        if (reference != null && reference.nativeHandle() instanceof WinNT.HANDLE handle) {
            kernel.CloseHandle(handle);
        }
    }

    interface KernelMemory extends StdCallLibrary {
        KernelMemory INSTANCE = Native.load("kernel32", KernelMemory.class,
                W32APIOptions.DEFAULT_OPTIONS);
        WinNT.HANDLE OpenProcess(int desiredAccess, boolean inheritHandle, int processId);
        BaseTSD.SIZE_T VirtualQueryEx(WinNT.HANDLE process, Pointer address,
                                      MemoryBasicInformation information, BaseTSD.SIZE_T length);
        boolean ReadProcessMemory(WinNT.HANDLE process, Pointer baseAddress,
                                  byte[] buffer, int size,
                                  Pointer bytesRead);
        boolean CloseHandle(WinNT.HANDLE handle);
    }

    @Structure.FieldOrder({"baseAddress", "allocationBase", "allocationProtect",
            "partitionId", "regionSize", "state", "protect", "type"})
    public static final class MemoryBasicInformation extends Structure {
        public Pointer baseAddress;
        public Pointer allocationBase;
        public WinNT.DWORD allocationProtect;
        public short partitionId;
        public BaseTSD.SIZE_T regionSize;
        public WinNT.DWORD state;
        public WinNT.DWORD protect;
        public WinNT.DWORD type;
    }

    static final class WindowsProcessAccessException extends IllegalStateException {
        WindowsProcessAccessException(String message) { super(message); }
    }
}
