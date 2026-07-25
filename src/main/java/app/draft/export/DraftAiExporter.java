package app.draft.export;

import app.draft.model.DraftCardCount;
import app.draft.model.DraftCardRating;
import app.draft.model.DraftPickState;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class DraftAiExporter {
    public String export(DraftPickState state) {
        return export(state, Map.of());
    }

    public String export(
            DraftPickState state,
            Map<Long, DraftCardRating> ratings) {
        StringBuilder out = new StringBuilder();
        out.append("MTGA_DRAFT_PICK_REQUEST_V1\n");
        out.append("Use only the supplied card data. Do not substitute similarly named cards or invent rules text.\n");
        out.append("Arena-observed: draftId=").append(state.draftId())
                .append(", pack=").append(state.packNumber())
                .append(", pick=").append(state.pickNumber()).append("\n\n");
        out.append("[CURRENT PACK]\n");
        for (long id : state.offeredCardIds()) {
            appendCard(out, id, 1, state.cards(), ratings);
        }
        out.append("\n[CURRENT COLLECTION]\n");
        for (DraftCardCount entry : state.draftedPool()) {
            appendCard(out, entry.arenaId(), entry.quantity(), state.cards(), ratings);
        }
        out.append("\n[CURRENT MAIN DECK]\n");
        appendCounts(out, state.mainDeck(), state.cards(), ratings);
        out.append("\n[CURRENT SIDEBOARD]\n");
        appendCounts(out, state.sideboard(), state.cards(), ratings);
        out.append("\n[REQUEST]\nRecommend the pick, explain the top alternatives, and describe what the current collection indicates we are building. Separate observed facts from strategic inference.\n");
        return out.toString();
    }

    private void appendCounts(
            StringBuilder out,
            List<DraftCardCount> counts,
            Map<Long, CardInfo> cards,
            Map<Long, DraftCardRating> ratings) {
        if (counts.isEmpty()) out.append("(not observed)\n");
        for (DraftCardCount entry : counts) {
            appendCard(out, entry.arenaId(), entry.quantity(), cards, ratings);
        }
    }

    private void appendCard(
            StringBuilder out,
            long arenaId,
            int quantity,
            Map<Long, CardInfo> cards,
            Map<Long, DraftCardRating> ratings) {
        CardInfo card = cards.get(arenaId);
        out.append("- quantity=").append(quantity).append("; arenaId=").append(arenaId);
        DraftCardRating rating = ratings.get(arenaId);
        if (rating != null) {
            field(out, "setTier", rating.tier().name());
            field(out, "setTierNote", rating.note());
        }
        if (card == null) {
            out.append("; metadata=unavailable\n");
            return;
        }
        field(out, "name", card.getName());
        field(out, "scryfallId", card.getId());
        field(out, "set", card.getSet());
        field(out, "collectorNumber", card.getCollectorNumber());
        field(out, "layout", card.getLayout());
        field(out, "manaCost", card.getManaCost());
        if (card.getCmc() != null) field(out, "manaValue", card.getCmc().toString());
        field(out, "typeLine", card.effectiveTypeLine());
        field(out, "oracleText", card.effectiveOracleText());
        field(out, "power", card.getPower());
        field(out, "toughness", card.getToughness());
        field(out, "loyalty", card.getLoyalty());
        field(out, "defense", card.getDefense());
        field(out, "colors", join(card.getColors()));
        field(out, "colorIdentity", join(card.getColorIdentity()));
        field(out, "keywords", join(card.getKeywords()));
        field(out, "producedMana", join(card.getProducedMana()));
        field(out, "rarity", card.getRarity());
        if (card.getCardFaces() != null && !card.getCardFaces().isEmpty()) {
            int faceNumber = 1;
            for (CardFaceInfo face : card.getCardFaces()) {
                if (face == null) continue;
                field(out, "face" + faceNumber + "Name", face.getName());
                field(out, "face" + faceNumber + "ManaCost", face.getManaCost());
                field(out, "face" + faceNumber + "TypeLine", face.getTypeLine());
                field(out, "face" + faceNumber + "OracleText", face.getOracleText());
                faceNumber++;
            }
        }
        out.append('\n');
    }

    private void field(StringBuilder out, String name, String value) {
        if (value == null || value.isBlank()) return;
        out.append("; ").append(name).append('=').append(value.replace("\n", "\\n"));
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) return "";
        return new StringJoiner(",").add(String.join(",", values)).toString();
    }
}
