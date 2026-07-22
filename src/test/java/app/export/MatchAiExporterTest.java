package app.export;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.GameResult;
import app.model.game.PlayerTurnSnapshot;
import app.model.match.MatchScore;
import app.model.session.GameModel;
import app.model.session.MatchSession;
import app.projection.AbilityNameStore;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchAiExporterTest {

    @Test
    void exportsAllKnownGamesInOrderWithCompactSemanticState() {
        MatchSession match = new MatchSession("match-123", new AbilityNameStore());
        match.matchState().observePlayers(Map.of(2, "Opponent", 1, "Me"));

        GameModel gameTwo = match.game(2).model();
        gameTwo.setOpeningHand("Me", 1, List.of(card("Island"), card("Counterspell")));
        gameTwo.addRawRecord("{large raw record}");

        GameEvent secondGameEvent = new GameEvent();
        secondGameEvent.setTurnNumber(1);
        secondGameEvent.setActivePlayerName("Opponent");
        secondGameEvent.setPhase("Phase_Main1");
        secondGameEvent.setText("Opponent plays Mountain");
        gameTwo.addEvents(List.of(secondGameEvent));

        GameModel gameOne = match.game(1).model();
        GameEvent snapshotEvent = new GameEvent();
        snapshotEvent.setTurnNumber(2);
        snapshotEvent.setActivePlayerName("Me");

        PlayerTurnSnapshot player = new PlayerTurnSnapshot();
        player.setSeatId(1);
        player.setPlayerName("Me");
        player.setLifeTotal(18);
        player.setPoisonCounters(0);
        player.setHandSize(5);

        BoardPermanentSnapshot permanent = new BoardPermanentSnapshot();
        permanent.setLogicalObjectId(10);
        permanent.setOwnerSeatId(1);
        permanent.setControllerSeatId(1);
        permanent.setName("Bear");
        permanent.setPower(2);
        permanent.setToughness(2);
        permanent.setTapped(true);
        player.getBattlefield().add(permanent);
        snapshotEvent.getTurnSnapshot().add(player);
        gameOne.addEvents(List.of(snapshotEvent));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.startsWith("MTGA_MATCH_V1\n"));
        assertTrue(report.contains("players=1:Me|2:Opponent"));
        assertTrue(report.indexOf("\nG1") < report.indexOf("\nG2"));
        assertTrue(report.contains("S Me life=18 poison=0 hand=5 board=Bear[2/2,tap]"));
        assertTrue(report.contains("H player=Me mull=1 cards=Island|Counterspell"));
        assertTrue(report.contains("P Main1"));
        assertTrue(report.contains("E Opponent plays Mountain"));
        assertFalse(report.contains("large raw record"));
    }

    @Test
    void emitsStructuredResultsWithoutDuplicatingPresentationText() {
        MatchSession match = new MatchSession("match-456", new AbilityNameStore());
        GameModel game = match.game(1).model();

        GameResult result = new GameResult();
        result.setWinnerSeatId(1);
        result.setWinnerName("Me");
        result.setReason(GameResult.Reason.DAMAGE);
        result.setConfidence(GameResult.Confidence.CORRELATED);
        result.setFinishingCard("Lightning Bolt");

        GameEvent resultEvent = new GameEvent();
        resultEvent.setText("Me wins by damage");
        resultEvent.setGameResult(result);

        GameEvent scoreEvent = new GameEvent();
        scoreEvent.setMatchScore(new MatchScore(1, 0, 0));
        game.addEvents(List.of(resultEvent, scoreEvent));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "GR winner=Me reason=DAMAGE confidence=CORRELATED card=Lightning Bolt"));
        assertTrue(report.contains("MS 1-0"));
        assertFalse(report.contains("Me wins by damage"));
    }

    private CardInfo card(String name) {
        CardInfo card = new CardInfo();
        card.setName(name);
        return card;
    }
}
