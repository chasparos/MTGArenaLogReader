package app.export;

import app.model.card.CardInfo;
import app.model.event.AbilityReference;
import app.model.event.DecisionObservation;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.event.ObjectReference;
import app.model.event.ZoneTransitionObservation;
import app.model.event.TargetObservation;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.CounterState;
import app.model.game.GameResult;
import app.model.game.PermanentDamage;
import app.model.game.PlayerLifeChange;
import app.model.game.PlayerTurnSnapshot;
import app.model.game.PlayerTurnDelta;
import app.projection.TurnStateDiffer;
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

    private ExportContext context;

    public synchronized String export(MatchSession match) {
        Objects.requireNonNull(match, "match");

        List<GameModel> games = new ArrayList<>(match.gameSnapshot());
        games.sort(Comparator.comparingInt(GameModel::getGameNumber));
        context = new ExportContext(match.matchState().playerSnapshot(), cardDictionary(match));
        try {
            StringBuilder out = new StringBuilder(8192);
            out.append("MTGA_MATCH_V5\n");
            out.append("K G=game H=opening T=turn P=phase S=state TD=turn-delta E=event A=ability C=decision MOVE=zone-transition L=life D=permanent-damage GR=result MS=score MR=match-result\n");
            out.append("Z L=library H=hand B=battlefield G=graveyard S=stack X=exile M=limbo C=command; MOVE x>y is an observed zone transition\n");
            out.append("Q quoted values escape backslash and quote with a preceding backslash; line breaks become spaces\n");
            out.append("STATE knownH/knownG/knownX list identities known in hand/graveyard/exile;"
                    + " permanent attributes include P/T,tap,unlocked,abilities,counters,attachments,control\n");
            out.append("match=").append(quoted(value(match.matchState().getMatchId(), "?"))).append('\n');
            appendDictionaries(out);

            int[] nextEventId = {1};
            for (GameModel game : games) {
                appendGame(out, game, nextEventId);
            }
            return out.toString();
        } finally {
            context = null;
        }
    }

    private void appendDictionaries(StringBuilder out) {
        if (!context.players().isEmpty()) {
            StringJoiner players = new StringJoiner("|");
            context.players().forEach((seat, name) ->
                    players.add("p" + seat + "=" + quoted(name)));
            out.append("PLAYERS ").append(players).append('\n');
        }
        context.cards().forEach((name, entry) -> {
            out.append("CARD ").append(entry.alias()).append('=').append(quoted(name));
            if (entry.arenaId() > 0) out.append('@').append(entry.arenaId());
            out.append('\n');
        });
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
                if (event.getTargetObservation() != null) {
                    addReferenceCard(cards, event.getTargetObservation().source());
                    event.getTargetObservation().targets().forEach(reference -> addReferenceCard(cards, reference));
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

    private void appendGame(StringBuilder out, GameModel game, int[] nextEventId) {
        out.append("\nG").append(game.getGameNumber());
        if (game.isComplete()) out.append(" complete");
        out.append('\n');

        List<CardInfo> opening = game.openingHandSnapshot();
        if (!opening.isEmpty()) {
            out.append("H player=").append(player(value(game.getOpeningHandPlayer(), "?")))
                    .append(" mull=").append(game.getMulliganCount())
                    .append(" cards=");
            appendCardNames(out, opening);
            out.append('\n');
        }

        Integer currentTurn = null;
        String currentPhase = null;
        String currentStep = null;
        List<PendingTarget> pendingTargets = new ArrayList<>();
        List<PlayerTurnSnapshot> previousTurnSnapshot = null;
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
                    out.append(" active=").append(player(event.getActivePlayerName()));
                }
                out.append('\n');
            }

            if (!event.getTurnSnapshot().isEmpty()) {
                if (previousTurnSnapshot != null) {
                    appendTurnDeltas(out, eventId, new TurnStateDiffer().diff(previousTurnSnapshot, event.getTurnSnapshot()));
                }
                appendTurnSnapshot(out, eventId, event.getTurnSnapshot());
                previousTurnSnapshot = List.copyOf(event.getTurnSnapshot());
                continue;
            }
            if (event.getDecision() != null) {
                appendDecision(out, eventId, event.getDecision());
                continue;
            }
            if (event.getTargetObservation() != null) {
                appendTargetObservation(out, eventId, event.getTargetObservation(), event.getText());
                pendingTargets.add(new PendingTarget(eventId, event.getTargetObservation()));
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
                appendDamageCausalLink(out, pendingTargets, eventId, event);
                continue;
            }
            if (event.getPermanentDamage() != null) {
                appendPermanentDamage(out, eventId, event.getPermanentDamage());
                appendDamageCausalLink(out, pendingTargets, eventId, event);
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
            } else if (event.getZoneTransition() != null) {
                appendZoneTransition(out, eventId, event);
                appendCausalLink(out, pendingTargets, eventId, event);
            } else if (hasText(event.getText())) {
                out.append("E#").append(eventId)
                        .append(" text=").append(quoted(event.getText()));
                appendObjectReferences(out, event.getObjects());
                appendCardIdentities(out, event.getCards());
                out.append('\n');
            }
        }
    }


    private void appendCausalLink(StringBuilder out, List<PendingTarget> pendingTargets,
                                  int outcomeEventId, GameEvent outcome) {
        ZoneTransitionObservation transition = outcome.getZoneTransition();
        if (transition == null || transition.subject() == null) return;
        String kind = switch (transition.reason()) {
            case COUNTERED -> "COUNTER";
            case RETURNED_TO_HAND -> "BOUNCE";
            case PUT_INTO_GRAVEYARD -> "DESTROY";
            case EXILED_FROM_BATTLEFIELD, EXILED_FROM_GRAVEYARD -> "EXILE";
            default -> null;
        };
        if (kind == null) return;
        PendingTarget match = uniqueTarget(pendingTargets, transition.subject());
        if (match == null) return;
        out.append("LINK#").append(outcomeEventId)
                .append(" cause=TARGET#").append(match.eventId())
                .append(" outcome=").append(kind)
                .append(" provenance=UNIQUE_TARGET_CORRELATION")
                .append(" confidence=CORRELATED\n");
        pendingTargets.remove(match);
    }

    private void appendDamageCausalLink(StringBuilder out, List<PendingTarget> pendingTargets,
                                        int outcomeEventId, GameEvent outcome) {
        ObjectReference target = null;
        if (outcome.getPermanentDamage() != null) {
            var damage = outcome.getPermanentDamage();
            target = new ObjectReference(damage.targetLogicalObjectId(), 0, 0,
                    damage.targetName(), null, null);
        } else if (outcome.getPlayerLifeChange() != null
                && outcome.getPlayerLifeChange().kind() == PlayerLifeChange.Kind.DAMAGE) {
            var damage = outcome.getPlayerLifeChange();
            target = new ObjectReference(0, 0, 0, damage.playerName(), damage.seatId(), null);
        }
        if (target == null) return;
        PendingTarget match = uniqueTarget(pendingTargets, target);
        if (match == null) return;
        out.append("LINK#").append(outcomeEventId)
                .append(" cause=TARGET#").append(match.eventId())
                .append(" outcome=DAMAGE provenance=UNIQUE_TARGET_CORRELATION")
                .append(" confidence=CORRELATED\n");
        pendingTargets.remove(match);
    }

    private PendingTarget uniqueTarget(List<PendingTarget> pendingTargets, ObjectReference target) {
        List<PendingTarget> matches = pendingTargets.stream()
                .filter(pending -> pending.observation().targets().stream()
                        .anyMatch(candidate -> sameReference(candidate, target)))
                .toList();
        return matches.size() == 1 ? matches.get(0) : null;
    }

    private boolean sameReference(ObjectReference left, ObjectReference right) {
        if (left == null || right == null) return false;
        if (left.isPlayer() || right.isPlayer()) {
            return left.playerSeat() != null && left.playerSeat().equals(right.playerSeat());
        }
        if (left.logicalObjectId() > 0 && right.logicalObjectId() > 0) {
            return left.logicalObjectId() == right.logicalObjectId();
        }
        return left.arenaInstanceId() > 0 && left.arenaInstanceId() == right.arenaInstanceId();
    }

    private record PendingTarget(int eventId, TargetObservation observation) {}

    private void appendTurnDeltas(StringBuilder out, int eventId, List<PlayerTurnDelta> deltas) {
        for (PlayerTurnDelta delta : deltas) {
            out.append("TD#").append(eventId).append(' ')
                    .append(player(value(delta.playerName(), "seat" + delta.seatId())));
            if (delta.lifeChange() != null && delta.lifeChange() != 0) out.append(" life=").append(signed(delta.lifeChange()));
            if (delta.handSizeChange() != null && delta.handSizeChange() != 0) out.append(" hand=").append(signed(delta.handSizeChange()));
            if (!delta.enteredBattlefield().isEmpty()) out.append(" board+=").append(permanentList(delta.enteredBattlefield()));
            if (!delta.leftBattlefield().isEmpty()) out.append(" board-=").append(permanentList(delta.leftBattlefield()));
            if (!delta.enteredKnownHand().isEmpty()) out.append(" knownH+=").append(cardList(delta.enteredKnownHand()));
            if (!delta.leftKnownHand().isEmpty()) out.append(" knownH-=").append(cardList(delta.leftKnownHand()));
            if (!delta.enteredKnownGraveyard().isEmpty()) out.append(" knownG+=").append(cardList(delta.enteredKnownGraveyard()));
            if (!delta.enteredKnownExile().isEmpty()) out.append(" knownX+=").append(cardList(delta.enteredKnownExile()));
            if (!delta.counterChanges().isEmpty()) {
                StringJoiner changes = new StringJoiner("|");
                delta.counterChanges().forEach(change -> changes.add(
                        card(value(change.permanentName(), "?")) + "#" + change.logicalObjectId()
                                + ":" + compact(change.counterType()) + signed(change.change())));
                out.append(" counters=").append(changes);
            }
            out.append('
');
        }
    }

    private String permanentList(List<BoardPermanentSnapshot> permanents) {
        StringJoiner result = new StringJoiner("|");
        permanents.forEach(permanent -> result.add(permanent(permanent)));
        return result.toString();
    }

    private String cardList(List<CardInfo> cards) {
        StringJoiner result = new StringJoiner("|");
        cards.forEach(card -> result.add(cardIdentity(card)));
        return result.toString();
    }

    private String signed(int value) {
        return value > 0 ? "+" + value : Integer.toString(value);
    }

    private void appendTurnSnapshot(StringBuilder out, int eventId, List<PlayerTurnSnapshot> snapshots) {
        for (PlayerTurnSnapshot player : snapshots) {
            out.append("S#").append(eventId).append(' ').append(player(value(player.getPlayerName(), "seat" + player.getSeatId())))
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
                        .thenComparing(card -> card(value(card.getName(), "?")),
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
        StringBuilder identity = new StringBuilder(card(value(card.getName(), "?")));
        if (card.getArenaId() != null && card.getArenaId() > 0) {
            identity.append('@').append(card.getArenaId());
        }
        return identity.toString();
    }

    private String permanent(BoardPermanentSnapshot permanent) {
        StringBuilder out = new StringBuilder(card(value(permanent.getName(), "?")))
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
            out.append(" card=").append(card(result.getFinishingCard()));
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
        return player(value(playerName, "?"));
    }

    private void appendLifeChange(StringBuilder out, int eventId, PlayerLifeChange change) {
        out.append("L#").append(eventId).append(' ').append(player(value(change.playerName(), "seat" + change.seatId())))
                .append(' ').append(change.kind())
                .append(' ').append(change.amount())
                .append(' ').append(change.previousLife()).append('>').append(change.currentLife());
        if (hasText(change.sourceName())) {
            out.append(" src=").append(card(change.sourceName()));
        }
        out.append('\n');
    }

    private void appendPermanentDamage(StringBuilder out, int eventId, PermanentDamage damage) {
        out.append("D#").append(eventId).append(' ').append(card(value(damage.targetName(), "?")))
                .append(" amount=").append(damage.amount());
        if (hasText(damage.sourceName())) {
            out.append(" src=").append(card(damage.sourceName()));
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



    private void appendTargetObservation(StringBuilder out, int eventId,
                                         TargetObservation observation, String text) {
        out.append("TARGET#").append(eventId)
                .append(" provenance=").append(observation.provenance())
                .append(" confidence=").append(observation.confidence());
        if (observation.source() != null) {
            out.append(" source=").append(reference(observation.source()));
        }
        if (observation.abilityGrpId() > 0) {
            out.append(" abilityGrp=").append(observation.abilityGrpId());
        }
        out.append(" targets=").append(referenceList(observation.targets()));
        if (hasText(text)) out.append(" text=").append(quoted(text));
        out.append('\n');
    }

    private void appendZoneTransition(StringBuilder out, int eventId, GameEvent event) {
        ZoneTransitionObservation transition = event.getZoneTransition();
        out.append("MOVE#").append(eventId)
                .append(' ').append(zone(transition.fromZone()))
                .append('>').append(zone(transition.toZone()))
                .append(" reason=").append(transition.reason())
                .append(" provenance=").append(transition.provenance())
                .append(" confidence=").append(transition.confidence());
        if (transition.subject() != null) {
            out.append(" subject=").append(reference(transition.subject()));
        }
        if (hasText(event.getText())) {
            out.append(" text=").append(quoted(event.getText()));
        }
        out.append('\n');
    }

    private String zone(String zone) {
        if (zone == null) return "?";
        return switch (zone) {
            case "Library" -> "L";
            case "Hand" -> "H";
            case "Battlefield" -> "B";
            case "Graveyard" -> "G";
            case "Stack" -> "S";
            case "Exile" -> "X";
            case "Limbo" -> "M";
            case "Command" -> "C";
            default -> compact(zone);
        };
    }

    private void appendAbility(StringBuilder out, int eventId, GameEvent event) {
        AbilityReference ability = event.getAbility();
        out.append("A#").append(eventId)
                .append(" kind=").append(compact(value(ability.getKind(), "unknown")))
                .append(" source=").append(card(value(ability.getSourceName(), "?")));
        if (ability.getSourceGrpId() > 0) {
            out.append("@").append(ability.getSourceGrpId());
        }
        if (ability.getAbilityGrpId() > 0) {
            out.append(" abilityGrp=").append(ability.getAbilityGrpId());
        }
        if (ability.getChapter() != null) {
            out.append(" chapter=").append(ability.getChapter());
        }
        if (hasText(ability.getEffectText())) {
            out.append(" effect=\"").append(escape(ability.getEffectText())).append('\"');
        }
        if (hasText(ability.getConfidence())) {
            out.append(" confidence=").append(ability.getConfidence());
        }
        appendObjectReferences(out, event.getObjects());
        if (hasText(event.getText())) {
            out.append(" text=").append(quoted(event.getText()));
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
            return player(value(reference.playerName(), "seat" + reference.playerSeat()))
                    + "$" + reference.playerSeat();
        }
        StringBuilder out = new StringBuilder(card(value(reference.name(), "?")));
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
            StringBuilder identity = new StringBuilder(card(value(card.getName(), "?")));
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
            names.add(card(card == null ? "?" : value(card.getName(), "?")));
        }
        out.append(names);
    }

    private String player(String name) {
        return context.playerAlias(name);
    }

    private String card(String name) {
        return context.cardAlias(name);
    }

    private String quoted(String value) {
        return "\"" + escape(value == null ? "?" : value) + "\"";
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
    private String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", " ")
                .replace("\n", " ");
    }

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

    private record CardAlias(String alias, long arenaId) {}

    private static final class ExportContext {
        private final LinkedHashMap<Integer, String> players = new LinkedHashMap<>();
        private final LinkedHashMap<String, CardAlias> cards = new LinkedHashMap<>();
        private final Map<String, String> playerAliases = new LinkedHashMap<>();

        private ExportContext(Map<Integer, String> observedPlayers,
                              LinkedHashMap<String, Long> observedCards) {
            observedPlayers.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(entry -> {
                        players.put(entry.getKey(), entry.getValue());
                        playerAliases.put(entry.getValue(), "p" + entry.getKey());
                    });
            int number = 1;
            for (Map.Entry<String, Long> entry : observedCards.entrySet()) {
                cards.put(entry.getKey(), new CardAlias("c" + number++,
                        entry.getValue() == null ? 0L : entry.getValue()));
            }
        }

        private LinkedHashMap<Integer, String> players() { return players; }
        private LinkedHashMap<String, CardAlias> cards() { return cards; }

        private String playerAlias(String name) {
            if (name == null || name.isBlank()) return "?";
            return playerAliases.getOrDefault(name, safeAtom(name));
        }

        private String cardAlias(String name) {
            if (name == null || name.isBlank()) return "?";
            CardAlias alias = cards.get(name);
            return alias == null ? safeAtom(name) : alias.alias();
        }

        private static String safeAtom(String value) {
            String compact = value.replace('\\', '/')
                    .replace('\r', ' ')
                    .replace('\n', ' ')
                    .replace('|', '/')
                    .replace(';', ',')
                    .replaceAll("\\s+", " ")
                    .trim();
            return compact.matches("[A-Za-z0-9_?.:+/\\-]+")
                    ? compact
                    : "\"" + compact.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
    }

}
