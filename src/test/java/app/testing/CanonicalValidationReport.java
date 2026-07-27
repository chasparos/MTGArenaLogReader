package app.testing;

import app.model.event.GameEvent;
import app.model.session.GameModel;

import java.util.List;
import java.util.Objects;

/**
 * Deterministic coverage summary for the canonical raw-log validation fixture.
 *
 * <p>The report is intentionally descriptive. It records which semantic categories
 * survived projection without assigning strategic value to any event.</p>
 */
record CanonicalValidationReport(
        int rawRecords,
        long turns,
        long snapshots,
        long zoneTransitions,
        long damageEvents,
        long lifeChangeEvents,
        long targetEvents,
        long abilityEvents,
        long combatEvents,
        long resultEvents) {

    static CanonicalValidationReport from(GameModel game) {
        List<GameEvent> events = game.snapshot();
        return new CanonicalValidationReport(
                game.rawRecordSnapshot().size(),
                events.stream().map(GameEvent::getTurnNumber).filter(Objects::nonNull).distinct().count(),
                events.stream().filter(event -> !event.getTurnSnapshot().isEmpty()).count(),
                events.stream().filter(event -> event.getZoneTransition() != null).count(),
                events.stream().filter(event -> event.getPermanentDamage() != null).count(),
                events.stream().filter(event -> event.getPlayerLifeChange() != null).count(),
                events.stream().filter(event -> event.getTargetObservation() != null).count(),
                events.stream().filter(event -> event.getAbility() != null).count(),
                events.stream().filter(event -> !event.getAttackers().isEmpty() || !event.getBlockers().isEmpty()).count(),
                events.stream().filter(event -> event.getGameResult() != null).count());
    }

    String render() {
        return """
                Canonical Validation Report
                rawRecords=%d
                turns=%d
                snapshots=%d
                zoneTransitions=%d
                damageEvents=%d
                lifeChangeEvents=%d
                targetEvents=%d
                abilityEvents=%d
                combatEvents=%d
                resultEvents=%d
                """.formatted(
                rawRecords,
                turns,
                snapshots,
                zoneTransitions,
                damageEvents,
                lifeChangeEvents,
                targetEvents,
                abilityEvents,
                combatEvents,
                resultEvents);
    }
}
