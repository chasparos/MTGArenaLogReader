package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ScanEvidenceConfigLoaderTest {
    @TempDir Path directory;

    @Test
    void loadsVersionedKnownIdsAndConfirmedAnchors() throws Exception {
        Path file = directory.resolve("known.json");
        Files.writeString(file, "{\"version\":\"arena-test-1\",\"arenaIds\":[1101,1102,1103]}");

        var config = new ScanEvidenceConfigLoader().load(file, "1101=4\n1102,2\n");

        assertEquals("arena-test-1", config.version());
        assertEquals(3, config.knownArenaIds().size());
        assertEquals(2, config.anchors().size());
        assertEquals(4, config.anchors().getFirst().copies());
    }

    @Test
    void rejectsUnknownDuplicateAndInsufficientAnchors() throws Exception {
        Path file = directory.resolve("known.json");
        Files.writeString(file, "{\"version\":\"v1\",\"arenaIds\":[1101,1102]}");
        ScanEvidenceConfigLoader loader = new ScanEvidenceConfigLoader();

        assertThrows(IllegalArgumentException.class, () -> loader.load(file, "1101=4\n"));
        assertThrows(IllegalArgumentException.class, () -> loader.load(file, "1101=4\n9999=2\n"));
        assertThrows(IllegalArgumentException.class, () -> loader.load(file, "1101=4\n1101=2\n"));
    }

    @Test
    void explainsWhenCatalogHasNotBeenBuilt() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new ScanEvidenceConfigLoader().load(directory.resolve("missing.json"),
                        "1101=4\n1102=2\n"));

        assertTrue(error.getMessage().contains("does not exist"));
        assertTrue(error.getMessage().contains("Build from Arena install"));
    }
}
