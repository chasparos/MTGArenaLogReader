package app.deckplanner.filter;

import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/** Deterministic semantic tag rules for Deck Planner tag schema version 1. */
public final class CardTagRules {
    public static final int VERSION = 1;
    private static final Pattern MILL = Pattern.compile("\\bmill(?:s|ed|ing)?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SACRIFICE = Pattern.compile("\\bsacrific(?:e|es|ed|ing)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TARGET = Pattern.compile("\\btargets?\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern ALL_CREATURES = Pattern.compile("\\b(?:all|each) creatures?\\b", Pattern.CASE_INSENSITIVE);

    public Set<SemanticTag> tags(CardInfo card) {
        LinkedHashSet<SemanticTag> result = new LinkedHashSet<>();
        if (card == null) return Set.of();
        if (card.getKeywords() != null) card.getKeywords().stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> new SemanticTag(TagCategory.KEYWORD, normalize(value), value.strip()))
                .sorted().forEach(result::add);
        String text = combinedOracleText(card);
        addIf(result, MILL.matcher(text).find(), TagCategory.ACTION, "mill", "Mill");
        addIf(result, SACRIFICE.matcher(text).find(), TagCategory.ACTION, "sacrifice", "Sacrifice");
        addIf(result, TARGET.matcher(text).find(), TagCategory.ACTION, "target", "Target");
        addIf(result, ALL_CREATURES.matcher(text).find(), TagCategory.CONCEPT, "all-creatures", "All creatures");
        addZone(result, text, "graveyard");
        addZone(result, text, "exile");
        addZone(result, text, "library");
        addZone(result, text, "hand");
        addZone(result, text, "battlefield");
        return Collections.unmodifiableSet(new LinkedHashSet<>(result));
    }

    private void addZone(Set<SemanticTag> tags, String text, String zone) {
        addIf(tags, Pattern.compile("\\b" + zone + "\\b", Pattern.CASE_INSENSITIVE).matcher(text).find(),
                TagCategory.ZONE, zone, title(zone));
    }
    private static void addIf(Set<SemanticTag> tags, boolean include, TagCategory category, String key, String label) {
        if (include) tags.add(new SemanticTag(category, key, label));
    }
    private static String combinedOracleText(CardInfo card) {
        StringBuilder out = new StringBuilder();
        if (card.getOracleText() != null) out.append(card.getOracleText()).append('\n');
        if (card.getCardFaces() != null) for (CardFaceInfo face : card.getCardFaces()) {
            if (face != null && face.getOracleText() != null) out.append(face.getOracleText()).append('\n');
        }
        return out.toString();
    }
    private static String normalize(String value) {
        return value.strip().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }
    private static String title(String value) { return Character.toUpperCase(value.charAt(0)) + value.substring(1); }
}
