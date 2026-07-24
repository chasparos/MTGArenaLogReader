package app.export;


import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.session.GameModel;
import app.model.game.PlayerTurnSnapshot;
import app.model.game.BoardPermanentSnapshot;

import java.util.List;
import java.util.Objects;

/** Produces compact plain text intended for game review and chat analysis.
 * <p><strong>Architectural role:</strong> This type belongs to the output boundary and consumes structured replay data without reparsing raw Arena messages.</p>
 */
public final class GameTextExporter {
    public String export(GameModel game) {
        Objects.requireNonNull(game, "game");
        List<GameEvent> events = game.snapshot();

        StringBuilder out = new StringBuilder(4096);
        out.append("MTG Arena game report\n");
        out.append("Match: ").append(value(game.getMatchId(), "unknown")).append('\n');
        out.append("Game: ").append(game.getGameNumber()).append('\n');
        if (game.isComplete()) out.append("Status: completed\n");
        out.append('\n');
        List<app.model.card.CardInfo> opening = game.openingHandSnapshot();
        if (!opening.isEmpty()) {
            out.append("Opening hand");
            if (game.getOpeningHandPlayer() != null) out.append(" — ").append(game.getOpeningHandPlayer());
            out.append('\n');
            if (game.getMulliganCount() > 0) out.append("Mulligans: ").append(game.getMulliganCount()).append('\n');
            for (app.model.card.CardInfo card : opening) out.append("- ").append(card.getName()).append('\n');
            out.append('\n');
        }

        Integer previousTurn = null;
        String previousPhase = null;
        for (GameEvent event : events) {
            if (event.getTurnNumber() != null && !event.getTurnNumber().equals(previousTurn)) {
                if (previousTurn != null) out.append('\n');
                out.append("Turn ").append(event.getTurnNumber());
                if (event.getActivePlayerName() != null && !event.getActivePlayerName().isBlank()) {
                    out.append(" — ").append(event.getActivePlayerName());
                }
                out.append('\n');
                previousTurn = event.getTurnNumber();
                previousPhase = null;
            }

            if (!event.getTurnSnapshot().isEmpty()) {
                out.append("Start of turn\n");
                for (PlayerTurnSnapshot snapshot : event.getTurnSnapshot()) {
                    out.append(snapshot.getPlayerName()).append(" — ");
                    out.append(snapshot.getLifeTotal() == null ? "life ?" : snapshot.getLifeTotal() + " life");
                    if (snapshot.getPoisonCounters() != null && snapshot.getPoisonCounters() > 0) {
                        out.append(", ").append(snapshot.getPoisonCounters()).append(" poison");
                    }
                    out.append(snapshot.getHandSize() == null
                            ? ", hand ?"
                            : ", " + snapshot.getHandSize() + " cards in hand");
                    out.append('\n');
                    appendKnownCards(out, "Known hand", snapshot.getKnownHand());
                    appendKnownCards(out, "Graveyard", snapshot.getKnownGraveyard());
                    appendKnownCards(out, "Exile", snapshot.getKnownExile());
                    if (snapshot.getBattlefield().isEmpty()) {
                        out.append("  Battlefield: empty\n");
                    } else {
                        out.append("  Battlefield:\n");
                        for (BoardPermanentSnapshot permanent : roots(snapshot.getBattlefield())) {
                            out.append("    • ").append(boardPermanentText(permanent)).append('\n');
                            for (BoardPermanentSnapshot attached : attachmentsOf(
                                    snapshot.getBattlefield(), permanent.getLogicalObjectId())) {
                                out.append("      ↳ ").append(boardPermanentText(attached))
                                        .append(" [attached]\n");
                            }
                        }
                    }
                }
                continue;
            }

            if (event.getGameResult() != null) {
                out.append("\nGame result:\n");
                out.append("- ").append(event.getText()).append('\n');
                previousPhase = null;
                continue;
            }

            String phase = phase(event);
            if (!phase.isBlank() && !phase.equals(previousPhase)) {
                out.append(phase).append(':').append('\n');
                previousPhase = phase;
            }
            out.append("- ").append(event.getText()).append('\n');
        }
        return out.toString();
    }

    public String exportRaw(GameModel game) {
        Objects.requireNonNull(game, "game");
        StringBuilder out = new StringBuilder();
        out.append("# Raw MTG Arena records for match ")
                .append(value(game.getMatchId(), "unknown"))
                .append(", game ").append(game.getGameNumber()).append("\n\n");
        for (String record : game.rawRecordSnapshot()) {
            out.append(record.strip()).append("\n\n");
        }
        return out.toString();
    }

    private List<BoardPermanentSnapshot> roots(List<BoardPermanentSnapshot> battlefield) {
        java.util.Set<Long> ids = battlefield.stream()
                .map(BoardPermanentSnapshot::getLogicalObjectId)
                .collect(java.util.stream.Collectors.toSet());
        return battlefield.stream()
                .filter(permanent -> permanent.getAttachedToLogicalObjectId() == null
                        || !ids.contains(permanent.getAttachedToLogicalObjectId()))
                .toList();
    }

    private List<BoardPermanentSnapshot> attachmentsOf(
            List<BoardPermanentSnapshot> battlefield, long hostId) {
        return battlefield.stream()
                .filter(permanent -> permanent.getAttachedToLogicalObjectId() != null
                        && permanent.getAttachedToLogicalObjectId() == hostId)
                .toList();
    }

    private void appendKnownCards(StringBuilder out, String label,
                                  java.util.List<app.model.card.CardInfo> cards) {
        if (cards.isEmpty()) return;
        out.append("  ").append(label).append(": ")
                .append(cards.stream()
                        .map(app.model.card.CardInfo::getName)
                        .filter(java.util.Objects::nonNull)
                        .collect(java.util.stream.Collectors.joining(", ")))
                .append('\n');
    }

    private String boardPermanentText(BoardPermanentSnapshot permanent) {
        StringBuilder text = new StringBuilder(
                permanent.getName() == null || permanent.getName().isBlank()
                        ? "Unknown permanent" : permanent.getName());
        if (permanent.getPower() != null && permanent.getToughness() != null) {
            text.append(" (").append(permanent.getPower()).append('/')
                    .append(permanent.getToughness()).append(')');
        }
        if (Boolean.TRUE.equals(permanent.getTapped())) text.append(" [tapped]");
        if (!permanent.getUnlockedRoomHalves().isEmpty()) {
            text.append(" [unlocked: ")
                    .append(String.join(", ", permanent.getUnlockedRoomHalves()))
                    .append(']');
        }
        return text.toString();
    }

    private String phase(GameEvent event) {
        String phase = value(event.getPhase(), "");
        String step = value(event.getStep(), "");
        if (phase.isBlank()) return "";
        if (step.isBlank() || phase.equalsIgnoreCase(step)) return phase;
        return phase + " / " + step;
    }

    private String value(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
