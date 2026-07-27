package app.replay;

import app.model.event.GameEvent;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class ReplayEventRendererTest {
    @Test
    void measuresWithoutRegisteringAndRegistersOnlyWhenPainting() {
        List<GameEvent> registered = new ArrayList<>();
        ReplayEventRenderer renderer = new ReplayEventRenderer(
                host(registered), new ReplayFragmentParser());
        GameEvent event = new GameEvent();
        event.setText("Alice draws a card");
        Graphics2D graphics = new BufferedImage(
                600, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));

        int measuredBottom = renderer.paint(graphics, event, 10, 500, false, false);
        assertEquals(0, registered.size());

        int paintedBottom = renderer.paint(graphics, event, 10, 500, true, false);
        assertEquals(measuredBottom, paintedBottom);
        assertSame(event, registered.get(0));
        graphics.dispose();
    }

    private static ReplayEventRenderer.Host host(List<GameEvent> registered) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return new ReplayEventRenderer.Host() {
            @Override public Font font() { return font; }
            @Override public Color foreground() { return Color.BLACK; }
            @Override public Color colorOr(String key, Color fallback) { return fallback; }
            @Override public String contextText(GameEvent event) { return "Turn 1"; }
            @Override public int fragmentWidth(
                    Graphics2D graphics, ReplayFragment fragment) {
                return 80;
            }
            @Override public void paintFragment(
                    Graphics2D graphics, ReplayFragment fragment,
                    int x, int y, int lineHeight, GameEvent event) {
            }
            @Override public void paintPanel(
                    Graphics2D graphics, int y, int width,
                    int height, boolean highlighted) {
            }
            @Override public void registerHitbox(Rectangle bounds, GameEvent event) {
                registered.add(event);
            }
        };
    }
}
