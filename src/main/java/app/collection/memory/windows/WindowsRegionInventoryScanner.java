package app.collection.memory.windows;

import app.collection.memory.CollectionScanEngine;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Acquires MTGA.exe and inventories readable regions without reading collection bytes. */
public final class WindowsRegionInventoryScanner implements CollectionScanEngine {
    private final WindowsProcessApi api;
    private final Supplier<Optional<ProcessTarget>> targetFinder;

    public WindowsRegionInventoryScanner() {
        this(new JnaWindowsProcessApi(), WindowsRegionInventoryScanner::findArena);
    }

    WindowsRegionInventoryScanner(WindowsProcessApi api,
                                  Supplier<Optional<ProcessTarget>> targetFinder) {
        this.api = api;
        this.targetFinder = targetFinder;
    }

    @Override
    public ScanResult scan(Consumer<String> progress) throws Exception {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("windows")) {
            throw new UnsupportedOperationException("Memory inventory is supported only on Windows");
        }
        progress.accept("Locating MTGA.exe");
        ProcessTarget target = targetFinder.get().orElseThrow(() ->
                new IllegalStateException("MTGA.exe is not running"));
        WindowsProcessApi.ProcessHandleRef handle = null;
        try {
            handle = api.open(target.processId());
            progress.accept("Arena client process acquired: pid=" + target.processId());
            List<WindowsProcessApi.MemoryRegion> regions = api.inventory(handle, progress::accept);
            long readableCount = regions.stream().filter(WindowsProcessApi.MemoryRegion::committedReadable).count();
            long readableBytes = regions.stream().filter(WindowsProcessApi.MemoryRegion::committedReadable)
                    .mapToLong(WindowsProcessApi.MemoryRegion::size).sum();
            progress.accept("Readable-region inventory complete: " + readableCount + " regions");
            return new ScanResult(false, Map.of(), report(target, regions, readableCount, readableBytes));
        } finally {
            if (handle != null) {
                api.close(handle);
                progress.accept("Arena client process handle closed");
            }
        }
    }

    static Optional<ProcessTarget> findArena() {
        return ProcessHandle.allProcesses()
                .map(handle -> new ProcessTarget(Math.toIntExact(handle.pid()),
                        handle.info().command().orElse("")))
                .filter(target -> {
                    String command = target.command().replace('\\', '/');
                    int slash = command.lastIndexOf('/');
                    String name = slash < 0 ? command : command.substring(slash + 1);
                    return name.equalsIgnoreCase("MTGA.exe") || name.equalsIgnoreCase("MTGA");
                })
                .min(Comparator.comparingInt(ProcessTarget::processId));
    }

    private static String report(ProcessTarget target, List<WindowsProcessApi.MemoryRegion> regions,
                                 long readableCount, long readableBytes) {
        long committed = regions.stream().filter(region -> region.state() == 0x1000).count();
        return "WINDOWS MEMORY REGION INVENTORY\n"
                + "processId=" + target.processId() + "\n"
                + "command=" + target.command() + "\n"
                + "regions=" + regions.size() + "\n"
                + "committedRegions=" + committed + "\n"
                + "readableRegions=" + readableCount + "\n"
                + "readableBytes=" + readableBytes + "\n"
                + "collectionExtraction=NOT_ATTEMPTED\n";
    }

    record ProcessTarget(int processId, String command) { }
}
