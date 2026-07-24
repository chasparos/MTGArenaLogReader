package app.export;

import app.model.card.CardInfo;
import app.model.event.AbilityReference;
import app.model.event.DecisionObservation;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.event.ObjectReference;
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
import java.util.LinkedHashMap;

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
        out.append("MTGA_MATCH_V3\n");
        out.append("schema=G game;H opening;T turn;P phase;S# state;"
                + "E# event;A# ability;C# decision;L# life;D# pw-damage;"
                + "GR# result;MS# score;MR# match-result;obj Name#logical@grp;? unknown\n");
        out.append("ids=S/E/A/C/L/D/GR/MS/MR are stable within this export;"
                + " object references preserve logical identity when observed;"
                + " decisions list only Arena-observed legal alternatives\n");
        out.append("match=").append(value(match.matchState().getMatchId(), "?")).append('\n');
        appendPlayers(out, match.matchState().playerSnapshot());

        int[] nextEventId = {1};
        for (GameModel game : games) {
            appendGame(out, game, nextEventId);
        }
        return compactReport(match, out.toString());
    }

    private String compactReport(MatchSession match, String verbose) {
        String[] lines = verbose.split("\\R", -1);
        StringBuilder body = new StringBuilder(verbose.length());
        for (int i = 3; i < lines.length; i++) {
            if (lines[i].startsWith("players=")) continue;
            body.append(lines[i]).append('\n');
        }

        LinkedHashMap<String, String> replacements = new LinkedHashMap<>();
        match.matchState().playerSnapshot().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> replacements.put(entry.getValue(), "p" + entry.getKey()));

        LinkedHashMap<String, Long> cards = cardDictionary(match);
        int alias = 1;
        for (String name : cards.keySet()) replacements.putIfAbsent(name, "c" + alias++);

        List<Map.Entry<String, String>> ordered = replacements.entrySet().stream()
                .sorted((left, right) -> Integer.compare(right.getKey().length(), left.getKey().length()))
                .toList();
        String compactBody = body.toString();
        for (Map.Entry<String, String> replacement : ordered) {
            compactBody = compactBody.replace(replacement.getKey(), replacement.getValue());
        }
        compactBody = compactZones(compactBody);

        StringBuilder out = new StringBuilder(compactBody.length() + 1024);
        out.append("MTGA_MATCH_V5\n");
        out.append("K G=game H=opening T=turn P=phase S=state E=event A=ability C=decision L=life D=permanent-damage GR=result MS=score MR=match-result\n");
        out.append("Z L=library H=hand B=battlefield G=graveyard S=stack X=exile M=limbo C=command; MOVE x>y is an observed zone transition\n");
        out.append("STATE knownH/knownG/knownX list identities known in hand/graveyard/exile;"
                + " permanent attributes include P/T,tap,unlocked,abilities,counters,attachments,control\n");
        out.append("match=").append(value(match.matchState().getMatchId(), "?")).append('\n');
        if (!match.matchState().playerSnapshot().isEmpty()) {
            StringJoiner players = new StringJoiner("|");
            match.matchState().playerSnapshot().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> players.add("p" + entry.getKey() + "=" + compact(entry.getValue())));
            out.append("PLAYERS ").append(players).append('\n');
        }
        if (!cards.isEmpty()) {
            int number = 1;
            for (Map.Entry<String, Long> card : cards.entrySet()) {
                out.append("CARD c").append(number++).append('=').append(compact(card.getKey()));
                if (card.getValue() != null && card.getValue() > 0) out.append('@').append(card.getValue());
                out.append('\n');
            }
        }
        out.append(compactBody);
        return out.toString();
    }

    private LinkedHashMap<String, Long> cardDictionary(MatchSession match) {
        LinkedHashMap<String, Long> cards = new LinkedHashMap<>();
        List<GameModel> games = new ArrayList<>(match.gameSnapshot());
        games.sort(Comparator.comparingInt(GameModel::getGameNumber));
        for (GameModel game : games) {
            for (CardInfo card : game.openingHandSnapshot()) addCard(cards, card == null ? null : card.getName(), card == null ? null : card.getArenaId());
            for (GameEvent event : game.snapshot()) {
                for (CardInfo card : event.getCards()) addCard(cards, card == null ? null : card.getName(), card == null ? null : card.getArenaId());
                for (ObjectReference reference : event.getObjects()) {
                    addReferenceCard(cards, reference);
                }
                if (event.getDecision() != null) {
                    addReferenceCard(cards, event.getDecision().source());
                    event.getDecision().selected().forEach(reference -> addReferenceCard(cards, reference));
                    event.getDecision().alternatives().forEach(reference -> addReferenceCard(cards, reference));
                }
                for (PlayerTurnSnapshot player : event.getTurnSnapshot()) {
                    for (BoardPermanentSnapshot permanent : player.getBattlefield()) {
                        CardInfo card = permanent.getCard();
                        addCard(cards, permanent.getName(),
                                card == null ? null : card.getArenaId());
                    }
                    player.getKnownHand().forEach(card ->
                            addCard(cards, card == null ? null : card.getName(),
                                    card == null ? null : card.getArenaId()));
                    player.getKnownGraveyard().forEach(card ->
                            addCard(cards, card == null ? null : card.getName(),
                                    card == null ? null : card.getArenaId()));
                    player.getKnownExile().forEach(card ->
                            addCard(cards, card == null ? null : card.getName(),
                                    card == null ? null : card.getArenaId()));
                }
                if (event.getGameResult() != null) addCard(cards, event.getGameResult().getFinishingCard(), null);
            }
        }
        return cards;
    }

    private void addReferenceCard(LinkedHashMap<String, Long> cards, ObjectReference reference) {
        if (reference != null && !reference.isPlayer() && reference.arenaGrpId() > 0) {
            addCard(cards, reference.name(), reference.arenaGrpId());
        }
    }

    private void addCard(LinkedHashMap<String, Long> cards, String name, Long arenaId) {
        if (!hasText(name) || name.startsWith("Unknown ")) return;
        cards.merge(name, arenaId == null ? 0L : arenaId, (known, observed) -> known > 0 ? known : observed);
    }

    private String compactZones(String text) {
        return text.replace(" moved Library → Graveyard", " MOVE L>G")
                .replace(" moved Library → Hand", " MOVE L>H")
                .replace(" moved Hand → Stack", " MOVE H>S")
                .replace(" moved Stack → Hand", " MOVE S>H")
                .replace(" moved Stack → Graveyard", " MOVE S>G")
                .replace(" moved Battlefield → Graveyard", " MOVE B>G")
                .replace(" moved Graveyard → Hand", " MOVE G>H")
                .replace(" moved Graveyard → Exile", " MOVE G>X")
                .replace(" moved Battlefield → Exile", " MOVE B>X");
    }

    private void appendPlayers(StringBuilder out, Map<Integer, String> players) {
        if (players.isEmpty()) return;
        StringJoiner values = new StringJoiner("|");
        players.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> values.add(entry.getKey() + ":" + compact(entry.getValue())));
        out.append("players=").append(values).append('\n');
    }

    private void appendGame(StringBuilder out, GameModel game, int[] nextEventId) {
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
            int eventId = nextEventId[0]++;

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
                appendTurnSnapshot(out, eventId, event.getTurnSnapshot());
                continue;
            }
            if (event.getDecision() != null) {
                appendDecision(out, eventId, event.getDecision());
                continue;
            }

            if (event.getGameResult() != null) {
                appendGameResult(out, eventId, event.getGameResult());
                continue;
            }
            if (event.getMatchScore() != null) {
                MatchScore score = event.getMatchScore();
                out.append("MS#").append(eventId).append(' ').append(score.seatOneWins()).append('-')
                        .append(score.seatTwoWins());
                if (score.draws() > 0) out.append(" d=").append(score.draws());
                out.append('\n');
                continue;
            }
            if (event.getMatchResult() != null) {
                appendMatchResult(out, eventId, event.getMatchResult());
                continue;
            }
            if (event.getPlayerLifeChange() != null) {
                appendLifeChange(out, eventId, event.getPlayerLifeChange());
                continue;
            }
            if (event.getPermanentDamage() != null) {
                appendPermanentDamage(out, eventId, event.getPermanentDamage());
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
            if (event.getAbility() != null) {
                appendAbility(out, eventId, event);
            } else if (hasText(event.getText())) {
                out.append("E#").append(eventId)
                        .append(" text=").append(compact(event.getText()));
                appendObjectReferences(out, event.getObjects());
                appendCardIdentities(out, event.getCards());
                out.append('\n');
            }
        }
    }

    private void appendTurnSnapshot(StringBuilder out, int eventId, List<PlayerTurnSnapshot> snapshots) {
        for (PlayerTurnSnapshot player : snapshots) {
            out.append("S#").append(eventId).append(' ').append(compact(value(player.getPlayerName(),
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
            appendKnownZone(out, "knownH", player.getKnownHand());
            appendKnownZone(out, "knownG", player.getKnownGraveyard());
            appendKnownZone(out, "knownX", player.getKnownExile());
            out.append('\n');
        }
    }

    private void appendKnownZone(StringBuilder out, String label, List<CardInfo> cards) {
        if (cards == null || cards.isEmpty()) return;
        out.append(' ').append(label).append('=');
        StringJoiner identities = new StringJoiner("|");
        cards.stream()
                .filter(Objects::nonNull)
                .sorted(Comparator
                        .comparingInt(this::cardTypeOrder)
                        .thenComparing(card -> compact(value(card.getName(), "?")),
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparingLong(card -> card.getArenaId() == null ? 0L : card.getArenaId()))
                .forEach(card -> identities.add(cardIdentity(card)));
        String value = identities.toString();
        out.append(value.isEmpty() ? "-" : value);
    }

    private int cardTypeOrder(CardInfo card) {
        String typeLine = card == null || card.effectiveTypeLine() == null
                ? "" : card.effectiveTypeLine().toLowerCase(java.util.Locale.ROOT);
        if (typeLine.contains("land")) return 0;
        if (typeLine.contains("creature")) return 1;
        if (typeLine.contains("enchantment")) return 2;
        if (typeLine.contains("artifact")) return 3;
        if (typeLine.contains("planeswalker")) return 4;
        if (typeLine.contains("battle")) return 5;
        if (typeLine.contains("instant")) return 6;
        if (typeLine.contains("sorcery")) return 7;
        return 8;
    }

    private String cardIdentity(CardInfo card) {
        StringBuilder identity = new StringBuilder(compact(value(card.getName(), "?")));
        if (card.getArenaId() != null && card.getArenaId() > 0) {
            identity.append('@').append(card.getArenaId());
        }
        return identity.toString();
    }

    private String permanent(BoardPermanentSnapshot permanent) {
        StringBuilder out = new StringBuilder(compact(value(permanent.getName(), "?")))
                .append('#').append(permanent.getLogicalObjectId());
        List<String> attributes = new ArrayList<>();
        if (permanent.getPower() != null && permanent.getToughness() != null) {
            attributes.add(permanent.getPower() + "/" + permanent.getToughness());
        }
        if (Boolean.TRUE.equals(permanent.getTapped())) attributes.add("tap");
        if (!permanent.getUnlockedRoomHalves().isEmpty()) {
            attributes.add("unlocked="
                    + permanent.getUnlockedRoomHalves().stream()
                            .map(this::compact)
                            .collect(java.util.stream.Collectors.joining("|")));
        }
        if (!permanent.getEvergreenAbilities().isEmpty()) {
            attributes.add("abilities="
                    + permanent.getEvergreenAbilities().stream()
                            .map(this::compact)
                            .collect(java.util.stream.Collectors.joining("|")));
        }
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

    private void appendGameResult(StringBuilder out, int eventId, GameResult result) {
        out.append("GR#").append(eventId).append(" winner=")
                .append(playerReference(result.getWinnerSeatId(), result.getWinnerName()))
                .append(" reason=").append(result.getReason())
                .append(" confidence=").append(result.getConfidence());
        if (hasText(result.getFinishingCard())) {
            out.append(" card=").append(compact(result.getFinishingCard()));
        }
        out.append('\n');
    }

    private void appendMatchResult(StringBuilder out, int eventId, MatchResult result) {
        out.append("MR#").append(eventId).append(" winner=")
                .append(playerReference(result.winnerSeatId(), result.winnerName()));
        if (result.finalScore() != null) {
            out.append(" score=").append(result.finalScore().seatOneWins())
                    .append('-').append(result.finalScore().seatTwoWins());
            if (result.finalScore().draws() > 0) {
                out.append(" d=").append(result.finalScore().draws());
            }
        }
        out.append(" confidence=").append(result.confidence()).append('\n');
    }

    private String playerReference(Integer seatId, String playerName) {
        if (seatId != null && seatId > 0) {
            return "p" + seatId;
        }
        return compact(value(playerName, "?"));
    }

    private void appendLifeChange(StringBuilder out, int eventId, PlayerLifeChange change) {
        out.append("L#").append(eventId).append(' ').append(compact(value(change.playerName(), "seat" + change.seatId())))
                .append(' ').append(change.kind())
                .append(' ').append(change.amount())
                .append(' ').append(change.previousLife()).append('>').append(change.currentLife());
        if (hasText(change.sourceName())) {
            out.append(" src=").append(compact(change.sourceName()));
        }
        out.append('\n');
    }

    private void appendPermanentDamage(StringBuilder out, int eventId, PermanentDamage damage) {
        out.append("D#").append(eventId).append(' ').append(compact(value(damage.targetName(), "?")))
                .append(" amount=").append(damage.amount());
        if (hasText(damage.sourceName())) {
            out.append(" src=").append(compact(damage.sourceName()));
        }
        out.append('\n');
    }

    private void appendDecision(StringBuilder out,
                                int eventId,
                                DecisionObservation decision) {
        out.append("C#").append(eventId)
                .append(" kind=").append(decision.kind())
                .append(" confidence=").append(decision.confidence());
        if (decision.source() != null) {
            out.append(" source=").append(reference(decision.source()));
        }
        out.append(" chosen=").append(referenceList(decision.selected()))
                .append(" alternatives=").append(referenceList(decision.alternatives()))
                .append(" min=").append(decision.minimumSelections())
                .append(" max=").append(decision.maximumSelections())
                .append('\n');
    }

    private void appendAbility(StringBuilder out, int eventId, GameEvent event) {
        AbilityReference ability = event.getAbility();
        out.append("A#").append(eventId)
                .append(" kind=").append(compact(value(ability.getKind(), "unknown")))
                .append(" source=").append(compact(value(ability.getSourceName(), "?")));
        if (ability.getSourceGrpId() > 0) {
            out.append("@").append(ability.getSourceGrpId());
        }
        if (ability.getAbilityGrpId() > 0) {
            out.append(" abilityGrp=").append(ability.getAbilityGrpId());
        }
        appendObjectReferences(out, event.getObjects());
        if (hasText(event.getText())) {
            out.append(" text=").append(compact(event.getText()));
        }
        appendCardIdentities(out, event.getCards());
        out.append('\n');
    }

    private void appendObjectReferences(StringBuilder out,
                                        List<ObjectReference> references) {
        if (references == null || references.isEmpty()) return;
        out.append(" objects=").append(referenceList(references));
    }

    private String referenceList(List<ObjectReference> references) {
        if (references == null || references.isEmpty()) return "-";
        StringJoiner result = new StringJoiner("|");
        references.forEach(reference -> result.add(reference(reference)));
        return result.toString();
    }

    private String reference(ObjectReference reference) {
        if (reference == null) return "?";
        if (reference.isPlayer()) {
            return compact(value(reference.playerName(), "seat" + reference.playerSeat()))
                    + "$" + reference.playerSeat();
        }
        StringBuilder out = new StringBuilder(compact(value(reference.name(), "?")));
        if (reference.logicalObjectId() > 0) out.append('#').append(reference.logicalObjectId());
        if (reference.arenaGrpId() > 0) out.append('@').append(reference.arenaGrpId());
        else if (reference.arenaInstanceId() > 0 && reference.logicalObjectId() <= 0) {
            out.append('!').append(reference.arenaInstanceId());
        }
        return out.toString();
    }

    private void appendCardIdentities(StringBuilder out, List<CardInfo> cards) {
        if (cards == null || cards.isEmpty()) return;
        StringJoiner identities = new StringJoiner("|");
        for (CardInfo card : cards) {
            if (card == null) {
                identities.add("?");
                continue;
            }
            StringBuilder identity = new StringBuilder(compact(value(card.getName(), "?")));
            if (card.getArenaId() != null) {
                identity.append('@').append(card.getArenaId());
            }
            identities.add(identity);
        }
        out.append(" cards=").append(identities);
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
