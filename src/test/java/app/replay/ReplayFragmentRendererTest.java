package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayFragmentRendererTest {
    @Test
    void measuresTextManaKeywordAndCardFragments() {
        ReplayFragmentRenderer renderer = renderer(new ArrayList<>());
        Graphics2D graphics = graphics();
        CardInfo card = new CardInfo();
        card.setName("Lightning Bolt");
        card.setManaCost("{R}");
        card.setTypeLine("Instant");

        assertTrue(renderer.width(graphics, new TextFragment("casts ")) > 0);
        assertTrue(renderer.width(graphics, new ManaFragment("R")) > 0);
        assertTrue(renderer.width(
                graphics, new KeywordFragment("Flying", "Flying")) > 0);
        assertTrue(renderer.width(
                graphics, new CardFragment(card, card.getName(), "", null)) > 0);
        graphics.dispose();
    }

    @Test
    void cardPaintingRegistersHitboxOnlyWhenRequested() {
        List<Rectangle> hitboxes = new ArrayList<>();
        ReplayFragmentRenderer renderer = renderer(hitboxes);
        Graphics2D graphics = graphics();
        CardInfo card = new CardInfo();
        card.setName("Test Card");
        CardFragment fragment = new CardFragment(card, card.getName(), "", null);

        renderer.paint(graphics, fragment, 20, 10, 38, new GameEvent(), false);
        assertEquals(0, hitboxes.size());

        renderer.paint(graphics, fragment, 20, 10, 38, new GameEvent(), true);
        assertEquals(1, hitboxes.size());
        graphics.dispose();
    }

    private static ReplayFragmentRenderer renderer(List<Rectangle> hitboxes) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, 13);
        return new ReplayFragmentRenderer(new ReplayFragmentRenderer.Host() {
            @Override public Font font() { return font; }
            @Override public Color foreground() { return Color.BLACK; }
            @Override public Color colorOr(String key, Color fallback) {
                return fallback;
            }
            @Override public boolean isHovered(Rectangle bounds) { return false; }
            @Override public void registerHitbox(
                    Rectangle bounds, CardInfo card, GameEvent event,
                    BoardPermanentSnapshot permanent) {
                hitboxes.add(bounds);
            }
        });
    }

    private static Graphics2D graphics() {
        Graphics2D graphics = new BufferedImage(
                600, 120, BufferedImage.TYPE_INT_ARGB).createGraphics();
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        return graphics;
    }
}
