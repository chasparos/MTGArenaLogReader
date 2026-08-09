package app.collection.memory.extraction;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class ArenaKnownIdCatalogProducerTest {
    @TempDir Path directory;

    @Test
    void producesAReadableVersionedCatalogFromArenaSchema() throws Exception {
        Path database = directory.resolve("Raw_CardDatabase_fixture.mtga");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE Cards (GrpId INTEGER NOT NULL)");
            for (int id : IntStream.range(1_000, 1_120).toArray()) {
                statement.execute("INSERT INTO Cards VALUES (" + id + ")");
            }
            statement.execute("INSERT INTO Cards VALUES (1001)");
            statement.execute("INSERT INTO Cards VALUES (9999999)");
        }
        Path output = directory.resolve("known.json");

        var result = new ArenaKnownIdCatalogProducer().produceFromDatabase(database, output);
        var loaded = new ScanEvidenceConfigLoader().load(output, "1001=4\n1002=2\n");

        assertEquals(120, result.knownIds());
        assertTrue(result.version().startsWith("arena-local:Raw_CardDatabase_fixture.mtga:"));
        assertEquals(120, loaded.knownArenaIds().size());
    }
}
