package app.settings;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Arrays;

/**
 * Lets the current user replace or remove the locally encrypted API key.
 */
public final class SettingsDialog extends JDialog {
    private final ApiKeyStore apiKeyStore;
    private final ThemeService themes;
    private final JPasswordField apiKey = new JPasswordField(34);
    private final JComboBox<ThemeMode> theme =
            new JComboBox<>(ThemeMode.values());
    private final JLabel status = new JLabel();

    public SettingsDialog(
            Window owner, ApiKeyStore apiKeyStore, ThemeService themes) {
        super(owner, "Settings", ModalityType.APPLICATION_MODAL);
        this.apiKeyStore = apiKeyStore;
        this.themes = themes;
        initialize();
        refreshStatus();
    }

    public void open() {
        apiKey.setText("");
        refreshStatus();
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setResizable(false);

        JLabel explanation = new JLabel(
                "<html>The key is encrypted with Windows DPAPI for your Windows account.<br>"
                        + "Leave the field empty to keep the currently stored key.</html>");

        JButton save = new JButton("Save key");
        save.addActionListener(event -> save());

        JButton remove = new JButton("Remove key");
        remove.addActionListener(event -> remove());

        JButton cancel = new JButton("Close");
        cancel.addActionListener(event -> setVisible(false));

        JPanel field = new JPanel(new BorderLayout(8, 0));
        field.add(new JLabel("OpenAI API key:"), BorderLayout.WEST);
        field.add(apiKey, BorderLayout.CENTER);

        JPanel themeField = new JPanel(new BorderLayout(8, 0));
        themeField.add(new JLabel("Theme:"), BorderLayout.WEST);
        theme.setSelectedItem(themes.selected());
        theme.addActionListener(event -> {
            ThemeMode selected = (ThemeMode) theme.getSelectedItem();
            try {
                themes.select(selected);
                pack();
                setLocationRelativeTo(getOwner());
            } catch (RuntimeException error) {
                status.setText("Could not change theme: " + error.getMessage());
            }
        });
        themeField.add(theme, BorderLayout.CENTER);

        JPanel fields = new JPanel(new GridLayout(2, 1, 0, 8));
        fields.add(themeField);
        fields.add(field);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(remove);
        actions.add(cancel);
        actions.add(save);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.add(explanation, BorderLayout.NORTH);
        root.add(fields, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout(8, 0));
        footer.add(status, BorderLayout.CENTER);
        footer.add(actions, BorderLayout.EAST);
        root.add(footer, BorderLayout.SOUTH);

        setContentPane(root);
        getRootPane().setDefaultButton(save);
        pack();
    }

    private void save() {
        char[] value = apiKey.getPassword();
        try {
            if (value.length == 0) {
                status.setText("No change made");
                return;
            }
            apiKeyStore.save(value);
            apiKey.setText("");
            refreshStatus();
        } catch (RuntimeException error) {
            status.setText("Could not save key: " + error.getMessage());
        } finally {
            Arrays.fill(value, '\0');
        }
    }

    private void remove() {
        if (!apiKeyStore.isConfigured()) return;
        int choice = JOptionPane.showConfirmDialog(
                this,
                "Remove the stored OpenAI API key?",
                "Remove API key",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);
        if (choice == JOptionPane.OK_OPTION) {
            apiKeyStore.clear();
            apiKey.setText("");
            refreshStatus();
        }
    }

    private void refreshStatus() {
        status.setText(apiKeyStore.isConfigured()
                ? "API key is configured"
                : "No API key configured");
    }
}
