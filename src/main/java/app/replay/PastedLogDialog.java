package app.replay;

import app.log.PastedLogScanner;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/** Modal editor used to submit an isolated raw Arena log excerpt. */
final class PastedLogDialog extends JDialog {
    private final JTextArea textArea = new JTextArea(28, 100);
    private final JLabel status = new JLabel("Paste a complete match excerpt or one or more Arena JSON records.");
    private final JButton scanButton = new JButton("Scan pasted log");
    private final JButton clearButton = new JButton("Clear text");
    private final JButton closeButton = new JButton("Close");

    private final Runnable clearCurrentState;
    private final Function<String, CompletionStage<PastedLogScanner.ScanResult>> scanAction;

    PastedLogDialog(Window owner,
                    Runnable clearCurrentState,
                    Function<String, CompletionStage<PastedLogScanner.ScanResult>> scanAction) {
        super(owner, "Scan pasted raw log", ModalityType.APPLICATION_MODAL);
        this.clearCurrentState = Objects.requireNonNull(clearCurrentState, "clearCurrentState");
        this.scanAction = Objects.requireNonNull(scanAction, "scanAction");
        initialize();
    }

    void open() {
        setLocationRelativeTo(getOwner());
        setVisible(true);
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        textArea.setLineWrap(false);
        textArea.setTabSize(2);
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setPreferredSize(new Dimension(920, 560));

        JPanel content = new JPanel(new BorderLayout(8, 8));
        content.setBorder(new EmptyBorder(10, 10, 10, 10));
        content.add(scrollPane, BorderLayout.CENTER);

        JPanel options = new JPanel(new BorderLayout(8, 8));
        options.add(status, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttons.add(clearButton);
        buttons.add(closeButton);
        buttons.add(scanButton);
        options.add(buttons, BorderLayout.SOUTH);
        content.add(options, BorderLayout.SOUTH);
        setContentPane(content);

        clearButton.addActionListener(event -> {
            textArea.setText("");
            textArea.requestFocusInWindow();
            status.setText("Paste a complete match excerpt or one or more Arena JSON records.");
        });
        closeButton.addActionListener(event -> dispose());
        scanButton.addActionListener(event -> submit());
        getRootPane().setDefaultButton(scanButton);

        pack();
    }

    private void submit() {
        String text = textArea.getText();
        if (text == null || text.isBlank()) {
            status.setText("Nothing to scan.");
            textArea.requestFocusInWindow();
            return;
        }

        clearCurrentState.run();
        setBusy(true);
        status.setText("Framing and queueing pasted records…");

        CompletionStage<PastedLogScanner.ScanResult> stage;
        try {
            stage = scanAction.apply(text);
        } catch (Throwable error) {
            complete(null, error);
            return;
        }
        stage.whenComplete((result, error) ->
                SwingUtilities.invokeLater(() -> complete(result, error)));
    }

    private void complete(PastedLogScanner.ScanResult result, Throwable error) {
        setBusy(false);
        if (error != null) {
            status.setText("Scan failed: " + rootMessage(error));
            return;
        }
        status.setText("Queued " + result.queuedRecords()
                + " relevant records from " + result.physicalLines() + " pasted lines.");
    }

    private void setBusy(boolean busy) {
        scanButton.setEnabled(!busy);
        clearButton.setEnabled(!busy);
        closeButton.setEnabled(!busy);
        textArea.setEditable(!busy);
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        return message == null || message.isBlank()
                ? current.getClass().getSimpleName() : message;
    }
}
