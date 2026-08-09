package app.collection.ui;

import app.collection.CollectionUpdate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only, offline card choices from the installed Arena client's own localized database. */
public final class ArenaCardOptionCatalog {
    public List<CollectionUpdate.CardOption> load(Path arenaRoot) throws IOException, SQLException {
        Path raw = arenaRoot.resolve("MTGA_Data").resolve("Downloads").resolve("Raw");
        Path database;
        try (var files = Files.list(raw)) {
            database = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("Raw_CardDatabase_"))
                    .filter(path -> path.getFileName().toString().endsWith(".mtga"))
                    .max(Comparator.comparingLong(this::modifiedTime))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Arena CardDatabase was not found under " + raw));
        }
        return loadDatabase(database);
    }

    List<CollectionUpdate.CardOption> loadDatabase(Path database) throws SQLException {
        String sql = """
                SELECT c.GrpId, l.Loc, c.ExpansionCode, c.CollectorNumber
                FROM Cards c
                JOIN Localizations_enUS l ON l.LocId = c.TitleId AND l.Formatted = 1
                WHERE c.IsToken = 0 AND c.IsPrimaryCard = 1
                  AND c.GrpId >= 1000 AND c.GrpId < 900000
                  AND l.Loc IS NOT NULL AND TRIM(l.Loc) <> ''
                ORDER BY LOWER(l.Loc), c.ExpansionCode, c.CollectorNumber, c.GrpId
                """;
        List<CollectionUpdate.CardOption> options = new ArrayList<>();
        String url = "jdbc:sqlite:" + database.toAbsolutePath().toUri() + "?mode=ro";
        try (var connection = DriverManager.getConnection(url);
             var statement = connection.prepareStatement(sql);
             var rows = statement.executeQuery()) {
            while (rows.next()) {
                String setCode = java.util.Objects.requireNonNullElse(rows.getString(3), "");
                options.add(new CollectionUpdate.CardOption(rows.getLong(1), rows.getString(2),
                        setCode, "", java.util.Objects.requireNonNullElse(rows.getString(4), "")));
            }
        }
        return List.copyOf(options);
    }

    private long modifiedTime(Path path) {
        try { return Files.getLastModifiedTime(path).toMillis(); }
        catch (IOException error) { return Long.MIN_VALUE; }
    }
}
