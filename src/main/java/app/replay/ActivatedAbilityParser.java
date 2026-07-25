package app.replay;

import app.model.card.CardInfo;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts the first displayable activated ability in Oracle text into the
 * compact data rendered by the replay's permanent chip.
 */
public final class ActivatedAbilityParser {
    private static final Pattern MANA = Pattern.compile("\\{([^}]+)}");

    public Badge parse(CardInfo card) {
        if (card == null) return null;
        String oracle = card.effectiveOracleText();
        if (oracle == null || oracle.isBlank()) return null;

        for (String line : oracle.split("\\R")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String cost = line.substring(0, colon).strip();
            if (cost.isBlank() || cost.startsWith("When ")
                    || cost.startsWith("Whenever ") || cost.startsWith("At ")) {
                continue;
            }

            boolean tap = cost.contains("{T}");
            String effect = line.substring(colon + 1);
            boolean manaAbility = tap
                    && effect.toLowerCase(Locale.ROOT).matches(".*\\badd\\b.*");
            return new Badge(
                    manaSymbols(cost),
                    compactNonManaCost(cost),
                    tap,
                    manaAbility ? producedManaOptions(effect) : List.of());
        }
        return null;
    }

    private String manaSymbols(String text) {
        Matcher matcher = MANA.matcher(text == null ? "" : text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String symbol = matcher.group(1).toUpperCase(Locale.ROOT);
            if (!"T".equals(symbol) && !"Q".equals(symbol)) {
                result.append('{').append(symbol).append('}');
            }
        }
        return result.toString();
    }

    private List<String> producedManaOptions(String effect) {
        Matcher matcher = MANA.matcher(effect == null ? "" : effect);
        LinkedHashSet<String> result = new LinkedHashSet<>();
        while (matcher.find()) {
            String symbol = matcher.group(1).toUpperCase(Locale.ROOT);
            if (symbol.matches("[WUBRGC]")) result.add(symbol);
        }
        return List.copyOf(result);
    }

    private String compactNonManaCost(String cost) {
        String compact = MANA.matcher(cost == null ? "" : cost).replaceAll("")
                .replace(",", "")
                .replace("(", "")
                .replace(")", "")
                .replace(":", "")
                .replaceAll("\\s+", "")
                .strip();
        return compact.length() <= 5 ? compact : "";
    }

    public record Badge(String manaCost, String textCost, boolean tap,
                        List<String> manaOptions) {
        public Badge {
            manaOptions = List.copyOf(manaOptions);
        }
    }
}
