package app.collection.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ArenaCardOptionCatalogTest {
    @TempDir Path directory;

    @Test
    void loadsLocalizedPrimaryNonTokenPrintingsInSearchOrder() throws Exception {
        Path database = directory.resolve("cards.mtga");
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database)) {
            try (var statement = connection.createStatement()) {
                statement.execute("CREATE TABLE Cards (GrpId INT, TitleId INT, ExpansionCode TEXT, "
                        + "CollectorNumber TEXT, IsToken BOOLEAN, IsPrimaryCard BOOLEAN)");
                statement.execute("CREATE TABLE Localizations_enUS (LocId INT, Formatted INT, Loc TEXT)");
                statement.execute("INSERT INTO Localizations_enUS VALUES "
                        + "(1,1,'Zulu Card'),(1,0,'unused'),(2,1,'Alpha Card'),(3,1,'Token')");
                statement.execute("INSERT INTO Cards VALUES "
                        + "(1001,1,'SET','2',0,1),(1002,2,'SET','1',0,1),(1003,3,'SET','3',1,1)");
            }
        }

        var options = new ArenaCardOptionCatalog().loadDatabase(database);

        assertEquals(2, options.size());
        assertEquals("Alpha Card", options.getFirst().name());
        assertEquals(1002L, options.getFirst().arenaId());
        assertEquals("Zulu Card", options.getLast().name());
    }
}
