package app.settings;

import app.ui.AppButtonUI;
import app.ui.AppComboBoxUI;
import app.ui.AppTabbedPaneUI;
import app.ui.AppScrollBarUI;
import app.ui.AppSplitPaneUI;
import app.ui.RoundedBorder;

import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.BorderUIResource;
import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Properties;
import java.util.prefs.Preferences;

/**
 * Applies and persists the application look-and-feel. Palette values remain
 * data in theme property resources rather than being spread through dialogs.
 */
public final class ThemeService {
    private static final String PREFERENCE = "theme";
    private final Preferences preferences =
            Preferences.userRoot().node("arena-log-viewer/ui");

    public ThemeMode selected() {
        String stored = preferences.get(PREFERENCE, ThemeMode.SYSTEM.name());
        try {
            return ThemeMode.valueOf(stored.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException error) {
            return ThemeMode.SYSTEM;
        }
    }

    public void applySaved() {
        apply(selected(), false);
    }

    public void select(ThemeMode mode) {
        ThemeMode selected = mode == null ? ThemeMode.SYSTEM : mode;
        preferences.put(PREFERENCE, selected.name());
        apply(selected, true);
    }

    private void apply(ThemeMode mode, boolean refreshWindows) {
        try {
            clearApplicationPalette();
            if (mode == ThemeMode.SYSTEM) {
                UIManager.setLookAndFeel(
                        UIManager.getSystemLookAndFeelClassName());
                installSystemSemanticColors();
            } else {
                UIManager.setLookAndFeel(
                        UIManager.getCrossPlatformLookAndFeelClassName());
                applyPalette(loadPalette(mode));
            }
            installDesignSystem();
            if (refreshWindows) refreshWindows();
        } catch (Exception error) {
            throw new IllegalStateException(
                    "Could not apply " + mode + " theme", error);
        }
    }

    private void clearApplicationPalette() {
        for (ThemeMode paletteMode : new ThemeMode[]{
                ThemeMode.LIGHT, ThemeMode.DARK}) {
            for (String key : loadPalette(paletteMode).stringPropertyNames()) {
                UIManager.put(key, null);
            }
        }
    }

    private void installSystemSemanticColors() {
        Color panel = UIManager.getColor("Panel.background");
        if (panel == null) panel = new Color(0xF2F2F2);
        Color text = UIManager.getColor("Label.foreground");
        if (text == null) text = Color.DARK_GRAY;
        boolean dark = panel.getRed() + panel.getGreen() + panel.getBlue() < 360;
        UIManager.put("App.surface", new ColorUIResource(panel));
        UIManager.put("App.surfaceRaised", new ColorUIResource(
                dark ? panel.brighter() : Color.WHITE));
        UIManager.put("App.control", new ColorUIResource(
                dark ? panel.brighter() : new Color(0xF7F8FA)));
        UIManager.put("App.controlHover", new ColorUIResource(
                dark ? panel.brighter().brighter() : new Color(0xE9EEF4)));
        UIManager.put("App.controlPressed", new ColorUIResource(
                dark ? new Color(0x283F56) : new Color(0xD7E4F0)));
        UIManager.put("App.controlDisabled", new ColorUIResource(
                dark ? panel.darker() : new Color(0xEEEEEE)));
        UIManager.put("App.border", new ColorUIResource(
                dark ? panel.brighter().brighter() : new Color(0xAAB2BC)));
        UIManager.put("App.focus", new ColorUIResource(new Color(0x3978B8)));
        UIManager.put("App.accent", new ColorUIResource(new Color(0x3978B8)));
        UIManager.put("Button.disabledText", new ColorUIResource(
                dark ? text.darker() : new Color(0x9A9A9A)));
        UIManager.put("App.scrollTrack", new ColorUIResource(
                dark ? panel.darker() : new Color(0xEDF0F3)));
        UIManager.put("App.scrollThumb", new ColorUIResource(
                dark ? panel.brighter().brighter() : new Color(0xA5ADB7)));
        UIManager.put("App.scrollThumbHover", new ColorUIResource(
                dark ? text.darker() : new Color(0x7D8793)));
        UIManager.put("App.divider", new ColorUIResource(
                dark ? panel.brighter() : new Color(0xE4E8EC)));
        UIManager.put("App.dividerGrip", new ColorUIResource(
                dark ? text.darker() : new Color(0x89929D)));
        UIManager.put("App.chartBar", new ColorUIResource(
                new Color(0xD2A83D)));
        UIManager.put("App.chartBarHighlight", new ColorUIResource(
                new Color(0xE5C15E)));
        UIManager.put("App.textMuted", new ColorUIResource(
                dark ? text.darker() : new Color(0x626A74)));
    }

    Properties loadPalette(ThemeMode mode) {
        String resource = "/themes/"
                + mode.name().toLowerCase(Locale.ROOT) + ".properties";
        try (InputStream input = ThemeService.class.getResourceAsStream(resource)) {
            if (input == null) {
                throw new IllegalStateException("Missing theme " + resource);
            }
            Properties result = new Properties();
            result.load(input);
            return result;
        } catch (IOException error) {
            throw new IllegalStateException("Could not read " + resource, error);
        }
    }

    private void applyPalette(Properties palette) {
        for (String key : palette.stringPropertyNames()) {
            UIManager.put(key, new ColorUIResource(
                    Color.decode(palette.getProperty(key).strip())));
        }
    }

    private void installDesignSystem() {
        UIManager.put("ButtonUI", AppButtonUI.class.getName());
        UIManager.put("ToggleButtonUI", AppButtonUI.class.getName());
        UIManager.put("TabbedPaneUI", AppTabbedPaneUI.class.getName());
        UIManager.put("ComboBoxUI", AppComboBoxUI.class.getName());
        UIManager.put("ScrollBarUI", AppScrollBarUI.class.getName());
        UIManager.put("SplitPaneUI", AppSplitPaneUI.class.getName());
        UIManager.put("SplitPane.border",
                new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0));
        UIManager.put("SplitPaneDivider.border",
                new BorderUIResource.EmptyBorderUIResource(0, 0, 0, 0));

        BorderUIResource fieldBorder = new BorderUIResource(
                new RoundedBorder(9, new Insets(5, 8, 5, 8)));
        UIManager.put("TextField.border", fieldBorder);
        UIManager.put("PasswordField.border", fieldBorder);
        UIManager.put("FormattedTextField.border", fieldBorder);
        UIManager.put("ComboBox.border", new BorderUIResource(
                new RoundedBorder(9, new Insets(2, 7, 2, 4))));
        UIManager.put("ScrollPane.border", new BorderUIResource(
                new RoundedBorder(12, new Insets(1, 1, 1, 1))));
        UIManager.put("TitledBorder.border", new BorderUIResource(
                new RoundedBorder(12, new Insets(8, 8, 8, 8))));
    }

    private void refreshWindows() {
        for (Window window : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(window);
            window.invalidate();
            window.validate();
            window.repaint();
        }
    }
}
