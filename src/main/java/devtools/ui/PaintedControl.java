package devtools.ui;

import javax.swing.JPanel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Objects;

/**
 * Lightweight accessible action surface whose geometry and rendering are owned
 * by project code rather than a platform Look-and-Feel button delegate.
 */
public final class PaintedControl extends JPanel {
    @FunctionalInterface
    public interface Geometry {
        Shape shape(int width, int height);
    }

    @FunctionalInterface
    public interface Painter {
        void paint(Graphics2D graphics, Shape shape, State state);
    }

    public record State(boolean hovered, boolean pressed, boolean focused, boolean enabled) {
    }

    private final Geometry geometry;
    private final Painter painter;
    private final Runnable action;
    private boolean hovered;
    private boolean pressed;

    public PaintedControl(
            Dimension size,
            String accessibleName,
            String tooltip,
            Geometry geometry,
            Painter painter,
            Runnable action) {
        this.geometry = Objects.requireNonNull(geometry);
        this.painter = Objects.requireNonNull(painter);
        this.action = Objects.requireNonNull(action);
        setPreferredSize(size);
        setMinimumSize(size);
        setOpaque(false);
        setFocusable(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        setToolTipText(tooltip);
        getAccessibleContext().setAccessibleName(accessibleName);
        installInteraction();
    }

    public static Painter flatPainter(Color normal, Color hover, Color pressed) {
        return (graphics, shape, state) -> {
            Color fill = state.pressed() ? pressed : state.hovered() ? hover : normal;
            graphics.setColor(state.enabled() ? fill : SteadyArcTheme.SURFACE_RECESSED);
            graphics.fill(shape);
            if (state.focused()) {
                graphics.setColor(SteadyArcTheme.TEXT);
                graphics.draw(shape);
            }
        };
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Shape shape = geometry.shape(getWidth(), getHeight());
            painter.paint(copy, shape,
                    new State(hovered, pressed, hasFocus(), isEnabled()));
        } finally {
            copy.dispose();
        }
    }

    private void installInteraction() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent event) {
                hovered = true;
                repaint();
            }

            @Override
            public void mouseExited(MouseEvent event) {
                hovered = false;
                pressed = false;
                repaint();
            }

            @Override
            public void mousePressed(MouseEvent event) {
                if (isEnabled() && event.getButton() == MouseEvent.BUTTON1) {
                    requestFocusInWindow();
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void mouseReleased(MouseEvent event) {
                boolean activate = pressed && contains(event.getPoint());
                pressed = false;
                repaint();
                if (activate) {
                    action.run();
                }
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent event) {
                if (isEnabled() && (event.getKeyCode() == KeyEvent.VK_ENTER
                        || event.getKeyCode() == KeyEvent.VK_SPACE)) {
                    pressed = true;
                    repaint();
                }
            }

            @Override
            public void keyReleased(KeyEvent event) {
                if (pressed && (event.getKeyCode() == KeyEvent.VK_ENTER
                        || event.getKeyCode() == KeyEvent.VK_SPACE)) {
                    pressed = false;
                    repaint();
                    action.run();
                }
            }
        });
    }
}
