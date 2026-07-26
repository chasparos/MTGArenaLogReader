package app.ui;

import javax.swing.*;
import java.awt.*;

/** Semantic colors consumed by custom application UI delegates. */
public final class AppColors {
    private AppColors() {
    }

    public static Color color(String key, Color fallback) {
        Color value = UIManager.getColor(key);
        return value == null ? fallback : value;
    }

    public static Color blend(Color left, Color right, float amount) {
        float n = Math.max(0, Math.min(1, amount));
        return new Color(
                Math.round(left.getRed() * (1 - n) + right.getRed() * n),
                Math.round(left.getGreen() * (1 - n) + right.getGreen() * n),
                Math.round(left.getBlue() * (1 - n) + right.getBlue() * n));
    }
}
