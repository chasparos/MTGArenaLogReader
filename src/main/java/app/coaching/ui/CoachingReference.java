package app.coaching.ui;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A stable reference emitted by the coaching protocol.
 */
public record CoachingReference(
        Kind kind,
        int number,
        Integer objectId,
        String token) {

    private static final Pattern NUMBERED =
            Pattern.compile("\\[(G|T|E|A|C|L|GR|S)#?(\\d+)]");
    private static final Pattern CARD =
            Pattern.compile("\\[c(\\d+)(?:#(\\d+))?]");

    public CoachingReference {
        if (kind == null) throw new IllegalArgumentException("kind is required");
        if (number < 0) throw new IllegalArgumentException("number cannot be negative");
        if (token == null || token.isBlank()) throw new IllegalArgumentException("token is required");
    }

    public static Optional<CoachingReference> parse(String token) {
        if ("[MATCH]".equals(token)) {
            return Optional.of(new CoachingReference(Kind.MATCH, 0, null, token));
        }

        Matcher card = CARD.matcher(token);
        if (card.matches()) {
            Integer objectId = card.group(2) == null ? null : Integer.valueOf(card.group(2));
            return Optional.of(new CoachingReference(
                    Kind.CARD,
                    Integer.parseInt(card.group(1)),
                    objectId,
                    token));
        }

        Matcher numbered = NUMBERED.matcher(token);
        if (!numbered.matches()) return Optional.empty();

        Kind kind = switch (numbered.group(1)) {
            case "G" -> Kind.GAME;
            case "T" -> Kind.TURN;
            case "E" -> Kind.EVENT;
            case "A" -> Kind.ABILITY;
            case "C" -> Kind.DECISION;
            case "L" -> Kind.LIFE;
            case "GR" -> Kind.RESULT;
            case "S" -> Kind.SNAPSHOT;
            default -> throw new IllegalStateException("Unsupported reference " + token);
        };
        return Optional.of(new CoachingReference(
                kind,
                Integer.parseInt(numbered.group(2)),
                null,
                token));
    }

    public enum Kind {
        MATCH,
        GAME,
        TURN,
        EVENT,
        ABILITY,
        DECISION,
        LIFE,
        RESULT,
        SNAPSHOT,
        CARD
    }
}
