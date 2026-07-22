package app.coaching.persistence;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CoachingGameSnapshotCodecTest {
    @Test
    void restoresSemanticGameForRichReplay() {
        GameModel game = new GameModel();
        game.setMatchId("match-1");
        game.setGameNumber(2);
        CardInfo forest = new CardInfo();
        forest.setArenaId(123L);
        forest.setName("Forest");
        game.setOpeningHand("Player", 1, List.of(forest));

        GameEvent event = new GameEvent();
        event.setSequence(42);
        event.setTimestamp(Instant.parse("2026-01-02T03:04:05Z"));
        event.setTurnNumber(3);
        event.setActivePlayerName("Player");
        event.setText("Player plays Forest");
        event.getCards().add(forest);
        game.addEvents(List.of(event));

        CoachingGameSnapshotCodec codec = new CoachingGameSnapshotCodec();
        GameModel restored = codec.decode(codec.encode(game));

        assertEquals("match-1", restored.getMatchId());
        assertEquals(2, restored.getGameNumber());
        assertEquals("Player", restored.getOpeningHandPlayer());
        assertEquals(1, restored.getMulliganCount());
        assertEquals("Forest", restored.openingHandSnapshot().getFirst().getName());
        assertEquals(1, restored.snapshot().size());
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), restored.snapshot().getFirst().getTimestamp());
        assertEquals("Player plays Forest", restored.snapshot().getFirst().getText());
        assertEquals("Forest", restored.snapshot().getFirst().getCards().getFirst().getName());
    }
}
