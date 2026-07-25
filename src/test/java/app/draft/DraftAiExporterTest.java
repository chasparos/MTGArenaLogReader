package app.draft;

import app.draft.export.DraftAiExporter;
import app.draft.model.DraftCardCount;
import app.draft.model.DraftPickState;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class DraftAiExporterTest {
    @Test
    void exportCarriesStableIdentityRulesTextAndFaceData() {
        CardFaceInfo face = new CardFaceInfo();
        face.setName("Known Front");
        face.setManaCost("{1}{U}");
        face.setTypeLine("Creature — Test");
        face.setOracleText("Flying");

        CardInfo card = new CardInfo();
        card.setArenaId(123L);
        card.setId("scryfall-id");
        card.setName("Known Card");
        card.setSet("tst");
        card.setCollectorNumber("7");
        card.setManaCost("{1}{U}");
        card.setCmc(2.0);
        card.setTypeLine("Creature — Test");
        card.setOracleText("Flying");
        card.setCardFaces(List.of(face));

        DraftPickState state = new DraftPickState(
                "draft", 1, 1, List.of(123L), null,
                List.of(new DraftCardCount(123L, 1)), List.of(), List.of(), Map.of(123L, card));

        String export = new DraftAiExporter().export(state);

        assertTrue(export.contains("MTGA_DRAFT_PICK_REQUEST_V1"));
        assertTrue(export.contains("arenaId=123"));
        assertTrue(export.contains("scryfallId=scryfall-id"));
        assertTrue(export.contains("oracleText=Flying"));
        assertTrue(export.contains("face1Name=Known Front"));
        assertTrue(export.contains("Do not substitute similarly named cards"));
    }
}
