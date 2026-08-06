package devtools.ui;

import java.awt.Color;
import java.awt.Font;

/** Shared visual identity for Steady Arc desktop surfaces. */
public final class SteadyArcTheme {
    public static final Color SURFACE = new Color(0x5A3049);
    public static final Color SURFACE_RAISED = new Color(0x6A3B55);
    public static final Color SURFACE_RECESSED = new Color(0x48263B);
    public static final Color GOLD = new Color(0xE77F4F);
    public static final Color GOLD_HOVER = new Color(0xF29464);
    public static final Color TEXT = new Color(0xFFF5E8);
    public static final Color TEXT_MUTED = new Color(0xD8C5CF);
    public static final Color BORDER = new Color(0x3D2032);
    public static final Color SUCCESS = new Color(0x9BCF9B);
    public static final Color WARNING = new Color(0xF4C36B);

    private SteadyArcTheme() {
    }

    public static Font semibold(Font base, float size) {
        return base.deriveFont(Font.BOLD, size);
    }
}
