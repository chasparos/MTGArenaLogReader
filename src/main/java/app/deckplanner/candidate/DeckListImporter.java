package app.deckplanner.candidate;

import app.deckplanner.filter.CatalogFilterIndex;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves Arena-exported deck text into stable candidates identities.
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
                         int parsedCardLines, int fallbackCards) {
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
        return resolve(deckText, CardNameRepository.local(index));
    }

    public static Result resolve(String deckText, CardNameRepository cards) {
        Objects.requireNonNull(cards);
        LinkedHashSet<String> resolved = new LinkedHashSet<>();
        LinkedHashSet<String> unresolved = new LinkedHashSet<>();
        int parsed = 0;
        int fallback = 0;

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
                var resolution = cards.resolve(spec.name(), spec.setCode(), spec.collectorNumber());
                if (resolution.isEmpty()) {
                    unresolved.add(spec.name());
                } else {
                    resolved.add(resolution.get().identity());
                    if (resolution.get().fallback()) fallback++;
                }
            }
        }
        return new Result(List.copyOf(resolved), List.copyOf(unresolved), parsed, fallback);
    }

    private static CardSpec parseSpec(String value) {
        Matcher suffix = ARENA_SUFFIX.matcher(value);
        if (suffix.matches()) {
            return new CardSpec(suffix.group(1).strip(), suffix.group(2).strip(),
                    suffix.group(3).strip());
        }
        return new CardSpec(value.strip(), null, null);
    }
}
