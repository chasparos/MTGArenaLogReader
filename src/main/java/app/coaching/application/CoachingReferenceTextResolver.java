package app.coaching.application;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves coaching protocol card references into readable conversation text.
 *
 * <p>The persisted assistant response remains unchanged. Resolution is a
 * presentation-time translation based on the CARD dictionary in the persisted
 * reconstruction.</p>
 */
public final class CoachingReferenceTextResolver {
    private static final Pattern CARD_DICTIONARY =
            Pattern.compile("^CARD\\s+(c\\d+)=(.+?)(?:@\\d+)?$", Pattern.MULTILINE);
    private static final Pattern CARD_REFERENCE =
            Pattern.compile("\\[(c\\d+)(?:#(\\d+))?]");

    public String resolve(String reconstruction, String response) {
        Objects.requireNonNull(reconstruction, "reconstruction");
        Objects.requireNonNull(response, "response");

        Map<String, String> cardNames = cardNames(reconstruction);
        Matcher references = CARD_REFERENCE.matcher(response);
        StringBuffer resolved = new StringBuffer();
        while (references.find()) {
            String alias = references.group(1);
            String name = cardNames.get(alias);
            if (name == null) {
                references.appendReplacement(resolved, Matcher.quoteReplacement(references.group()));
                continue;
            }

            String objectId = references.group(2);
            String display = objectId == null
                    ? name + " [" + alias + "]"
                    : name + " [" + alias + "#" + objectId + "]";
            references.appendReplacement(resolved, Matcher.quoteReplacement(display));
        }
        references.appendTail(resolved);
        return resolved.toString();
    }

    private Map<String, String> cardNames(String reconstruction) {
        Map<String, String> names = new LinkedHashMap<>();
        Matcher dictionary = CARD_DICTIONARY.matcher(reconstruction);
        while (dictionary.find()) {
            names.put(dictionary.group(1), dictionary.group(2).trim());
        }
        return names;
    }
}
