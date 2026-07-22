package app.coaching.application;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CoachingReferenceTextResolverTest {
    private final CoachingReferenceTextResolver resolver = new CoachingReferenceTextResolver();

    @Test
    void resolvesCardAliasesAndSpecificObjectsWithoutChangingUnknownReferences() {
        String reconstruction = """
                MTGA_MATCH_V4
                CARD c1=Fynn, the Fangbearer@94065
                CARD c13=Entity Tracker@92119
                """;
        String response = "Cast [c1], then blocked with [c13#251]. Review [E52] and [c99].";

        assertEquals(
                "Cast Fynn, the Fangbearer [c1], then blocked with Entity Tracker [c13#251]. "
                        + "Review [E52] and [c99].",
                resolver.resolve(reconstruction, response));
    }
}
