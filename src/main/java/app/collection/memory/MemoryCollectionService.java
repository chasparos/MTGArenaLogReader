package app.collection.memory;

import app.collection.CollectionOwnership;
import app.collection.CollectionUpdate;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import app.collection.memory.windows.WindowsRegionInventoryScanner;
import app.collection.memory.windows.WindowsAnchorDiscoveryScanner;
import app.collection.memory.extraction.ScanEvidenceConfigLoader;
import java.util.function.Supplier;

/** Isolated ownership service; scanner diagnostics are supplied only to the test harness. */
public final class MemoryCollectionService implements CollectionOwnership, CollectionUpdate, AutoCloseable {
    private final MemoryCollectionRepository repository;
    private final CollectionScanEngine scanner;
    private final ExecutorService executor;
    private final Consumer<String> progress;
    private final Consumer<String> output;
    private final Consumer<CollectionScanEngine.ScanResult> diagnostic;
    private final Supplier<java.util.List<CollectionUpdate.CardOption>> cardOptions;
    private final AtomicBoolean running = new AtomicBoolean();

    MemoryCollectionService(Path databasePath, CollectionScanEngine scanner,
                                    Consumer<String> progress, Consumer<String> output) {
        this(databasePath, scanner, progress, output, ignored -> { });
    }

    MemoryCollectionService(Path databasePath, CollectionScanEngine scanner,
                            Consumer<String> progress, Consumer<String> output,
                            Consumer<CollectionScanEngine.ScanResult> diagnostic) {
        this(databasePath, scanner, progress, output, diagnostic, null);
    }

    MemoryCollectionService(Path databasePath, CollectionScanEngine scanner,
                            Consumer<String> progress, Consumer<String> output,
                            Consumer<CollectionScanEngine.ScanResult> diagnostic,
                            Supplier<java.util.List<CollectionUpdate.CardOption>> cardOptions) {
        repository = new MemoryCollectionRepository(databasePath);
        this.scanner = Objects.requireNonNull(scanner);
        this.progress = Objects.requireNonNull(progress);
        this.output = Objects.requireNonNull(output);
        this.diagnostic = Objects.requireNonNull(diagnostic);
        this.cardOptions = cardOptions;
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "memory-collection-scan");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Dev-harness factory. Real Windows composition remains deliberately absent in MSC-01. */
    public static MemoryCollectionService fakeHarness(
            Path databasePath, Consumer<String> progress, Consumer<String> output) {
        return new MemoryCollectionService(databasePath, fakeScanner(), progress, output);
    }

    /** MSC-02 harness factory: inventories memory regions but deliberately never publishes ownership. */
    public static MemoryCollectionService windowsInventoryHarness(
            Path databasePath, Consumer<String> progress, Consumer<String> output) {
        return new MemoryCollectionService(databasePath,
                new WindowsRegionInventoryScanner(), progress, output);
    }

    public static MemoryCollectionService windowsAnchorDiscoveryHarness(
            Path databasePath, Supplier<ScanEvidenceConfigLoader.Config> evidence,
            Consumer<String> progress, Consumer<String> output) {
        return windowsAnchorDiscoveryHarness(databasePath, evidence, progress, output, ignored -> { });
    }

    public static MemoryCollectionService windowsAnchorDiscoveryHarness(
            Path databasePath, Supplier<ScanEvidenceConfigLoader.Config> evidence,
            Consumer<String> progress, Consumer<String> output,
            Consumer<CollectionScanEngine.ScanResult> diagnostic) {
        return new MemoryCollectionService(databasePath,
                new WindowsAnchorDiscoveryScanner(evidence), progress, output, diagnostic);
    }

    /** Production-ready guided composition; public inputs and outputs remain scanner-neutral. */
    public static MemoryCollectionService windowsGuided(
            Path databasePath, Path knownIdsFile,
            Supplier<java.util.List<CollectionUpdate.CardOption>> cardOptions,
            Consumer<String> progress, Consumer<String> output) {
        AtomicReference<MemoryCollectionService> owner = new AtomicReference<>();
        Supplier<ScanEvidenceConfigLoader.Config> evidence = () -> {
            MemoryCollectionService service = Objects.requireNonNull(owner.get(), "Service not initialized");
            StringBuilder confirmed = new StringBuilder();
            service.repository.verifiedCardsPreferred().forEach((id, copies) ->
                    confirmed.append(id).append('=').append(copies).append('\n'));
            try {
                return new ScanEvidenceConfigLoader().load(knownIdsFile, confirmed.toString());
            } catch (java.io.IOException error) {
                throw new IllegalStateException("Could not load Arena card catalog", error);
            }
        };
        MemoryCollectionService service = new MemoryCollectionService(databasePath,
                new WindowsAnchorDiscoveryScanner(evidence), progress, output,
                ignored -> { }, Objects.requireNonNull(cardOptions));
        owner.set(service);
        return service;
    }

