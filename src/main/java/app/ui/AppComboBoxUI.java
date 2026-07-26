package app.ui;

import javax.swing.*;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicArrowButton;
import javax.swing.plaf.basic.BasicComboBoxUI;
import java.awt.*;

/** Flat combo-box delegate matching the application input surfaces. */
public final class AppComboBoxUI extends BasicComboBoxUI {
    public static ComponentUI createUI(JComponent component) {
        return new AppComboBoxUI();
    }

    @Override
    protected JButton createArrowButton() {
        BasicArrowButton arrow = new BasicArrowButton(
                SwingConstants.SOUTH,
                AppColors.color("App.control", new Color(0x343941)),
                AppColors.color("App.border", new Color(0x626873)),
                AppColors.color("ComboBox.foreground", Color.WHITE),
                AppColors.color("App.border", new Color(0x626873)));
        arrow.setBorder(BorderFactory.createEmptyBorder());
        arrow.setName("ComboBox.arrowButton");
        return arrow;
    }
}
