package app.deckplanner.consideration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UnderConsiderationRepositoryTest {
    @TempDir Path temp;

    @Test void orderedMembershipSurvivesRepositoryRestart() {
        Path database = temp.resolve("planner");
        try (UnderConsiderationRepository repository = new UnderConsiderationRepository(database)) {
            repository.replace(List.of("oracle:b", "oracle:a", "oracle:b", "oracle:c"));
            assertEquals(List.of("oracle:b", "oracle:a", "oracle:c"), repository.load());
        }

        try (UnderConsiderationRepository reopened = new UnderConsiderationRepository(database)) {
            assertEquals(List.of("oracle:b", "oracle:a", "oracle:c"), reopened.load());
            reopened.replace(List.of("oracle:c", "oracle:b"));
        }

        try (UnderConsiderationRepository reopened = new UnderConsiderationRepository(database)) {
            assertEquals(List.of("oracle:c", "oracle:b"), reopened.load());
        }
    }
}
