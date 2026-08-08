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
            repository.save("Control shell", List.of("oracle:b", "oracle:a"), workspace);
        }

        try (CandidateSetRepository reopened = new CandidateSetRepository(database)) {
            assertEquals(workspace, reopened.loadWorkspace());
            CandidateSetRepository.CandidateSet loaded =
                    reopened.load("Control shell").orElseThrow();
            assertEquals(List.of("oracle:b", "oracle:a"), loaded.identities());
            assertEquals(workspace, loaded.workspace());
            assertEquals(List.of("Control shell"),
                    reopened.list().stream().map(CandidateSetRepository.CandidateSet::name).toList());
        }
    }
}
