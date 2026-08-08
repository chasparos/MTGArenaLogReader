package app.deckplanner.candidate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CandidateSetRepositoryTest {
    @TempDir Path temp;

    @Test void workspaceCategoriesAndNamedCandidateSetsRoundTrip() {
        Path database = temp.resolve("planner");
        CandidateWorkspaceState.Snapshot workspace = new CandidateWorkspaceState.Snapshot(
                List.of(new CandidateWorkspaceState.Category("win-cons", "Win Cons"),
                        new CandidateWorkspaceState.Category("creatures", "Creatures")),
                Map.of("oracle:a", "win-cons"));

        try (CandidateSetRepository repository = new CandidateSetRepository(database)) {
            repository.replaceWorkspace(workspace);
            repository.save("Control shell", List.of("oracle:b", "oracle:a"), workspace,
                    "Primary plan:\nStabilize, recur the engine, then close with flyers.");
        }

        try (CandidateSetRepository reopened = new CandidateSetRepository(database)) {
            assertEquals(workspace, reopened.loadWorkspace());
            CandidateSetRepository.CandidateSet loaded =
                    reopened.load("Control shell").orElseThrow();
            assertEquals(List.of("oracle:b", "oracle:a"), loaded.identities());
            assertEquals(workspace, loaded.workspace());
            assertEquals("Primary plan:\nStabilize, recur the engine, then close with flyers.",
                    loaded.note());
            assertEquals(List.of("Control shell"),
                    reopened.list().stream().map(CandidateSetRepository.CandidateSet::name).toList());
        }
    }

    @Test void legacyCandidateSetSchemaAddsNoteColumnWithoutLosingExistingSet() throws Exception {
        Path database = temp.resolve("legacy-planner");
        String jdbc = "jdbc:h2:file:" + database.toAbsolutePath().toString().replace('\\', '/')
                + ";DB_CLOSE_ON_EXIT=FALSE";
        try (java.sql.Connection connection = java.sql.DriverManager.getConnection(jdbc, "sa", "");
             java.sql.Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                    "CREATE TABLE deck_planner_candidate_sets(set_name VARCHAR(256) PRIMARY KEY)");
            statement.executeUpdate(
                    "INSERT INTO deck_planner_candidate_sets(set_name) VALUES ('Legacy set')");
        }

        try (CandidateSetRepository repository = new CandidateSetRepository(database)) {
            CandidateSetRepository.CandidateSet loaded =
                    repository.load("Legacy set").orElseThrow();
            assertEquals("", loaded.note());
        }
    }
}
