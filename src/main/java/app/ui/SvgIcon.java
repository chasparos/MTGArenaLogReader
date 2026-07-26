package app.ui;

import app.replay.SvgAssetRenderer;

import javax.swing.*;
import java.awt.*;

/** Theme-tinted SVG icon for compact application controls. */
public final class SvgIcon implements Icon {
    private static final SvgAssetRenderer SVG = new SvgAssetRenderer();
    private final String resource;
    private final int size;

    public SvgIcon(String resource, int size) {
        this.resource = resource;
        this.size = size;
    }

    @Override
    public void paintIcon(
            Component component, Graphics graphics, int x, int y) {
        Color color = component.isEnabled()
                ? component.getForeground()
                : AppColors.color(
                        "Button.disabledText", new Color(0x888E97));
        SVG.paintTinted((Graphics2D) graphics, resource,
                x, y, size, size, color);
    }

    @Override public int getIconWidth() {
        return size;
    }

    @Override public int getIconHeight() {
        return size;
    }
}
