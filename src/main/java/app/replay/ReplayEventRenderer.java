package app.replay;

import app.model.event.GameEvent;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/** Lays out and paints ordinary replay events, separate from view interaction state. */
final class ReplayEventRenderer {
    interface Host {
        Font font();
        Color foreground();
        Color colorOr(String key, Color fallback);
        String contextText(GameEvent event);
        int fragmentWidth(Graphics2D graphics, ReplayFragment fragment);
        void paintFragment(Graphics2D graphics, ReplayFragment fragment,
                           int x, int y, int lineHeight, GameEvent event);
        void paintPanel(Graphics2D graphics, int y, int width, int height, boolean highlighted);
        void registerHitbox(Rectangle bounds, GameEvent event);
    }

    private static final int OUTER_PADDING = 18;
    private static final int EVENT_GAP = 9;
    private static final int CARD_PADDING = 11;
    private static final int CONTEXT_WIDTH = 190;
    private static final int RICH_LINE_HEIGHT = 38;

    private final Host host;
    private final ReplayFragmentParser fragments;

    ReplayEventRenderer(Host host, ReplayFragmentParser fragments) {
        this.host = host;
        this.fragments = fragments;
    }

    int paint(Graphics2D graphics, GameEvent event, int y, int width,
              boolean draw, boolean highlighted) {
        int contentX = OUTER_PADDING + CARD_PADDING + CONTEXT_WIDTH;
        int maxX = OUTER_PADDING + width - CARD_PADDING;
        int contentHeight = layout(graphics, event, contentX, maxX,
                y + CARD_PADDING, draw);
        int boxHeight = Math.max(graphics.getFontMetrics().getHeight(), contentHeight)
                + CARD_PADDING * 2;
        if (draw) {
            host.paintPanel(graphics, y, width, boxHeight, highlighted);
            graphics.setColor(host.colorOr("Label.disabledForeground", host.foreground()));
            graphics.setFont(host.font().deriveFont(Font.PLAIN, 12f));
            graphics.drawString(host.contextText(event), OUTER_PADDING + CARD_PADDING,
                    y + CARD_PADDING + graphics.getFontMetrics(host.font()).getAscent());
            graphics.setFont(host.font());
            layout(graphics, event, contentX, maxX, y + CARD_PADDING, true);
            host.registerHitbox(new Rectangle(OUTER_PADDING, y, width, boxHeight), event);
        }
        return y + boxHeight + EVENT_GAP;
    }

    private int layout(Graphics2D graphics, GameEvent event,
                       int startX, int maxX, int topY, boolean draw) {
        List<ReplayFragment> parsed = fragments.parse(event);
        FontMetrics metrics = graphics.getFontMetrics(host.font());
        int lineHeight = Math.max(RICH_LINE_HEIGHT, metrics.getHeight());
        int x = startX;
        int y = topY;
        for (ReplayFragment fragment : parsed) {
            int width = host.fragmentWidth(graphics, fragment);
            if (x > startX && x + width > maxX) {
                x = startX;
                y += lineHeight;
            }
            if (draw) host.paintFragment(graphics, fragment, x, y, lineHeight, event);
            x += width;
        }
        return y - topY + lineHeight;
    }
}
