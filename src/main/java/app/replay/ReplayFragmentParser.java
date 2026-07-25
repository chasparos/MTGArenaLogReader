package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts event text and referenced cards into semantic fragments for replay
 * layout. Swing measurement and painting intentionally remain in {@link GameView}.
 */
final class ReplayFragmentParser {
    private static final Pattern MANA = Pattern.compile("\\{([^}]+)}");
    private static final Pattern POWER_TOUGHNESS = Pattern.compile(
            "\\(?(-?\\d+|\\*)/(-?\\d+|\\*)\\)?");
    private static final Pattern WORDS = Pattern.compile("\\s+|\\S+");
    private static final List<String> KEYWORDS = List.of(
            "deathtouch", "defender", "double strike", "first strike", "flying",
            "haste", "hexproof", "indestructible", "lifelink", "menace",
            "reach", "trample", "vigilance", "ward");

    List<ReplayFragment> parse(GameEvent event) {
        String text = event.getText() == null ? "" : event.getText();
        List<CardMatchCandidate> cards = event.getCards().stream()
                .filter(Objects::nonNull)
                .filter(card -> card.getName() != null && !card.getName().isBlank())
                .flatMap(card -> candidates(card).stream())
                .sorted(Comparator.comparingInt(
                        (CardMatchCandidate candidate) -> candidate.label().length()).reversed())
                .toList();

        List<ReplayFragment> result = new ArrayList<>();
        int position = 0;
        while (position < text.length()) {
            CardMatch next = nextMatch(text, position, cards);
            if (next == null) {
                appendTextAndMana(result, text.substring(position));
                break;
            }
            if (next.start() > position) {
                appendTextAndMana(result, text.substring(position, next.start()));
            }
            result.add(new CardFragment(
                    next.card(),
                    next.label(),
                    roomStateLabel(event, next.card(), next.label()),
                    null));
            position = next.end();
        }
        if (text.isEmpty()) result.add(new TextFragment(""));
        return List.copyOf(result);
    }

    private List<CardMatchCandidate> candidates(CardInfo card) {
        List<CardMatchCandidate> result = new ArrayList<>();
        result.add(new CardMatchCandidate(card, card.getName()));
        for (String face : card.getName().split("\\s+//\\s+")) {
            if (!face.isBlank() && !face.equals(card.getName())) {
                result.add(new CardMatchCandidate(card, face));
            }
        }
        return result;
    }

    private CardMatch nextMatch(String text, int from,
                                List<CardMatchCandidate> cards) {
        CardMatch best = null;
        for (CardMatchCandidate candidate : cards) {
            int at = text.indexOf(candidate.label(), from);
            if (at < 0) continue;
            CardMatch match = new CardMatch(
                    at,
                    at + candidate.label().length(),
                    candidate.card(),
                    candidate.label());
            if (best == null || match.start() < best.start()
                    || (match.start() == best.start() && match.end() > best.end())) {
                best = match;
            }
        }
        return best;
    }

    private String roomStateLabel(GameEvent event, CardInfo card,
                                  String renderedLabel) {
        for (BoardPermanentSnapshot permanent : event.getBattlefieldObservation()) {
            if (permanent.getCard() == null
                    || !Objects.equals(
                    permanent.getCard().getArenaId(), card.getArenaId())
                    || permanent.getUnlockedRoomHalves().isEmpty()) {
                continue;
            }
            return "unlocked: " + String.join(
                    ", ", permanent.getUnlockedRoomHalves());
        }
        if (card.getName() != null && card.getName().contains(" // ")
                && !renderedLabel.equals(card.getName())
                && event.getText() != null
                && event.getText().contains("casts " + renderedLabel)) {
            return "unlock";
        }
        return "";
    }

    private void appendTextAndMana(List<ReplayFragment> output, String text) {
        Matcher matcher = MANA.matcher(text);
        int position = 0;
        while (matcher.find()) {
            appendDecoratedWords(output, text.substring(position, matcher.start()));
            output.add(new ManaFragment(matcher.group(1)));
            position = matcher.end();
        }
        appendDecoratedWords(output, text.substring(position));
    }

    private void appendDecoratedWords(List<ReplayFragment> output, String text) {
        int position = 0;
        while (position < text.length()) {
            DecoratedToken token = nextDecoratedToken(text, position);
            if (token == null) {
                appendPlainWords(output, text.substring(position));
                return;
            }
            if (token.start() > position) {
                appendPlainWords(output, text.substring(position, token.start()));
            }
            output.add(token.fragment());
            position = token.end();
        }
    }

    private DecoratedToken nextDecoratedToken(String text, int from) {
        DecoratedToken best = null;
        Matcher powerToughness = POWER_TOUGHNESS.matcher(text);
        if (powerToughness.find(from)) {
            best = new DecoratedToken(
                    powerToughness.start(),
                    powerToughness.end(),
                    new PowerToughnessFragment(
                            powerToughness.group(1), powerToughness.group(2)));
        }

        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : KEYWORDS) {
            int at = lower.indexOf(keyword, from);
            if (at < 0 || !wordBoundary(
                    lower, at, at + keyword.length())) {
                continue;
            }
            DecoratedToken candidate = new DecoratedToken(
                    at,
                    at + keyword.length(),
                    new KeywordFragment(keyword, displayKeyword(keyword)));
            if (best == null || candidate.start() < best.start()) best = candidate;
        }
        return best;
    }

    private void appendPlainWords(List<ReplayFragment> output, String text) {
        Matcher matcher = WORDS.matcher(text);
        while (matcher.find()) output.add(new TextFragment(matcher.group()));
    }

    private boolean wordBoundary(String text, int start, int end) {
        boolean left = start == 0
                || !Character.isLetterOrDigit(text.charAt(start - 1));
        boolean right = end >= text.length()
                || !Character.isLetterOrDigit(text.charAt(end));
        return left && right;
    }

    private String displayKeyword(String keyword) {
        return Arrays.stream(keyword.split(" "))
                .map(part -> Character.toUpperCase(part.charAt(0))
                        + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private record DecoratedToken(
            int start, int end, ReplayFragment fragment) {
    }

    private record CardMatch(
            int start, int end, CardInfo card, String label) {
    }

    private record CardMatchCandidate(CardInfo card, String label) {
    }
}

sealed interface ReplayFragment permits TextFragment, CardFragment, ManaFragment,
        PowerToughnessFragment, KeywordFragment {
}

record TextFragment(String text) implements ReplayFragment {
}

record CardFragment(
        CardInfo card,
        String label,
        String stateLabel,
        BoardPermanentSnapshot permanent) implements ReplayFragment {
}

record ManaFragment(String symbol) implements ReplayFragment {
}

record PowerToughnessFragment(
        String power, String toughness) implements ReplayFragment {
}

record KeywordFragment(
        String keyword, String label) implements ReplayFragment {
}
