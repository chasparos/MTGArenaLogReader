package app.ui;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

/** Shell adapter for a singleton production feature that still owns a secondary window. */
public final class SecondaryWindowModule extends JPanel implements ApplicationModule {
    private final String id;
    private final String displayName;
    private final Runnable openAction;

    public SecondaryWindowModule(String id, String displayName, String description,
                                 Runnable openAction) {
        super(new GridBagLayout());
        this.id = requireText(id, "id");
        this.displayName = requireText(displayName, "displayName");
        this.openAction = Objects.requireNonNull(openAction, "openAction");

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(
                        AppColors.color("App.border", new Color(0x555C66)), 1, true),
                BorderFactory.createEmptyBorder(22, 26, 22, 26)));
        JLabel title = new JLabel(displayName);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        JLabel detail = new JLabel(description, SwingConstants.CENTER);
        detail.setForeground(AppColors.color("App.textMuted", new Color(0xAEB5BF)));
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton open = new JButton("Open " + displayName);
        open.setAlignmentX(Component.CENTER_ALIGNMENT);
        open.addActionListener(event -> openAction.run());
        card.add(title);
        card.add(Box.createVerticalStrut(8));
        card.add(detail);
        card.add(Box.createVerticalStrut(16));
        card.add(open);
        add(card);
    }

    @Override public String id() { return id; }
    @Override public String displayName() { return displayName; }
    @Override public JComponent component() { return this; }
    @Override public String shellStatus() { return displayName + " opened in its companion window"; }

    @Override
    public void activated() {
        openAction.run();
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " is required");
        return value.strip();
    }
}
