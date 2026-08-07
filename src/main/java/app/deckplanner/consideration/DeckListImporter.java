package app.deckplanner.consideration;

import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.IndexedCatalogCard;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Arena-exported deck text into stable consideration identities.
 *
 * <p>Deck quantities and section membership are intentionally discarded because DP-06 stores
 * candidate membership, not a playable deck definition. Deck contents never imply ownership.</p>
 */
public final class DeckListImporter {
    private static final Pattern CARD_LINE = Pattern.compile("^\\s*(\\d+)\\s+(.+?)\\s*$");
    private static final Pattern ARENA_SUFFIX =
            Pattern.compile("^(.*?)\\s+\\(([^)]+)\\)\\s+([^\\s]+)\\s*$");
    private static final Set<String> SECTION_HEADERS = Set.of(
            "deck", "sideboard", "commander", "companion");

    public record Result(List<String> identities, List<String> unresolvedNames,
                         int parsedCardLines) {
        public Result {
            identities = List.copyOf(identities);
            unresolvedNames = List.copyOf(unresolvedNames);
        }

        public int resolvedCards() {
            return identities.size();
        }
    }

    private record CardSpec(String name, String setCode, String collectorNumber) { }

    private DeckListImporter() { }

    public static Result resolve(String deckText, CatalogFilterIndex index) {
        Objects.requireNonNull(index);
        Resolver resolver = Resolver.from(index);
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        int parsed = 0;

        if (deckText != null) {
            for (String rawLine : deckText.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
                String line = rawLine.strip();
                if (line.isEmpty() || SECTION_HEADERS.contains(line.toLowerCase(Locale.ROOT))) continue;

                Matcher cardLine = CARD_LINE.matcher(line);
                if (!cardLine.matches()) continue;
                int quantity;
                try {
                    quantity = Integer.parseInt(cardLine.group(1));
                } catch (NumberFormatException ignored) {
                    continue;
                }
                if (quantity <= 0) continue;
                parsed++;

                CardSpec spec = parseSpec(cardLine.group(2));
                String identity = resolver.resolve(spec);
                if (identity == null) unresolved.add(spec.name());
                else resolved.add(identity);
            }
        }
        return new Result(List.copyOf(resolved), List.copyOf(unresolved), parsed);
    }

    private static CardSpec parseSpec(String value) {
        Matcher suffix = ARENA_SUFFIX.matcher(value);
        if (suffix.matches()) {
            return new CardSpec(suffix.group(1).strip(), suffix.group(2).strip(),
                    suffix.group(3).strip());
        }
        return new CardSpec(value.strip(), null, null);
    }

    private static String normalizeName(String value) {
        return value.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private static String printingKey(String name, String setCode, String collectorNumber) {
        return normalizeName(name) + "|" + setCode.strip().toLowerCase(Locale.ROOT)
                + "|" + collectorNumber.strip().toLowerCase(Locale.ROOT);
    }

    private static final class Resolver {
        private final Map<String, String> identityByPrinting;
        private final Map<String, LinkedHashSet<String>> identitiesByName;

        private Resolver(Map<String, String> identityByPrinting,
                         Map<String, LinkedHashSet<String>> identitiesByName) {
            this.identityByPrinting = identityByPrinting;
            this.identitiesByName = identitiesByName;
        }

        static Resolver from(CatalogFilterIndex index) {
            LinkedHashMap<String, String> byPrinting = new LinkedHashMap<>();
            LinkedHashMap<String, LinkedHashSet<String>> byName = new LinkedHashMap<>();
            for (IndexedCatalogCard indexed : index.cards()) {
                String identity = indexed.group().identity();
                for (CardInfo printing : indexed.group().printings()) {
                    indexCard(byPrinting, byName, printing, identity);
                }
                indexCard(byPrinting, byName, indexed.group().preferredPrinting(), identity);
            }
            return new Resolver(byPrinting, byName);
        }

        String resolve(CardSpec spec) {
            if (spec.setCode() != null && spec.collectorNumber() != null) {
                String exact = identityByPrinting.get(
                        printingKey(spec.name(), spec.setCode(), spec.collectorNumber()));
                if (exact != null) return exact;
            }
            LinkedHashSet<String> matches = identitiesByName.get(normalizeName(spec.name()));
            return matches != null && matches.size() == 1 ? matches.getFirst() : null;
        }

        private static void indexCard(Map<String, String> byPrinting,
                                      Map<String, LinkedHashSet<String>> byName,
                                      CardInfo card, String identity) {
            if (card == null) return;
            addName(byName, card.getName(), identity);
            if (hasText(card.getName()) && hasText(card.getSet()) && hasText(card.getCollectorNumber())) {
                byPrinting.putIfAbsent(
                        printingKey(card.getName(), card.getSet(), card.getCollectorNumber()), identity);
            }
            if (card.getCardFaces() != null) {
                for (CardFaceInfo face : card.getCardFaces()) {
                    if (face != null) addName(byName, face.getName(), identity);
                }
            }
        }

        private static void addName(Map<String, LinkedHashSet<String>> result,
                                    String name, String identity) {
            if (!hasText(name)) return;
            result.computeIfAbsent(normalizeName(name), ignored -> new LinkedHashSet<>()).add(identity);
            int separator = name.indexOf(" // ");
            if (separator > 0) {
                String frontName = name.substring(0, separator);
                result.computeIfAbsent(normalizeName(frontName), ignored -> new LinkedHashSet<>())
                        .add(identity);
            }
        }

        private static boolean hasText(String value) {
            return value != null && !value.isBlank();
        }
    }
}
