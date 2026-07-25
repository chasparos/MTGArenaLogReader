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
    private final JPasswordField apiKey = new JPasswordField(34);
    private final JLabel status = new JLabel();

    public SettingsDialog(Window owner, ApiKeyStore apiKeyStore) {
        super(owner, "Settings", ModalityType.APPLICATION_MODAL);
        this.apiKeyStore = apiKeyStore;
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

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(remove);
        actions.add(cancel);
        actions.add(save);

        JPanel root = new JPanel(new BorderLayout(0, 10));
        root.setBorder(new EmptyBorder(14, 14, 14, 14));
        root.add(explanation, BorderLayout.NORTH);
        root.add(field, BorderLayout.CENTER);

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
