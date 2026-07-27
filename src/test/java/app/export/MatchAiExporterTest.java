package app.export;

import app.model.card.CardInfo;
import app.model.event.AbilityReference;
import app.model.event.DecisionObservation;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.event.ObjectReference;
import app.model.event.TargetObservation;
import app.model.event.SemanticConfidence;
import app.model.event.SemanticProvenance;
import app.model.event.ZoneTransitionObservation;
import app.model.event.ZoneTransitionReason;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.GameResult;
import app.model.game.PlayerTurnSnapshot;
import app.model.match.MatchScore;
import app.model.session.GameModel;
import app.model.session.MatchSession;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchAiExporterTest {

    @Test
    void exportsAllKnownGamesInOrderWithCompactSemanticState() {
        MatchSession match = new MatchSession("match-123");
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

        assertTrue(report.startsWith("MTGA_MATCH_V5\n"));
        assertTrue(report.contains("PLAYERS p1=\"Me\"|p2=\"Opponent\""));
        assertTrue(report.indexOf("\nG1") < report.indexOf("\nG2"));
        assertTrue(report.contains("S#1 p1 life=18 poison=0 hand=5 board=c1#10[2/2,tap]"));
        assertTrue(report.contains("H player=p1 mull=1 cards=c2|c3"));
        assertTrue(report.contains("P Main1"));
        assertTrue(report.contains("E#2 text=\"Opponent plays Mountain\""));
        assertFalse(report.contains("large raw record"));
    }

    @Test
    void emitsStructuredResultsWithoutDuplicatingPresentationText() {
        MatchSession match = new MatchSession("match-456");
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
        MatchSession match = new MatchSession("match-ability");
        GameModel game = match.game(1).model();

        CardInfo sourceCard = card("The Serpent Society");
        sourceCard.setArenaId(12345L);

        AbilityReference ability = new AbilityReference();
        ability.setKind("triggered");
        ability.setSourceName("The Serpent Society");
        ability.setSourceGrpId(12345L);
        ability.setAbilityGrpId(67890L);
        ability.setChapter(2);
        ability.setEffectText("Mill two cards, then draw a card.");
        ability.setConfidence("ORACLE_HEURISTIC");

        GameEvent event = new GameEvent();
        event.setAbility(ability);
        event.setText("Me puts an ability from The Serpent Society on the stack");
        event.getCards().add(sourceCard);
        game.addEvents(List.of(event));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "A#1 kind=triggered source=c1@12345 abilityGrp=67890 chapter=2 "
                        + "effect=\"Mill two cards, then draw a card.\" "
                        + "confidence=ORACLE_HEURISTIC"));
        assertTrue(report.contains("cards=c1@12345"));
    }


    @Test
    void exportsExplicitTargetDecisionWithChosenAndAlternativeObjects() {
        MatchSession match = new MatchSession("match-decision");
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


    @Test
    void exportsStableSpellOrAbilityTargetReferences() {
        MatchSession match = new MatchSession("match-target");
        GameModel game = match.game(1).model();

        ObjectReference source = new ObjectReference(
                50, 150, 9101, "Murder", null, null);
        ObjectReference target = new ObjectReference(
                51, 151, 9102, "Engine Rat", null, null);

        GameEvent event = new GameEvent();
        event.setText("Murder targets Engine Rat");
        event.setTargetObservation(new TargetObservation(
                source,
                73001L,
                List.of(target),
                SemanticProvenance.ARENA_TARGET_DECLARATION,
                SemanticConfidence.EXPLICIT));
        game.addEvents(List.of(event));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "TARGET#1 provenance=ARENA_TARGET_DECLARATION confidence=EXPLICIT source=c1#50@9101"
                        + " abilityGrp=73001 targets=c2#51@9102"
                        + " text=\"Murder targets Engine Rat\""));
    }


    @Test
    void exportsKnownZonesAndObservedPermanentInstanceState() {
        MatchSession match = new MatchSession("match-state");
        match.matchState().observePlayers(Map.of(1, "Me"));
        GameModel game = match.game(1).model();

        PlayerTurnSnapshot player = new PlayerTurnSnapshot();
        player.setSeatId(1);
        player.setPlayerName("Me");
        player.setLifeTotal(17);
        player.setPoisonCounters(2);
        player.setHandSize(3);

        CardInfo roomCard = card("Mirror Room // Fractured Realm");
        roomCard.setArenaId(92135L);
        roomCard.setTypeLine("Enchantment — Room");

        BoardPermanentSnapshot room = new BoardPermanentSnapshot();
        room.setLogicalObjectId(261);
        room.setOwnerSeatId(1);
        room.setControllerSeatId(1);
        room.setName(roomCard.getName());
        room.setCard(roomCard);
        room.getUnlockedRoomHalves().add("Mirror Room");
        player.getBattlefield().add(room);

        CardInfo creatureCard = card("Fynn, the Fangbearer");
        creatureCard.setArenaId(94065L);
        creatureCard.setTypeLine("Legendary Creature — Human Warrior");

        BoardPermanentSnapshot creature = new BoardPermanentSnapshot();
        creature.setLogicalObjectId(192);
        creature.setOwnerSeatId(1);
        creature.setControllerSeatId(1);
        creature.setName(creatureCard.getName());
        creature.setCard(creatureCard);
        creature.setPower(1);
        creature.setToughness(3);
        creature.getEvergreenAbilities().add("Deathtouch");

        CounterState shield = new CounterState();
        shield.setArenaType(999);
        shield.setType("Shield");
        shield.setCount(1);
        creature.getCounters().add(shield);
        player.getBattlefield().add(creature);

        CardInfo knownLand = card("Island");
        knownLand.setArenaId(92375L);
        knownLand.setTypeLine("Basic Land — Island");
        CardInfo knownCreature = card("Engine Rat");
        knownCreature.setArenaId(94886L);
        knownCreature.setTypeLine("Artifact Creature — Rat");
        CardInfo knownSpell = card("Corrupted Conviction");
        knownSpell.setArenaId(84366L);
        knownSpell.setTypeLine("Instant");
        player.getKnownHand().add(knownCreature);
        player.getKnownHand().add(knownLand);
        player.getKnownGraveyard().add(knownSpell);

        GameEvent snapshot = new GameEvent();
        snapshot.setTurnNumber(4);
        snapshot.getTurnSnapshot().add(player);
        game.addEvents(List.of(snapshot));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains("STATE knownH/knownG/knownX"));
        assertTrue(report.contains("c1#261[unlocked=Mirror Room]"));
        assertTrue(report.contains("c2#192[1/3,abilities=Deathtouch,Shield=1]"));
        assertTrue(report.contains("knownH=c4@92375|c3@94886"));
        assertTrue(report.contains("knownG=c5@84366"));
    }


    @Test
    void aliasesOnlyStructuredIdentityFieldsAndEscapesFreeText() {
        MatchSession match = new MatchSession("match-\"quoted\"");
        match.matchState().observePlayers(Map.of(1, "Will"));
        GameModel game = match.game(1).model();

        CardInfo bear = card("Bear");
        bear.setArenaId(77L);

        GameEvent event = new GameEvent();
        event.setActivePlayerName("Will");
        event.setTurnNumber(1);
        event.setText("Willpower makes Bearable text \"safe\".\nNext line.");
        event.getCards().add(bear);
        game.addEvents(List.of(event));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains("match=\"match-\\\"quoted\\\"\""));
        assertTrue(report.contains("PLAYERS p1=\"Will\""));
        assertTrue(report.contains("CARD c1=\"Bear\"@77"));
        assertTrue(report.contains("T1 active=p1"));
        assertTrue(report.contains("cards=c1@77"));
        assertTrue(report.contains(
                "text=\"Willpower makes Bearable text \\\"safe\\\". Next line.\""));
        assertFalse(report.contains("p1power"));
        assertFalse(report.contains("c1able"));
        assertFalse(report.contains("MTGA_MATCH_V3"));
    }

    @Test
    void exportsStructuredZoneTransitionReasonAndSubject() {
        MatchSession match = new MatchSession("match-zone-transition");
        GameModel game = match.game(1).model();

        ObjectReference subject = new ObjectReference(
                51, 151, 7001, "Ral", null, null);
        GameEvent event = new GameEvent();
        event.setText("Ral is countered and put into the graveyard");
        event.getObjects().add(subject);
        event.setZoneTransition(new ZoneTransitionObservation(
                "Stack", "Graveyard", ZoneTransitionReason.COUNTERED, subject,
                SemanticProvenance.ARENA_CATEGORY, SemanticConfidence.EXPLICIT));
        game.addEvents(List.of(event));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "MOVE#1 S>G reason=COUNTERED provenance=ARENA_CATEGORY confidence=EXPLICIT subject=c1#51@7001 "
                        + "text=\"Ral is countered and put into the graveyard\""));
    }


    @Test
    void exportsConservativeTargetToZoneOutcomeCausalLink() {
        MatchSession match = new MatchSession("match-causal");
        GameModel game = match.game(1).model();

        ObjectReference source = new ObjectReference(50, 150, 9101, "Murder", null, null);
        ObjectReference target = new ObjectReference(51, 151, 9102, "Engine Rat", null, null);

        GameEvent targetEvent = new GameEvent();
        targetEvent.setTargetObservation(new TargetObservation(
                source, 73001L, List.of(target), SemanticProvenance.ARENA_TARGET_DECLARATION, SemanticConfidence.EXPLICIT));

        GameEvent outcomeEvent = new GameEvent();
        outcomeEvent.setZoneTransition(new ZoneTransitionObservation(
                "Battlefield", "Graveyard", ZoneTransitionReason.PUT_INTO_GRAVEYARD, target,
                SemanticProvenance.ZONE_PATTERN, SemanticConfidence.INFERRED));
        outcomeEvent.setText("Engine Rat is destroyed");
        game.addEvents(List.of(targetEvent, outcomeEvent));

        String report = new MatchAiExporter().export(match);

        assertTrue(report.contains(
                "LINK#2 cause=TARGET#1 outcome=DESTROY provenance=UNIQUE_TARGET_CORRELATION confidence=CORRELATED"));
    }

    @Test
    void omitsCausalLinkWhenMultipleTargetsCouldExplainOutcome() {
        MatchSession match = new MatchSession("match-ambiguous-causal");
        GameModel game = match.game(1).model();

        ObjectReference target = new ObjectReference(51, 151, 9102, "Engine Rat", null, null);
        for (long sourceId : List.of(50L, 60L)) {
            GameEvent targetEvent = new GameEvent();
            targetEvent.setTargetObservation(new TargetObservation(
                    new ObjectReference(sourceId, sourceId + 100, sourceId + 9000,
                            "Removal " + sourceId, null, null),
                    73001L + sourceId, List.of(target), SemanticProvenance.ARENA_TARGET_DECLARATION, SemanticConfidence.EXPLICIT));
            game.addEvents(List.of(targetEvent));
        }

        GameEvent outcomeEvent = new GameEvent();
        outcomeEvent.setZoneTransition(new ZoneTransitionObservation(
                "Battlefield", "Graveyard", ZoneTransitionReason.PUT_INTO_GRAVEYARD, target,
                SemanticProvenance.ZONE_PATTERN, SemanticConfidence.INFERRED));
        game.addEvents(List.of(outcomeEvent));

        String report = new MatchAiExporter().export(match);

        assertFalse(report.contains("outcome=DESTROY provenance=UNIQUE_TARGET_CORRELATION confidence=CORRELATED"));
    }

    private CardInfo card(String name) {
        CardInfo card = new CardInfo();
        card.setName(name);
        return card;
    }
}
