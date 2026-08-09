package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StructuralEvidenceSnapshotStoreTest {
    @TempDir Path directory;

    @Test
    void preservesBaselineAndWritesExactComparison() throws Exception {
        StructuralEvidenceSnapshotStore store = new StructuralEvidenceSnapshotStore(directory);
        var baseline = store.record(Map.of(10L, 1, 20L, 4));
        assertEquals(StructuralEvidenceSnapshotStore.Outcome.BASELINE_CREATED, baseline.outcome());

        var comparison = store.record(Map.of(10L, 2, 20L, 4, 30L, 1));
        assertEquals(StructuralEvidenceSnapshotStore.Outcome.COMPARISON_CREATED, comparison.outcome());
        assertEquals(java.util.List.of(
                new StructuralEvidenceSnapshotStore.Change(10L, 1, 2),
                new StructuralEvidenceSnapshotStore.Change(30L, 0, 1)), comparison.changes());
        assertTrue(java.nio.file.Files.exists(directory.resolve("structural-evidence-baseline.json")));
        assertTrue(java.nio.file.Files.exists(directory.resolve("structural-evidence-comparison.json")));
    }
}
