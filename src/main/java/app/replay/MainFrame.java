package app.replay;

import app.deck.tracking.DeckTracker;
import app.deck.ui.DeckTrackerFrame;
import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.ModelObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/**
 * Implements MainFrame in the Swing replay presentation layer.
 *
 * <p>It consumes per-game models and immutable semantic events produced by projection.</p>
 *
 * <p>It must not interpret raw GRE messages or mutate canonical game state.</p>
 * <p><strong>Architectural role:</strong> This type belongs to the Swing presentation boundary and consumes structured models and events without owning game reconstruction.</p>
 */
public final class MainFrame extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final BlockingQueue<LogMessageInterface> uiQueue;
    private final JTextArea textArea = new JTextArea();
    private final GameSessionsPanel gamesPanel = new GameSessionsPanel();
    private final JLabel status = new JLabel("Running");
    private final Consumer<Void> closeAction;
    private final DeckTracker deckTracker;
    private final DeckTrackerFrame deckTrackerFrame;

    public MainFrame(BlockingQueue<LogMessageInterface> uiQueue, DeckTracker deckTracker,
                     DeckTrackerFrame deckTrackerFrame, Consumer<Void> closeAction) {
        super("MTG Arena Parallel Log");
        this.uiQueue = uiQueue;
        this.closeAction = closeAction;
        this.deckTracker = deckTracker;
        this.deckTrackerFrame = deckTrackerFrame;
        initialize();
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 760);
        setLocationByPlatform(true);

        textArea.setEditable(true);
        textArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        textArea.setLineWrap(false);

        JButton showDeckTracker = new JButton("Show deck tracker");
        showDeckTracker.addActionListener(event -> showSelectedDeckTracker());

        JButton showMatchLog = new JButton("Show match log");
        showMatchLog.addActionListener(event -> showMatchLog());

        JButton clearRaw = new JButton("Clear raw log");
        clearRaw.addActionListener(event -> textArea.setText(""));

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(5, 8, 8, 8));
        footer.add(status, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(showDeckTracker);
        actions.add(showMatchLog);
        actions.add(clearRaw);
        footer.add(actions, BorderLayout.EAST);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Games", gamesPanel);
        tabs.addTab("Raw log", new JScrollPane(textArea));
        add(tabs, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);

        Timer timer = new Timer(100, event -> drainQueue());
        timer.start();

        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                timer.stop();
                closeAction.accept(null);
            }
        });
    }

    private void drainQueue() {
        List<LogMessageInterface> batch = new ArrayList<>(256);
        uiQueue.drainTo(batch, 256);
        for (LogMessageInterface message : batch) {
            deckTracker.accept(message);
            gamesPanel.accept(message);
            appendBase(message);
            message.getModelFuture().whenComplete((model, error) ->
                    SwingUtilities.invokeLater(() -> appendEnrichment(message, model, error)));
        }
        status.setText("Queued UI messages: " + uiQueue.size());
    }


    private void showSelectedDeckTracker() {
        String matchId = gamesPanel.selectedMatchId();
        int gameNumber = gamesPanel.selectedGameNumber();
        app.deck.model.DeckGameState state = matchId == null || gameNumber <= 0
                ? deckTracker.currentState()
                : deckTracker.stateForGame(matchId, gameNumber);
        deckTrackerFrame.showState(state);
    }

    private void showMatchLog() {
        String text = gamesPanel.matchLogText()
                + System.lineSeparator()
                + System.lineSeparator()
                + "Deck tracking"
                + System.lineSeparator()
                + deckTracker.matchLogText();
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(760, 520));
        JOptionPane.showMessageDialog(this, scroll, "Match Log", JOptionPane.PLAIN_MESSAGE);
    }

    private void appendBase(LogMessageInterface message) {
        textArea.append("[%s] #%d %-6s %s%n".formatted(
                TIME.format(message.getTimestamp()),
                message.getSequence(),
                message.getCategory(),
                message.getDisplayText()));
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    private void appendEnrichment(LogMessageInterface message, ModelObject model, Throwable error) {
        if (error != null) {
            textArea.append("           ↳ enrichment failed: " + error.getMessage() + "\n");
            return;
        }
        if (!(model instanceof InformationBundle bundle) || bundle.getCards().isEmpty()) {
            return;
        }
        bundle.getCards().forEach((id, card) -> textArea.append(
                "           ↳ %d = %s | %s | %s%n".formatted(
                        id,
                        card.getName(),
                        nullToEmpty(card.getManaCost()),
                        nullToEmpty(card.getTypeLine()))));
        textArea.setCaretPosition(textArea.getDocument().getLength());
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
