package app.deckplanner.export;

import app.deckplanner.candidate.CandidateWorkspaceState;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class DeckBuildRequestExporterTest {

    @Test void exportsCandidateSetNoteCategoriesAuthoritativeFactsAndAnalysisBriefDeterministically() {
        CardInfo creature = card("oracle-a", "scry-a", 101L, "Engine Adept",
                "{1}{U}", 2.0, "Creature — Wizard", "Draw a card.\nThen discard a card.");
        creature.setPower("2");
        creature.setToughness("3");
        creature.setColors(List.of("U"));
        creature.setColorIdentity(List.of("U"));
        creature.setKeywords(List.of("Ward"));
        creature.setProducedMana(List.of("U"));

        CandidateWorkspaceState.Snapshot workspace = new CandidateWorkspaceState.Snapshot(
                List.of(new CandidateWorkspaceState.Category("engine", "Engine")),
                Map.of("oracle:oracle-a", "engine"));

        DeckBuildRequestExporter exporter = new DeckBuildRequestExporter();
        String first = exporter.export("Standard", "Tempo shell",
                "Play at instant speed.\nProtect the engine.",
                List.of("oracle:oracle-a"), workspace,
                identity -> Optional.of(creature), ignored -> -1);
        String second = exporter.export("Standard", "Tempo shell",
                "Play at instant speed.\nProtect the engine.",
                List.of("oracle:oracle-a"), workspace,
                identity -> Optional.of(creature), ignored -> -1);

        assertEquals(first, second);
        assertTrue(first.startsWith("MTGA_DECK_BUILD_REQUEST_V1\n"));
        assertTrue(first.contains("SET name=\"Tempo shell\""));
        assertTrue(first.contains("NOTE \"Play at instant speed.\\nProtect the engine.\""));
        assertTrue(first.contains("CATEGORIES G1=\"Engine\""));
        assertTrue(first.contains("qty=-1"));
        assertTrue(first.contains("name=\"Engine Adept\""));
        assertTrue(first.contains("type=\"Creature — Wizard\""));
        assertTrue(first.contains("text=\"Draw a card.\\nThen discard a card.\""));
        assertTrue(first.contains("I'm working on designing a MTGArena deck"));
        assertTrue(first.contains("plausible win conditions"));
        assertTrue(first.contains("probably should be removed"));
        assertFalse(first.contains("What deck would you build with these cards?"));
    }

    @Test void exportsEveryFaceAndKeepsUnknownCandidateExplicit() {
        CardInfo transform = card("oracle-b", "scry-b", 202L, "Daybound Example",
                null, 3.0, null, null);
        CardFaceInfo front = new CardFaceInfo();
        front.setName("Day Face");
        front.setManaCost("{2}{G}");
        front.setTypeLine("Creature — Human");
        front.setOracleText("Daybound");
        front.setPower("3");
        front.setToughness("3");
        CardFaceInfo back = new CardFaceInfo();
        back.setName("Night Face");
        back.setTypeLine("Creature — Werewolf");
        back.setOracleText("Nightbound");
        back.setPower("4");
        back.setToughness("4");
        transform.setCardFaces(List.of(front, back));

        String out = new DeckBuildRequestExporter().export(
                "standard", "Transforms", "",
                List.of("oracle:oracle-b", "oracle:missing"),
                CandidateWorkspaceState.defaults(),
                identity -> identity.endsWith("oracle-b")
                        ? Optional.of(transform) : Optional.empty(),
                ignored -> 0);

        assertTrue(out.contains("FACE C1.1 name=\"Day Face\""));
        assertTrue(out.contains("FACE C1.2 name=\"Night Face\""));
        assertTrue(out.contains("CARD C2 id=\"oracle:missing\""));
        assertTrue(out.contains("status=UNRESOLVED"));
    }

    @Test void preservesZeroPositiveAndUnknownCollectionStates() {
        CardInfo a = card("oa", "sa", 1L, "A", "", 1.0, "Instant", "A");
        CardInfo b = card("ob", "sb", 2L, "B", "", 2.0, "Sorcery", "B");
        CardInfo c = card("oc", "sc", 3L, "C", "", 3.0, "Creature", "C");
        Map<String, CardInfo> cards = Map.of("a", a, "b", b, "c", c);

        String out = new DeckBuildRequestExporter().export(
                "standard", "Qty", "", List.of("a", "b", "c"),
                CandidateWorkspaceState.defaults(),
                id -> Optional.of(cards.get(id)),
                card -> card == a ? -1 : card == b ? 0 : 3);

        assertTrue(out.contains("CARD C1 id=\"a\" category=G1 qty=-1"));
        assertTrue(out.contains("CARD C2 id=\"b\" category=G1 qty=0"));
        assertTrue(out.contains("CARD C3 id=\"c\" category=G1 qty=3"));
    }

    private static CardInfo card(String oracleId, String scryfallId, long arenaId,
                                 String name, String mana, Double mv,
                                 String type, String text) {
        CardInfo card = new CardInfo();
        card.setOracleId(oracleId);
        card.setId(scryfallId);
        card.setArenaId(arenaId);
        card.setName(name);
        card.setManaCost(mana);
        card.setCmc(mv);
        card.setTypeLine(type);
        card.setOracleText(text);
        return card;
    }
}
