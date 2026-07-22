package app.export;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.GameResult;
import app.model.game.PermanentDamage;
import app.model.game.PlayerLifeChange;
import app.model.game.PlayerTurnSnapshot;
import app.model.match.MatchResult;
import app.model.match.MatchScore;
import app.model.session.GameModel;
import app.model.session.MatchSession;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;

/**
 * Produces a compact, deterministic match report for language-model review.
 *
 * <p>The exporter consumes semantic reconstruction only. It deliberately omits
 * raw Arena records, presentation markup, timestamps, and repeated context.
 * Unknown information remains explicit instead of being guessed.</p>
 */
public final class MatchAiExporter {

    public String export(MatchSession match) {
        Objects.requireNonNull(match, "match");

        List<GameModel> games = new ArrayList<>(match.gameSnapshot());
        games.sort(Comparator.comparingInt(GameModel::getGameNumber));

        StringBuilder out = new StringBuilder(8192);
        out.append("MTGA_MATCH_V1\n");
        out.append("schema=G game;H opening;T turn;P phase;S state;E event;"
                + "L life;D pw-damage;GR result;MS score;MR match-result;? unknown\n");
        out.append("match=").append(value(match.matchState().getMatchId(), "?")).append('\n');
        appendPlayers(out, match.matchState().playerSnapshot());

        for (GameModel game : games) {
            appendGame(out, game);
        }
        return out.toString();
    }

