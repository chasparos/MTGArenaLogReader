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
    private static final Pattern EQUIP = Pattern.compile(
            "^Equip\\s+((?:\\{[^}]+})+)", Pattern.CASE_INSENSITIVE);

    public Badge parse(CardInfo card) {
        if (card == null) return null;
        String oracle = card.effectiveOracleText();
        if (oracle == null || oracle.isBlank()) return null;

        for (String line : oracle.split("\\R")) {
            Matcher equip = EQUIP.matcher(line.strip());
            if (equip.find()) {
                return new Badge(equip.group(1), "Eq", false, List.of());
            }

            int colon = abilityColon(line);
            if (colon <= 0) continue;
            String cost = stripReminderText(line.substring(0, colon)).strip();
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

    private int abilityColon(String line) {
        int parentheses = 0;
        for (int index = 0; index < line.length(); index++) {
            char character = line.charAt(index);
            if (character == '(') parentheses++;
            else if (character == ')') parentheses = Math.max(0, parentheses - 1);
            else if (character == ':' && parentheses == 0) return index;
        }
        return -1;
    }

    private String stripReminderText(String value) {
        return value.replaceAll("\\([^)]*\\)", "");
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
        String withoutMana = MANA.matcher(
                stripReminderText(cost == null ? "" : cost)).replaceAll("");
        List<String> actions = new java.util.ArrayList<>();
        for (String raw : withoutMana.split(",")) {
            String clause = raw.strip();
            if (clause.isBlank()) continue;
            String lower = clause.toLowerCase(Locale.ROOT);
            String action;
            if (lower.equals("pay")) {
                continue;
            } else if (lower.equals("sac") || lower.startsWith("sacrifice")) {
                action = "Sac";
            } else if (lower.startsWith("discard")) {
                action = "Dis";
            } else if (lower.startsWith("remove")
                    && lower.contains("counter")) {
                action = "−ctr";
            } else if (lower.startsWith("exile")) {
                action = "Ex";
            } else if (lower.startsWith("pay") && lower.contains("life")) {
                Matcher number = Pattern.compile("\\d+").matcher(lower);
                action = number.find() ? "−" + number.group() + "♥" : "−♥";
            } else {
                action = clause.replaceAll("[^A-Za-z0-9+−]", "");
                if (action.length() > 5) action = action.substring(0, 4) + "…";
            }
            if (!action.isBlank()) actions.add(action);
            if (actions.size() == 3) break;
        }
        return String.join("·", actions);
    }

    public record Badge(String manaCost, String textCost, boolean tap,
                        List<String> manaOptions) {
        public Badge {
            manaOptions = List.copyOf(manaOptions);
        }
    }
}