    @Override public Map<Long, Integer> getCopiesOwned(java.util.Collection<Long> arenaIds) {
        Objects.requireNonNull(arenaIds);
        Map<Long, Integer> result = new java.util.LinkedHashMap<>();
        for (Long arenaId : arenaIds) {
            if (arenaId == null) throw new IllegalArgumentException("Arena IDs cannot contain null");
            result.put(arenaId, repository.getCopiesOwned(arenaId));
        }
        return Map.copyOf(result);
    }

    @Override public Session begin(Observer observer) {
        Objects.requireNonNull(observer);
        observer.onEvent(new Status("Ready to synchronize your Arena collection"));
        return new UpdateSession(observer);
    }

    private void runUpdate(UpdateSession session) {
        if (!running.compareAndSet(false, true)) {
            progress.accept("Scan already running");
            session.finish(false, 0, "A collection update is already running");
            return;
        }
        session.observer.onEvent(new Status("Connecting to MTG Arena"));
        progress.accept("Scan started");
        FutureTask<Void> work = new FutureTask<Void>((Callable<Void>) () -> {
            try {
                CollectionScanEngine.ScanResult result = scanner.scan(progress);
                if (session.cancelled.get() || Thread.currentThread().isInterrupted()) return null;
                diagnostic.accept(result);
                output.accept(result.output());
                if (!result.complete()) {
                    progress.accept("Scan did not produce a complete collection; previous ownership retained");
                    session.finish(false, 0,
                            "The collection could not be verified; previous ownership was retained");
                    return null;
                }
                session.observer.onEvent(new Status("Saving collection ownership"));
                if (session.cancelled.get() || Thread.currentThread().isInterrupted()) return null;
                repository.replaceComplete(result.copies());
                progress.accept("Complete collection published: " + result.copies().size() + " entries");
                int totalCopies = result.copies().values().stream().mapToInt(Integer::intValue).sum();
                session.finish(true, new CollectionUpdate.Summary(
                        0, result.copies().size(), totalCopies, Map.of(), Map.of(), Map.of()),
                        "Arena collection synchronized");
            } catch (Exception error) {
                if (!session.cancelled.get()) {
                    progress.accept("Scan failed: " + rootMessage(error));
                    session.finish(false, 0,
                            "Collection synchronization failed: " + rootMessage(error));
                }
            }
            return null;
        }) {
            @Override protected void done() { running.set(false); }
        };
        session.task = work;
        executor.execute(work);
    }

    private final class UpdateSession implements Session {
        private final Observer observer;
        private final AtomicBoolean scanStarted = new AtomicBoolean();
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final AtomicBoolean terminal = new AtomicBoolean();
        private final AtomicBoolean cardsVerifiedThisSession = new AtomicBoolean();
        private volatile Future<?> task;
        private UpdateSession(Observer observer) { this.observer = observer; }
        @Override public void respond(Response response) {
            Objects.requireNonNull(response);
            if (response instanceof Continue) continueUpdate();
            else if (response instanceof VerifiedCards verified) {
                repository.replaceVerifiedCards(verified.copies());
                cardsVerifiedThisSession.set(true);
                observer.onEvent(new Status("Card quantities saved; ready to continue"));
            }
        }
        private void continueUpdate() {
            if (cancelled.get() || scanStarted.get()) return;
            if (cardOptions != null && !cardsVerifiedThisSession.get()
                    && repository.verifiedCardsPreferred().size() < 2) {
                java.util.List<CollectionUpdate.CardOption> options = java.util.List.copyOf(cardOptions.get());
                observer.onEvent(new CardsRequired(
                        "Choose at least two cards and confirm how many copies Arena shows",
                        2, 5, options));
                return;
            }
            if (scanStarted.compareAndSet(false, true)) runUpdate(this);
        }
        @Override public void cancel() {
            cancelled.set(true);
            scanStarted.set(true);
            Future<?> current = task;
            if (current != null) current.cancel(true);
            finish(false, 0, "Collection synchronization cancelled");
        }
        private void finish(boolean updated, int entries, String message) {
            finish(updated, CollectionUpdate.Summary.basic(entries), message);
        }
        private void finish(boolean updated, CollectionUpdate.Summary summary, String message) {
            if (terminal.compareAndSet(false, true)) {
                observer.onEvent(new Completed(updated, summary, message));
            }
        }
    }

    private static CollectionScanEngine fakeScanner() {
        return progress -> {
            progress.accept("Fake scanner initialized");
            progress.accept("Arena client process acquired (simulated)");
            progress.accept("Candidate collection block accepted (simulated)");
            Map<Long, Integer> copies = Map.of(1001L, 4, 1002L, 1, 1003L, 2);
            return new CollectionScanEngine.ScanResult(true, copies,
                    "SIMULATED COMPLETE COLLECTION\n1001 -> 4\n1002 -> 1\n1003 -> 2\n");
        };
    }

    private static String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }

    @Override public void close() {
        executor.shutdownNow();
        repository.close();
    }
}
