package app.coaching.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Persisted human context in which a coaching question was asked.
 */
public record CoachingContext(
        Scope scope,
        Integer gameNumber,
        SortedSet<Integer> turns) {

    public CoachingContext {
        if (scope == null) throw new IllegalArgumentException("scope is required");

        TreeSet<Integer> normalizedTurns = new TreeSet<>();
        if (turns != null) {
            for (Integer turn : turns) {
                if (turn == null || turn <= 0) {
                    throw new IllegalArgumentException("turn numbers must be positive");
                }
                normalizedTurns.add(turn);
            }
        }
        turns = Collections.unmodifiableSortedSet(normalizedTurns);

        switch (scope) {
            case MATCH -> {
                if (gameNumber != null || !turns.isEmpty()) {
                    throw new IllegalArgumentException("match context cannot contain a game or turns");
                }
            }
            case GAME -> {
                requireGame(gameNumber);
                if (!turns.isEmpty()) {
                    throw new IllegalArgumentException("game context cannot contain turns");
                }
            }
            case TURN -> {
                requireGame(gameNumber);
                if (turns.size() != 1) {
                    throw new IllegalArgumentException("turn context requires exactly one turn");
                }
            }
            case SELECTED_TURNS -> {
                requireGame(gameNumber);
                if (turns.isEmpty()) {
                    throw new IllegalArgumentException("selected-turn context requires at least one turn");
                }
            }
        }
    }

    public String persistedTurns() {
        return turns.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    public String humanLabel() {
        return switch (scope) {
            case MATCH -> "Match";
            case GAME -> "Game " + gameNumber;
            case TURN -> "Game " + gameNumber + " · Turn " + turns.first();
            case SELECTED_TURNS -> "Game " + gameNumber + " · Turns "
                    + turns.stream().map(String::valueOf).collect(Collectors.joining(", "));
        };
    }

    public static SortedSet<Integer> parseTurns(String persisted) {
        if (persisted == null || persisted.isBlank()) return Collections.emptySortedSet();

        TreeSet<Integer> turns = Arrays.stream(persisted.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(Integer::parseInt)
                .collect(Collectors.toCollection(TreeSet::new));
        return Collections.unmodifiableSortedSet(turns);
    }

    private static void requireGame(Integer gameNumber) {
        if (gameNumber == null || gameNumber <= 0) {
            throw new IllegalArgumentException("game number must be positive");
        }
    }

    public enum Scope {
        MATCH,
        GAME,
        TURN,
        SELECTED_TURNS
    }
}
