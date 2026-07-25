package app.draft.export;

import app.draft.model.DraftSet;
import app.model.card.CardInfo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Comparator;
import java.util.List;

public final class DraftSetRankingExporter {
    private static final String TEMPLATE_RESOURCE =
            "/draft/protocols/set_ranking_request.txt";
    private final String template;

    public DraftSetRankingExporter() {
        this.template = loadTemplate();
    }

    public String export(DraftSet set, List<CardInfo> cards) {
        if (set == null) throw new IllegalArgumentException("set is required");
        StringBuilder catalog = new StringBuilder();
        cards.stream()
                .filter(card -> card != null && card.getName() != null)
                .sorted(Comparator
                        .comparing(CardInfo::getCollectorNumber,
                                Comparator.nullsLast(
                                        String.CASE_INSENSITIVE_ORDER))
                        .thenComparing(CardInfo::getName))
                .forEach(card -> appendCard(catalog, card));
        return template
                .replace("${setCode}", set.code().toUpperCase())
                .replace("${setName}", set.name())
                .replace("${cards}", catalog.toString().strip())
                .strip();
    }

    private void appendCard(StringBuilder out, CardInfo card) {
        out.append("- arenaId=")
                .append(card.getArenaId() == null ? 0 : card.getArenaId());
        field(out, "name", card.getName());
        field(out, "collectorNumber", card.getCollectorNumber());
        field(out, "rarity", card.getRarity());
        field(out, "manaCost", card.getManaCost());
        if (card.getCmc() != null) field(
                out, "manaValue", card.getCmc().toString());
        field(out, "typeLine", card.effectiveTypeLine());
        field(out, "oracleText", card.effectiveOracleText());
        field(out, "power", card.getPower());
        field(out, "toughness", card.getToughness());
        out.append('\n');
    }

    private void field(StringBuilder out, String key, String value) {
        if (value == null || value.isBlank()) return;
        out.append("; ").append(key).append('=')
                .append(value.replace("\n", "\\n"));
    }

    private String loadTemplate() {
        try (InputStream input = DraftSetRankingExporter.class
                .getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(
                        "Missing draft ranking protocol " + TEMPLATE_RESOURCE);
            }
            return new String(
                    input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not load draft ranking protocol", error);
        }
    }
}
