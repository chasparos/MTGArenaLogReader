package app.deckplanner.ui;

import app.ui.AppColors;

import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.BiConsumer;

/** Compact dual-handle mana-value selector with discrete labels from 0 through 7+. */
final class ManaValueRangeControl extends JComponent {
    static final int MAX_BUCKET = 7;
    private static final int HANDLE_RADIUS = 8;
    private int minimum;
    private int maximum = MAX_BUCKET;
    private int activeHandle;
    private BiConsumer<Integer, Integer> listener = (minimum, maximum) -> {};

    ManaValueRangeControl() {
        setFocusable(true);
        setPreferredSize(new Dimension(320, 62));
        setMinimumSize(new Dimension(240, 62));
        setToolTipText("Drag either handle to choose a mana-value range");
        AccessibleContext accessible = getAccessibleContext();
        accessible.setAccessibleName("Mana value range");
        accessible.setAccessibleDescription("Range from zero to seven or more");

        MouseAdapter mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                requestFocusInWindow();
                activeHandle = nearestHandle(event.getX());
                updateFromMouse(event.getX());
            }
            @Override public void mouseDragged(MouseEvent event) { updateFromMouse(event.getX()); }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent event) {
                if (event.getKeyCode() == KeyEvent.VK_SPACE) {
                    activeHandle = 1 - activeHandle;
                    repaint();
                    event.consume();
                    return;
                }
                int delta = switch (event.getKeyCode()) {
                    case KeyEvent.VK_LEFT, KeyEvent.VK_DOWN -> -1;
                    case KeyEvent.VK_RIGHT, KeyEvent.VK_UP -> 1;
                    default -> 0;
                };
                if (delta != 0) {
                    setHandle(activeHandle, (activeHandle == 0 ? minimum : maximum) + delta, true);
                    event.consume();
                }
            }
        });
    }

    @Override public AccessibleContext getAccessibleContext() {
        if (accessibleContext == null) accessibleContext = new AccessibleManaValueRangeControl();
        return accessibleContext;
    }

    protected final class AccessibleManaValueRangeControl extends AccessibleJComponent {
        @Override public AccessibleRole getAccessibleRole() {
            return AccessibleRole.SLIDER;
        }
    }

    void setRange(int minimum, int maximum) {
        int nextMinimum = Math.max(0, Math.min(MAX_BUCKET, minimum));
        int nextMaximum = Math.max(nextMinimum, Math.min(MAX_BUCKET, maximum));
        if (this.minimum == nextMinimum && this.maximum == nextMaximum) return;
        this.minimum = nextMinimum;
        this.maximum = nextMaximum;
        repaint();
    }

    int minimumValue() { return minimum; }
    int maximumValue() { return maximum; }
    void setRangeListener(BiConsumer<Integer, Integer> listener) {
        this.listener = listener == null ? (minimum, maximum) -> {} : listener;
    }

    @Override protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int left = 18;
            int right = getWidth() - 18;
            int y = 23;
            Color track = AppColors.color("App.border", new Color(0x626873));
            Color active = AppColors.color("App.accent", new Color(0xC69B52));
            Color text = AppColors.color("Label.foreground", Color.WHITE);
            g.setStroke(new BasicStroke(4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            g.setColor(track);
            g.drawLine(left, y, right, y);
            g.setColor(active);
            g.drawLine(xFor(minimum), y, xFor(maximum), y);

            for (int value = 0; value <= MAX_BUCKET; value++) {
                int x = xFor(value);
                g.setStroke(new BasicStroke(1f));
                g.setColor(track);
                g.drawLine(x, y - 4, x, y + 4);
                String label = value == MAX_BUCKET ? "7+" : Integer.toString(value);
                FontMetrics metrics = g.getFontMetrics();
                g.setColor(text);
                g.drawString(label, x - metrics.stringWidth(label) / 2, y + 25);
            }
            paintHandle(g, minimum, activeHandle == 0);
            paintHandle(g, maximum, activeHandle == 1);
        } finally {
            g.dispose();
        }
    }

    private void paintHandle(Graphics2D g, int value, boolean activeHandle) {
        int x = xFor(value);
        int y = 23;
        Color fill = AppColors.color("App.controlSelected", new Color(0x765529));
        Color border = AppColors.color("App.accent", new Color(0xC69B52));
        g.setColor(fill);
        g.fillOval(x - HANDLE_RADIUS, y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
        g.setColor(border);
        g.setStroke(new BasicStroke(activeHandle && isFocusOwner() ? 3f : 2f));
        g.drawOval(x - HANDLE_RADIUS, y - HANDLE_RADIUS, HANDLE_RADIUS * 2, HANDLE_RADIUS * 2);
    }

    private int nearestHandle(int mouseX) {
        return Math.abs(mouseX - xFor(minimum)) <= Math.abs(mouseX - xFor(maximum)) ? 0 : 1;
    }

    private void updateFromMouse(int mouseX) {
        int left = 18;
        int right = Math.max(left + 1, getWidth() - 18);
        int value = Math.round((mouseX - left) * MAX_BUCKET / (float) (right - left));
        setHandle(activeHandle, value, true);
    }

    private void setHandle(int handle, int value, boolean notify) {
        value = Math.max(0, Math.min(MAX_BUCKET, value));
        if (handle == 0) minimum = Math.min(value, maximum);
        else maximum = Math.max(value, minimum);
        repaint();
        if (notify) listener.accept(minimum, maximum);
    }

    private int xFor(int value) {
        int left = 18;
        int right = Math.max(left + 1, getWidth() - 18);
        return left + Math.round((right - left) * (value / (float) MAX_BUCKET));
    }
}
