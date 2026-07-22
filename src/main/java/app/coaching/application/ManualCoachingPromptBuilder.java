package app.coaching.application;

import app.replay.GameView;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds self-contained copy/paste prompts from the persisted AI reconstruction.
 *
 * <p>The builder deliberately slices the canonical match export rather than
 * re-exporting replay models. This preserves the stable event, card and object
 * references used by the response renderer.</p>
 */
public final class ManualCoachingPromptBuilder {
    static final String DEFAULT_TEMPLATE_RESOURCE = "/coach/protocols/coach_request.txt";

    private static final String QUESTION_PROPERTY = "${question}";
    private static final String CONTEXT_PROPERTY = "${context}";
    private static final Pattern PROPERTY = Pattern.compile("\\$\\{(question|context)}");
    private static final Pattern GAME = Pattern.compile("^G(\\d+)(?:\\s.*)?$");
    private static final Pattern TURN = Pattern.compile("^T(\\d+)(?:\\s.*)?$");

    private final String template;

    public ManualCoachingPromptBuilder() {
        this(loadTemplate(DEFAULT_TEMPLATE_RESOURCE));
    }

    ManualCoachingPromptBuilder(String template) {
        this.template = validateTemplate(template);
    }

    public String build(
            String reconstruction,
            GameView.CoachingScope scope,
            Integer gameNumber,
            Set<Integer> turns,
            String question) {
        Objects.requireNonNull(reconstruction, "reconstruction");
        Objects.requireNonNull(scope, "scope");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("question is empty");
        }

        String context = switch (scope) {
            case MATCH -> reconstruction.strip();
            case GAME -> sliceGame(reconstruction, requiredGame(gameNumber));
            case TURN, SELECTED_TURNS -> sliceTurns(
                    reconstruction,
                    requiredGame(gameNumber),
                    requireTurns(turns));
        };

        return applyTemplate(question.strip(), context).strip();
    }



    private String applyTemplate(String question, String context) {
        Matcher properties = PROPERTY.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (properties.find()) {
            String value = switch (properties.group(1)) {
                case "question" -> question;
                case "context" -> context;
                default -> throw new IllegalStateException("Unsupported coaching property");
            };
            properties.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        properties.appendTail(rendered);
        return rendered.toString();
    }

    private static String loadTemplate(String resourcePath) {
        try (InputStream input = ManualCoachingPromptBuilder.class.getResourceAsStream(resourcePath)) {
            if (input == null) {
                throw new IllegalStateException("Coaching protocol resource is missing: " + resourcePath);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException error) {
            throw new IllegalStateException("Could not read coaching protocol resource: " + resourcePath, error);
        }
    }

    private static String validateTemplate(String template) {
        Objects.requireNonNull(template, "template");
        if (!template.contains(QUESTION_PROPERTY)) {
            throw new IllegalArgumentException("Coaching protocol is missing " + QUESTION_PROPERTY);
        }
        if (!template.contains(CONTEXT_PROPERTY)) {
            throw new IllegalArgumentException("Coaching protocol is missing " + CONTEXT_PROPERTY);
        }
        return template;
    }

    private String sliceGame(String reconstruction, int wantedGame) {
        Parsed parsed = parse(reconstruction);
        List<String> game = parsed.games().get(wantedGame);
        if (game == null) throw new IllegalArgumentException("Game " + wantedGame + " is unavailable");
        return join(parsed.header(), game);
    }

    private String sliceTurns(String reconstruction, int wantedGame, Set<Integer> wantedTurns) {
        Parsed parsed = parse(reconstruction);
        List<String> game = parsed.games().get(wantedGame);
        if (game == null) throw new IllegalArgumentException("Game " + wantedGame + " is unavailable");

        List<String> selected = new ArrayList<>();
        int index = 0;
        while (index < game.size() && !TURN.matcher(game.get(index)).matches()) {
            selected.add(game.get(index++)); // G/H and any game preamble.
        }

        boolean found = false;
        while (index < game.size()) {
            Matcher turn = TURN.matcher(game.get(index));
            if (!turn.matches()) {
                index++;
                continue;
            }
            int number = Integer.parseInt(turn.group(1));
            int end = index + 1;
            while (end < game.size() && !TURN.matcher(game.get(end)).matches()) end++;
            if (wantedTurns.contains(number)) {
                selected.addAll(game.subList(index, end));
                found = true;
            }
            index = end;
        }
        if (!found) throw new IllegalArgumentException("Selected turns are unavailable");
        return join(parsed.header(), selected);
    }

    private Parsed parse(String reconstruction) {
        List<String> header = new ArrayList<>();
        java.util.Map<Integer, List<String>> games = new java.util.LinkedHashMap<>();
        List<String> current = null;
        for (String line : reconstruction.split("\\R")) {
            Matcher game = GAME.matcher(line);
            if (game.matches()) {
                current = new ArrayList<>();
                games.put(Integer.parseInt(game.group(1)), current);
            }
            if (current == null) header.add(line);
            else current.add(line);
        }
        return new Parsed(List.copyOf(header), games);
    }

    private String join(List<String> header, List<String> body) {
        List<String> lines = new ArrayList<>(header.size() + body.size());
        lines.addAll(header);
        lines.addAll(body);
        return String.join(System.lineSeparator(), lines).strip();
    }

    private int requiredGame(Integer gameNumber) {
        if (gameNumber == null || gameNumber < 1) {
            throw new IllegalArgumentException("game context is missing");
        }
        return gameNumber;
    }

    private Set<Integer> requireTurns(Set<Integer> turns) {
        if (turns == null || turns.isEmpty()) {
            throw new IllegalArgumentException("turn context is missing");
        }
        return new LinkedHashSet<>(turns);
    }

    private record Parsed(
            List<String> header,
            java.util.Map<Integer, List<String>> games) {
    }
}
