package app.projection;

import app.model.card.CardInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Conservative labels inferred from a source card's Oracle text.
 * <p><strong>Architectural role:</strong> This type belongs to the projection boundary between ordered Arena observations, canonical game state, and immutable semantic events.</p>
 */
public final class AbilityHeuristics {
    private AbilityHeuristics() {}

    public static String infer(CardInfo source, String kind) {
        if (source == null || source.getOracleText() == null) return "";
        List<String> candidates = new ArrayList<>();
        for (String paragraph : source.getOracleText().split("\\n")) {
            String text = paragraph.strip();
            String lower = text.toLowerCase(Locale.ROOT);
            boolean activated = text.contains(":");
            boolean triggered = lower.startsWith("when ") || lower.startsWith("whenever ") || lower.startsWith("at ");
            if ("activated".equals(kind) && activated) candidates.add(text);
            else if ("triggered".equals(kind) && triggered) candidates.add(text);
            else if ("unknown".equals(kind) && (activated || triggered)) candidates.add(text);
        }
        if (candidates.size() != 1) return "";
        String value = candidates.get(0).replaceAll("\\s+", " ");
        return value.length() <= 72 ? value : value.substring(0, 69) + "…";
    }
}
