package app.replay;

import app.model.event.GameEvent;
import app.model.game.PlayerTurnSnapshot;
import org.junit.jupiter.api.Test;

import java.awt.*;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TurnSnapshotRendererTest {
    @Test
    void calculatesSnapshotHeightWithoutPainting() {
        TurnSnapshotRenderer renderer = new TurnSnapshotRenderer(new TestHost());
        GameEvent event = new GameEvent();
        PlayerTurnSnapshot player = new PlayerTurnSnapshot();
        player.setPlayerName("Alice");
        player.setLifeTotal(20);
        player.setHandSize(7);
        event.getTurnSnapshot().add(player);

        BufferedImage image = new BufferedImage(
                800, 600, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            int nextY = renderer.paint(graphics, event, 20, 700, false);
            assertTrue(nextY > 20);
        } finally {
            graphics.dispose();
        }
    }

    private static final class TestHost implements TurnSnapshotRenderer.Host {
        private final Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        @Override public Font font() { return font; }
        @Override public Color foreground() { return Color.BLACK; }
        @Override public Color colorOr(String key, Color fallback) { return fallback; }
        @Override public Color blend(Color first, Color second, float amount) {
            return first;
        }
        @Override public boolean paintSvg(
                Graphics2D graphics, String resource,
                int x, int y, int width, int height) {
            return false;
        }
        @Override public int fragmentWidth(
                Graphics2D graphics, ReplayFragment fragment) {
            return 120;
        }
        @Override public void paintFragment(
                Graphics2D graphics, ReplayFragment fragment,
                int x, int topY, int lineHeight, GameEvent event) {}
        @Override public void paintPanel(
                Graphics2D graphics, int y, int width,
                int height, boolean snapshot) {}
    }
}
