package app.collection.memory.extraction;

import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/** Persists evidence-only maps for controlled before/after validation; never publishes ownership. */
public final class StructuralEvidenceSnapshotStore {
    private static final Type DOCUMENT = new TypeToken<Document>() { }.getType();
    private final Path directory;

    public StructuralEvidenceSnapshotStore(Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public Result record(Map<Long, Integer> evidence) throws IOException {
        Map<Long, Integer> snapshot = Map.copyOf(evidence);
        if (snapshot.isEmpty()) return new Result(Outcome.NO_EVIDENCE, 0, List.of(), null);
        Files.createDirectories(directory);
        Path baseline = directory.resolve("structural-evidence-baseline.json");
        var gson = new GsonBuilder().setPrettyPrinting().create();
        if (!Files.exists(baseline)) {
            write(baseline, gson.toJson(new Document(Instant.now().toString(), snapshot)));
            return new Result(Outcome.BASELINE_CREATED, snapshot.size(), List.of(), baseline);
        }
        Document before = gson.fromJson(Files.readString(baseline), DOCUMENT);
        List<Change> changes = difference(before.copies(), snapshot);
        Path comparison = directory.resolve("structural-evidence-comparison.json");
        write(comparison, gson.toJson(new Comparison(before.capturedAt(), Instant.now().toString(),
                before.copies().size(), snapshot.size(), changes)));
        return new Result(Outcome.COMPARISON_CREATED, snapshot.size(), changes, comparison);
    }

    private static List<Change> difference(Map<Long, Integer> before, Map<Long, Integer> after) {
        SortedSet<Long> ids = new TreeSet<>(before.keySet());
        ids.addAll(after.keySet());
        return ids.stream().filter(id -> !Objects.equals(before.get(id), after.get(id)))
                .map(id -> new Change(id, before.getOrDefault(id, 0), after.getOrDefault(id, 0)))
                .toList();
    }

    private static void write(Path path, String value) throws IOException {
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, value);
        try { Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public enum Outcome { NO_EVIDENCE, BASELINE_CREATED, COMPARISON_CREATED }
    public record Change(long arenaId, int before, int after) { }
    public record Result(Outcome outcome, int entries, List<Change> changes, Path path) {
        public Result { changes = List.copyOf(changes); }
    }
    private record Document(String capturedAt, Map<Long, Integer> copies) { }
    private record Comparison(String baselineCapturedAt, String comparedAt, int baselineEntries,
                              int comparedEntries, List<Change> changes) { }
}
