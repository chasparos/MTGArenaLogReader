package app.export;

import app.model.card.CardInfo;
import app.model.event.AbilityReference;
import app.model.event.DecisionObservation;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.event.ObjectReference;
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

        assertTrue(report.startsWith("MTGA_MATCH_V4\n"));
        assertTrue(report.contains("PLAYERS p1=Me|p2=Opponent"));
        assertTrue(report.indexOf("\nG1") < report.indexOf("\nG2"));
        assertTrue(report.contains("S#1 p1 life=18 poison=0 hand=5 board=c1#10[2/2,tap]"));
        assertTrue(report.contains("H player=p1 mull=1 cards=c2|c3"));
        assertTrue(report.contains("P Main1"));
        assertTrue(report.contains("E#2 text=p2 plays Mountain"));
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
                "GR#1 winner=p1 reason=DAMAGE confidence=CORRELATED card=c1"));
        assertTrue(report.contains("MS#2 1-0"));
        assertFalse(report.contains("Me wins by damage"));
    }


    @Test
    void exportsAbilitySourceAndArenaCardIdentityStructurally() {
        MatchSession match = new MatchSession("match-ability", new AbilityNameStore());
        GameModel game = match.game(1).model();

        CardInfo sourceCard = card("The Serpent Society");
        sourceCard.setArenaId(12345L);

        AbilityReference ability = new AbilityReference();
        ability.setKind("triggered");
        ability.setSourceName("The Serpent Society");
        ability.setSourceGrpId(12345L);
        ability.setAbilityGrpId(67890L);

        GameEvent event = new GameEvent();
        event.setAbility(ability);
        event.setText("Me puts an ability from The Serpent Society on the stack");
        event.getCards().add(sourceCard);
        game.addEvents(List.of(event));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "A#1 kind=triggered source=c1@12345 abilityGrp=67890"));
        assertTrue(report.contains("cards=c1@12345"));
    }


    @Test
    void exportsExplicitTargetDecisionWithChosenAndAlternativeObjects() {
        MatchSession match = new MatchSession("match-decision", new AbilityNameStore());
        GameModel game = match.game(1).model();

        ObjectReference source = new ObjectReference(
                40, 140, 9001, "Bushwhack", null, null);
        ObjectReference chosen = new ObjectReference(
                41, 141, 9002, "Engine Rat", null, null);
        ObjectReference alternative = new ObjectReference(
                42, 142, 9003, "Tinybones", null, null);

        GameEvent decisionEvent = new GameEvent();
        decisionEvent.setType(GameEventType.DECISION);
        decisionEvent.setDecision(new DecisionObservation(
                DecisionObservation.Kind.TARGET,
                source,
                List.of(chosen),
                List.of(alternative),
                1,
                1,
                DecisionObservation.Confidence.EXPLICIT));
        game.addEvents(List.of(decisionEvent));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "C#1 kind=TARGET confidence=EXPLICIT source=c1#40@9001"
                        + " chosen=c2#41@9002"
                        + " alternatives=c3#42@9003 min=1 max=1"));
    }

    private CardInfo card(String name) {
        CardInfo card = new CardInfo();
        card.setName(name);
        return card;
    }
}