    private void appendPlayers(StringBuilder out, Map<Integer, String> players) {
        if (players.isEmpty()) return;
        StringJoiner values = new StringJoiner("|");
        players.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.add(entry.getKey() + ":" + compact(entry.getValue())));
        out.append("players=").append(values).append('\n');
    }

    private void appendGame(StringBuilder out, GameModel game) {
        out.append("\nG").append(game.getGameNumber());
        if (game.isComplete()) out.append(" complete");
        out.append('\n');

        List<CardInfo> opening = game.openingHandSnapshot();
        if (!opening.isEmpty()) {
            out.append("H player=").append(compact(value(game.getOpeningHandPlayer(), "?")))
                    .append(" mull=").append(game.getMulliganCount())
                    .append(" cards=");
            appendCardNames(out, opening);
            out.append('\n');
        }

        Integer currentTurn = null;
        String currentPhase = null;
        String currentStep = null;
        for (GameEvent event : game.snapshot()) {
            if (event.getType() == app.model.event.GameEventType.MATCH_STARTED
                    || event.getType() == app.model.event.GameEventType.GAME_STARTED
                    || event.getType() == app.model.event.GameEventType.OPENING_HAND) {
                continue;
            }

            if (event.getTurnNumber() != null && !event.getTurnNumber().equals(currentTurn)) {
                currentTurn = event.getTurnNumber();
                currentPhase = null;
                currentStep = null;
                out.append("T").append(currentTurn);
                if (hasText(event.getActivePlayerName())) {
                    out.append(" active=").append(compact(event.getActivePlayerName()));
                }
                out.append('\n');
            }

            if (!event.getTurnSnapshot().isEmpty()) {
                appendTurnSnapshot(out, event.getTurnSnapshot());
                continue;
            }

            if (event.getGameResult() != null) {
                appendGameResult(out, event.getGameResult());
                continue;
            }
            if (event.getMatchScore() != null) {
                MatchScore score = event.getMatchScore();
                out.append("MS ").append(score.seatOneWins()).append('-')
                        .append(score.seatTwoWins());
                if (score.draws() > 0) out.append(" d=").append(score.draws());
                out.append('\n');
                continue;
            }
            if (event.getMatchResult() != null) {
                appendMatchResult(out, event.getMatchResult());
                continue;
            }
            if (event.getPlayerLifeChange() != null) {
                appendLifeChange(out, event.getPlayerLifeChange());
                continue;
            }
            if (event.getPermanentDamage() != null) {
                appendPermanentDamage(out, event.getPermanentDamage());
                continue;
            }

            String phase = compactPhase(event.getPhase());
            String step = compactPhase(event.getStep());
            if (!Objects.equals(phase, currentPhase) || !Objects.equals(step, currentStep)) {
                currentPhase = phase;
                currentStep = step;
                if (hasText(phase) || hasText(step)) {
                    out.append("P ");
                    if (hasText(phase)) out.append(phase);
                    if (hasText(step) && !Objects.equals(phase, step)) {
                        if (hasText(phase)) out.append('/');
                        out.append(step);
                    }
                    out.append('\n');
                }
            }
            if (hasText(event.getText())) {
                out.append("E ").append(compact(event.getText())).append('\n');
            }
        }
    }

    private void appendTurnSnapshot(StringBuilder out, List<PlayerTurnSnapshot> snapshots) {
        for (PlayerTurnSnapshot player : snapshots) {
            out.append("S ").append(compact(value(player.getPlayerName(),
                            "seat" + player.getSeatId())))
                    .append(" life=").append(number(player.getLifeTotal()))
                    .append(" poison=").append(number(player.getPoisonCounters()))
                    .append(" hand=").append(number(player.getHandSize()))
                    .append(" board=");
            if (player.getBattlefield().isEmpty()) {
                out.append('-');
            } else {
                StringJoiner board = new StringJoiner(";");
                for (BoardPermanentSnapshot permanent : player.getBattlefield()) {
                    board.add(permanent(permanent));
                }
                out.append(board);
            }
            out.append('\n');
        }
    }

    private String permanent(BoardPermanentSnapshot permanent) {
        StringBuilder out = new StringBuilder(compact(value(permanent.getName(), "?")));
        List<String> attributes = new ArrayList<>();
        if (permanent.getPower() != null && permanent.getToughness() != null) {
            attributes.add(permanent.getPower() + "/" + permanent.getToughness());
        }
        if (Boolean.TRUE.equals(permanent.getTapped())) attributes.add("tap");
        if (permanent.getAttachedToLogicalObjectId() != null) {
            attributes.add("attached#" + permanent.getAttachedToLogicalObjectId());
        }
        if (permanent.getOwnerSeatId() != permanent.getControllerSeatId()) {
            attributes.add("ctrl=" + permanent.getControllerSeatId());
        }
        for (CounterState counter : permanent.getCounters()) {
            if (counter.getCount() > 0) {
                attributes.add(compact(value(counter.getType(),
                        "counter#" + counter.getArenaType())) + "=" + counter.getCount());
            }
        }
        if (!attributes.isEmpty()) out.append('[').append(String.join(",", attributes)).append(']');
        return out.toString();
    }

    private void appendGameResult(StringBuilder out, GameResult result) {
        out.append("GR winner=").append(compact(value(result.getWinnerName(), "?")))
                .append(" reason=").append(result.getReason())
                .append(" confidence=").append(result.getConfidence());
        if (hasText(result.getFinishingCard())) {
            out.append(" card=").append(compact(result.getFinishingCard()));
        }
        out.append('\n');
    }

    private void appendMatchResult(StringBuilder out, MatchResult result) {
        out.append("MR winner=").append(compact(value(result.winnerName(), "?")));
        if (result.finalScore() != null) {
            out.append(" score=").append(result.finalScore().seatOneWins())
                    .append('-').append(result.finalScore().seatTwoWins());
            if (result.finalScore().draws() > 0) {
                out.append(" d=").append(result.finalScore().draws());
            }
        }
        out.append(" confidence=").append(result.confidence()).append('\n');
    }

    private void appendLifeChange(StringBuilder out, PlayerLifeChange change) {
        out.append("L ").append(compact(value(change.playerName(), "seat" + change.seatId())))
                .append(' ').append(change.kind())
                .append(' ').append(change.amount())
                .append(' ').append(change.previousLife()).append('>').append(change.currentLife());
        if (hasText(change.sourceName())) {
            out.append(" src=").append(compact(change.sourceName()));
        }
        out.append('\n');
    }

    private void appendPermanentDamage(StringBuilder out, PermanentDamage damage) {
        out.append("D ").append(compact(value(damage.targetName(), "?")))
                .append(" amount=").append(damage.amount());
        if (hasText(damage.sourceName())) {
            out.append(" src=").append(compact(damage.sourceName()));
        }
        out.append('\n');
    }

    private void appendCardNames(StringBuilder out, List<CardInfo> cards) {
        StringJoiner names = new StringJoiner("|");
        for (CardInfo card : cards) {
            names.add(compact(card == null ? "?" : value(card.getName(), "?")));
        }
        out.append(names);
    }

    private String compactPhase(String phase) {
        if (!hasText(phase)) return "";
        return switch (phase.trim()) {
            case "Phase_Beginning" -> "Beginning";
            case "Phase_Main1" -> "Main1";
            case "Phase_Combat" -> "Combat";
            case "Phase_Main2" -> "Main2";
            case "Phase_Ending" -> "Ending";
            default -> compact(phase);
        };
    }

    private String number(Integer value) {
        return value == null ? "?" : value.toString();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String value(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    /**
     * Keeps the line protocol unambiguous without verbose JSON quoting.
     */
    private String compact(String value) {
        if (value == null) return "?";
        return value.replace('\\', '/')
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replace('|', '/')
                .replace(';', ',')
                .replaceAll("\\s+", " ")
                .trim();
    }
}
