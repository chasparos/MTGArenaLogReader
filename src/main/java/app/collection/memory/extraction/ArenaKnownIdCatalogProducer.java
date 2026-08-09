package app.collection.memory.extraction;

import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Produces scanner evidence from Arena's own read-only Raw CardDatabase. */
public final class ArenaKnownIdCatalogProducer {
    public record Result(Path source, Path output, String version, int knownIds) { }

    public Result produce(Path arenaRoot, Path output) throws IOException, SQLException {
        Path raw = arenaRoot.resolve("MTGA_Data").resolve("Downloads").resolve("Raw");
        Path database;
        try (var files = Files.list(raw)) {
            database = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("Raw_CardDatabase_"))
                    .filter(path -> path.getFileName().toString().endsWith(".mtga"))
                    .max(Comparator.comparingLong(this::modifiedTime))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Arena Raw CardDatabase was not found under " + raw));
        }
        return produceFromDatabase(database, output);
    }

    Result produceFromDatabase(Path database, Path output) throws IOException, SQLException {
        SortedSet<Long> ids = new TreeSet<>();
        String url = "jdbc:sqlite:" + database.toAbsolutePath().toUri() + "?mode=ro";
        try (Connection connection = DriverManager.getConnection(url);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT GrpId FROM Cards")) {
            while (rows.next()) {
                long id = rows.getLong(1);
                if (id >= 1_000 && id < 900_000) ids.add(id);
            }
        }
        if (ids.size() < 100) throw new IllegalArgumentException(
                "Arena CardDatabase produced too few valid IDs: " + ids.size());
        String version = "arena-local:" + database.getFileName() + ":"
                + Files.size(database) + ":" + Files.getLastModifiedTime(database).toMillis();
        CatalogDocument document = new CatalogDocument(version, new ArrayList<>(ids));
        Path absoluteOutput = output.toAbsolutePath();
        Files.createDirectories(absoluteOutput.getParent());
        Path temporary = absoluteOutput.resolveSibling(absoluteOutput.getFileName() + ".tmp");
        Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(document),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        try {
            Files.move(temporary, absoluteOutput, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, absoluteOutput, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Result(database, absoluteOutput, version, ids.size());
    }

    private long modifiedTime(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException error) { return Long.MIN_VALUE; }
    }

    private record CatalogDocument(String version, List<Long> arenaIds) { }
}
