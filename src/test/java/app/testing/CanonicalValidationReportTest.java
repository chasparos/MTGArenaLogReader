package app.testing;

import app.model.event.GameEvent;
import app.model.game.PermanentDamage;
import app.model.game.PlayerLifeChange;
import app.model.session.GameModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CanonicalValidationReportTest {
    @Test
    void countsPlayerAndPlaneswalkerDamageWithoutCountingOtherLifeChanges() {
        GameModel game = new GameModel();
        game.setMatchId("match");
        game.setGameNumber(1);

        GameEvent playerDamage = new GameEvent();
        playerDamage.setPlayerLifeChange(new PlayerLifeChange(
                PlayerLifeChange.Kind.DAMAGE, 1, "Player", 3, 20, 17, 10L, "Source"));
        game.addEvents(List.of(playerDamage));

        GameEvent planeswalkerDamage = new GameEvent();
        planeswalkerDamage.setPermanentDamage(new PermanentDamage(
                20L, "Planeswalker", 2, 11L, "Attacker"));
        game.addEvents(List.of(planeswalkerDamage));

        GameEvent lifeLoss = new GameEvent();
        lifeLoss.setPlayerLifeChange(new PlayerLifeChange(
                PlayerLifeChange.Kind.LIFE_LOSS, 1, "Player", 1, 17, 16, null, null));
        game.addEvents(List.of(lifeLoss));

        CanonicalValidationReport report = CanonicalValidationReport.from(game);

        assertEquals(2, report.damageEvents());
        assertEquals(2, report.lifeChangeEvents());
    }
}
