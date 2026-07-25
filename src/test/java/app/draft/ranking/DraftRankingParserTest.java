package app.draft.ranking;

import app.draft.model.DraftTier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DraftRankingParserTest {
    @Test
    void parsesProtocolRowsAndIgnoresHeaders() {
        var ratings = new DraftRankingParser().parse("""
                MTGA_DRAFT_SET_RANKING_V1
                set=DFT
                - arenaId=101; name=Great Card; tier=S; note=Efficient
                - arenaId=102; name=Fine Card; tier=c; note=Playable
                """);

        assertEquals(2, ratings.size());
        assertEquals(101, ratings.getFirst().arenaId());
        assertEquals(DraftTier.S, ratings.getFirst().tier());
        assertEquals(DraftTier.C, ratings.getLast().tier());
    }

    @Test
    void rejectsResponsesWithoutRankingRows() {
        assertThrows(IllegalArgumentException.class,
                () -> new DraftRankingParser().parse("No rankings here"));
    }
}
