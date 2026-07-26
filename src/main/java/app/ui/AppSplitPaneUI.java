package app.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import java.awt.*;

/** Flat split-pane divider with a small semantic grip. */
public final class AppSplitPaneUI extends BasicSplitPaneUI {
    public static ComponentUI createUI(JComponent component) {
        return new AppSplitPaneUI();
    }

    @Override
    protected void installDefaults() {
        super.installDefaults();
        splitPane.setDividerSize(9);
        splitPane.setBorder(BorderFactory.createEmptyBorder());
        splitPane.setOneTouchExpandable(false);
        splitPane.setContinuousLayout(true);
    }

    @Override
    public BasicSplitPaneDivider createDefaultDivider() {
        BasicSplitPaneDivider divider = new BasicSplitPaneDivider(this) {
            @Override
            public void paint(Graphics graphics) {
                Graphics2D g = (Graphics2D) graphics.create();
                try {
                    g.setColor(AppColors.color(
                            "App.divider", new Color(0x343941)));
                    g.fillRect(0, 0, getWidth(), getHeight());
                    g.setColor(AppColors.color(
                            "App.dividerGrip", new Color(0x747C87)));
                    if (orientation == JSplitPane.HORIZONTAL_SPLIT) {
                        int x = getWidth() / 2;
                        int start = getHeight() / 2 - 8;
                        for (int offset = 0; offset < 5; offset++) {
                            g.fillOval(x - 1, start + offset * 4, 2, 2);
                        }
                    } else {
                        int y = getHeight() / 2;
                        int start = getWidth() / 2 - 8;
                        for (int offset = 0; offset < 5; offset++) {
                            g.fillOval(start + offset * 4, y - 1, 2, 2);
                        }
                    }
                } finally {
                    g.dispose();
                }
            }
        };
        divider.setBorder(BorderFactory.createEmptyBorder());
        return divider;
    }
}
