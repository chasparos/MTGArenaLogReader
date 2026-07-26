package app.draft.analysis;

import app.draft.model.DraftCardCount;
import app.model.card.CardInfo;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DraftDeckAnalysis {
    private static final Pattern MANA_SYMBOL = Pattern.compile("\\{([^}]+)}");
    private static final List<Pattern> REMOVAL_PATTERNS = List.of(
            Pattern.compile("\\bdestroy (?:target|each|all|up to one target) "
                    + "(?:artifact|battle|creature|enchantment|land|"
                    + "nonland permanent|permanent|planeswalker)\\b"),
            Pattern.compile("\\bexile (?:target|each|all|up to one target) "
                    + "(?:artifact|battle|creature|enchantment|"
                    + "nonland permanent|permanent|planeswalker)\\b"),
            Pattern.compile("\\bdeal(?:s)?\\s+[^.\\n]{0,45}\\s+damage to "
                    + "(?:any target|target (?:creature|planeswalker)|"
                    + "each creature|all creatures)\\b"),
            Pattern.compile("\\btarget creature gets -[^.\\n]{0,18}/-"),
            Pattern.compile("\\benchanted creature gets -[^.\\n]{0,18}/-"),
            Pattern.compile("\\breturn target (?:creature|nonland permanent|"
                    + "permanent) to (?:its|their) owner's hand\\b"),
            Pattern.compile("\\b(?:target creature you control )?"
                    + "fights? target creature\\b"),
            Pattern.compile("\\b(?:target player|each opponent) sacrifices? "
                    + "(?:a|one|that many) (?:creature|permanent)\\b"),
            Pattern.compile("\\b(?:destroy|exile) all creatures\\b"));

    public Summary analyze(
            List<DraftCardCount> counts,
            Map<Long, CardInfo> cards) {
        int total = 0;
        int creatures = 0;
        int removal = 0;
        Map<String, Integer> pips = new LinkedHashMap<>();
        for (String color : List.of("W", "U", "B", "R", "G")) pips.put(color, 0);
        Map<Integer, Integer> curve = new LinkedHashMap<>();
        for (int value = 0; value <= 7; value++) curve.put(value, 0);

        for (DraftCardCount count : counts == null ? List.<DraftCardCount>of() : counts) {
            CardInfo card = cards.get(count.arenaId());
            int quantity = count.quantity();
            total += quantity;
            if (isType(card, "Creature")) creatures += quantity;
            if (isRemoval(card)) removal += quantity;
            addPips(pips, card, quantity);
            if (!isType(card, "Land")) {
                int manaValue = card == null || card.getCmc() == null
                        ? 0 : Math.max(0, card.getCmc().intValue());
                curve.merge(Math.min(7, manaValue), quantity, Integer::sum);
            }
        }
        return new Summary(total, creatures, removal, pips, curve);
    }

    private boolean isType(CardInfo card, String type) {
        String line = card == null ? null : card.effectiveTypeLine();
        return line != null && line.contains(type);
    }

    private boolean isRemoval(CardInfo card) {
        if (card == null) return false;
        String rules = card.effectiveOracleText();
        if (rules == null) return false;
        String lower = rules.toLowerCase(Locale.ROOT);
        return REMOVAL_PATTERNS.stream()
                .anyMatch(pattern -> pattern.matcher(lower).find());
    }

    private void addPips(Map<String, Integer> pips, CardInfo card, int quantity) {
        if (card == null || card.getManaCost() == null) return;
        Matcher matcher = MANA_SYMBOL.matcher(card.getManaCost().toUpperCase(Locale.ROOT));
        while (matcher.find()) {
            String symbol = matcher.group(1);
            for (String color : pips.keySet()) {
                if (symbol.contains(color)) {
                    pips.merge(color, quantity, Integer::sum);
                }
            }
        }
    }

    public record Summary(
            int totalCards,
            int creatures,
            int removal,
            Map<String, Integer> colorPips,
            Map<Integer, Integer> manaCurve) {
        public Summary {
            colorPips = Map.copyOf(colorPips);
            manaCurve = Map.copyOf(manaCurve);
        }
    }
}
