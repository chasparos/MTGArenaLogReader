package devtools;

import app.log.LogMessageParser;
import app.log.LogRecordFramer;
import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.RawLogEntry;
import app.replay.GameSessionsPanel;
import app.settings.ThemeService;
import com.google.gson.GsonBuilder;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/** Dedicated developer harness for deterministic Replay UI fixtures and pasted logs. */
public final class ReplayUiHarness {
    private final JFrame frame = new JFrame("Replay UI Harness");
    private final GameSessionsPanel games = new GameSessionsPanel(ignored -> { });
    private final JLabel status = new JLabel("Choose a fixture or paste an Arena log excerpt");

    private ReplayUiHarness() {
        JButton replayFixture = new JButton("Replay bundled match fixture");
        replayFixture.addActionListener(event -> replayRepositoryFixture());
        JButton draftFixture = new JButton("Replay bundled draft fixture");
        draftFixture.addActionListener(event -> replayClasspathFixture("/logs/premier-draft.log"));
        JButton paste = new JButton("Scan pasted raw log");
        paste.addActionListener(event -> openPasteDialog());
        JButton choose = new JButton("Open log file…");
        choose.addActionListener(event -> chooseLog());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        actions.add(replayFixture);
        actions.add(draftFixture);
        actions.add(paste);
        actions.add(choose);
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(new EmptyBorder(7, 8, 7, 8));
        header.add(actions, BorderLayout.WEST);
        header.add(status, BorderLayout.EAST);

        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1200, 800);
        frame.setLocationByPlatform(true);
        frame.add(header, BorderLayout.NORTH);
        frame.add(games, BorderLayout.CENTER);
    }

    public static void main(String[] args) {
        new ThemeService().applySaved();
        SwingUtilities.invokeLater(() -> new ReplayUiHarness().frame.setVisible(true));
    }

    private void replayRepositoryFixture() {
        Path fixture = Path.of("src", "test", "resources", "logs", "multigame.log");
        if (!Files.isRegularFile(fixture)) {
            showError("Run the harness from the repository root; fixture not found: " + fixture);
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(fixture)) {
            replay(reader, fixture.toString());
        } catch (IOException error) {
            showError(error.getMessage());
        }
    }

    private void replayClasspathFixture(String resource) {
        try (InputStream input = ReplayUiHarness.class.getResourceAsStream(resource)) {
            if (input == null) throw new IOException("Missing fixture " + resource);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(input, StandardCharsets.UTF_8))) {
                replay(reader, resource);
            }
        } catch (IOException error) {
            showError(error.getMessage());
        }
    }

    private void chooseLog() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) return;
        try (BufferedReader reader = Files.newBufferedReader(chooser.getSelectedFile().toPath())) {
            replay(reader, chooser.getSelectedFile().getName());
        } catch (IOException error) {
            showError(error.getMessage());
        }
    }

    private void openPasteDialog() {
        JTextArea input = new JTextArea(24, 90);
        int result = JOptionPane.showConfirmDialog(frame, new JScrollPane(input),
                "Paste raw Arena log", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (result != JOptionPane.OK_OPTION) return;
        try (BufferedReader reader = new BufferedReader(new java.io.StringReader(input.getText()))) {
            replay(reader, "pasted excerpt");
        } catch (IOException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private void replay(BufferedReader reader, String source) throws IOException {
        games.clear();
        int records = decode(reader, games::accept);
        status.setText("Replayed " + records + " records from " + source);
    }

    static int decode(BufferedReader reader, Consumer<LogMessageInterface> target) throws IOException {
        LogMessageParser parser = new LogMessageParser(
                new GsonBuilder().disableHtmlEscaping().create());
        LogRecordFramer framer = new LogRecordFramer();
        long sequence = 0;
        int records = 0;
        String line;
        while ((line = reader.readLine()) != null) {
            List<String> framed = framer.accept(line);
            for (String record : framed) {
                LogMessageInterface message = parser.parse(new RawLogEntry(
                        ++sequence, Instant.EPOCH.plusMillis(sequence), record));
                message.getModelFuture().complete(new InformationBundle());
                target.accept(message);
                records++;
            }
        }
        return records;
    }

    private void showError(String message) {
        status.setText("Replay failed");
        JOptionPane.showMessageDialog(frame, message, "Replay harness error",
                JOptionPane.ERROR_MESSAGE);
    }
}
