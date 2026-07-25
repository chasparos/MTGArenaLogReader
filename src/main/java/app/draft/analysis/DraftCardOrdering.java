package app.draft.analysis;

import app.draft.model.DraftCardCount;
import app.model.card.CardInfo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class DraftCardOrdering {
    public List<DraftCardCount> sort(
            List<DraftCardCount> counts,
            Map<Long, CardInfo> cards) {
        List<DraftCardCount> result = new ArrayList<>(
                counts == null ? List.of() : counts);
        result.sort(Comparator
                .comparingInt((DraftCardCount count) ->
                        typeOrder(cards.get(count.arenaId())))
                .thenComparingDouble(count ->
                        manaValue(cards.get(count.arenaId())))
                .thenComparingInt(count ->
                        colorOrder(cards.get(count.arenaId())))
                .thenComparing(count -> cardName(
                        cards.get(count.arenaId())),
                        String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    int typeOrder(CardInfo card) {
        String type = card == null ? "" : nullToEmpty(card.effectiveTypeLine());
        if (type.contains("Creature")) return 0;
        if (type.contains("Planeswalker")) return 1;
        if (type.contains("Instant")) return 2;
        if (type.contains("Sorcery")) return 3;
        if (type.contains("Artifact")) return 4;
        if (type.contains("Enchantment")) return 5;
        if (type.contains("Battle")) return 6;
        if (type.contains("Land")) return 8;
        return 7;
    }

    public String typeGroup(CardInfo card) {
        return switch (typeOrder(card)) {
            case 0 -> "Creatures";
            case 1 -> "Planeswalkers";
            case 2 -> "Instants";
            case 3 -> "Sorceries";
            case 4 -> "Artifacts";
            case 5 -> "Enchantments";
            case 6 -> "Battles";
            case 8 -> "Lands";
            default -> "Other";
        };
    }

    int colorOrder(CardInfo card) {
        if (card == null) return 7;
        List<String> colors = card.getColors();
        if (colors == null || colors.isEmpty()) colors = card.getColorIdentity();
        if (colors == null || colors.isEmpty()) return 6;
        Set<String> distinct = new HashSet<>(colors);
        if (distinct.size() > 1) return 5;
        return switch (distinct.iterator().next()) {
            case "W" -> 0;
            case "U" -> 1;
            case "B" -> 2;
            case "R" -> 3;
            case "G" -> 4;
            default -> 6;
        };
    }

    private double manaValue(CardInfo card) {
        return card == null || card.getCmc() == null
                ? Double.MAX_VALUE
                : card.getCmc();
    }

    private String cardName(CardInfo card) {
        return card == null || card.getName() == null ? "" : card.getName();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
