package app.replay;

import app.deck.tracking.DeckTracker;
import app.deck.ui.DeckTrackerFrame;
import app.draft.tracking.DraftTracker;
import app.draft.ui.DraftAssistantFrame;
import app.model.session.MatchSession;
import app.model.log.LogMessageInterface;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
    private final BlockingQueue<LogMessageInterface> uiQueue;
    private final GameSessionsPanel gamesPanel;
    private final JLabel status = new JLabel("Running");
    private final Runnable rescanAction;
    private final Consumer<Void> closeAction;
    private final DeckTracker deckTracker;
    private final DeckTrackerFrame deckTrackerFrame;
    private final DraftTracker draftTracker;
    private final DraftAssistantFrame draftAssistantFrame;
    private final Runnable replayDraftFixtureAction;

    public MainFrame(BlockingQueue<LogMessageInterface> uiQueue, DeckTracker deckTracker,
                     DeckTrackerFrame deckTrackerFrame,
                     DraftTracker draftTracker,
                     DraftAssistantFrame draftAssistantFrame,
                     Consumer<MatchSession> coachingAction,
                     Runnable rescanAction,
                     Runnable replayDraftFixtureAction,
                     Consumer<Void> closeAction) {
        super("MTG Arena Parallel Log");
        this.uiQueue = uiQueue;
        this.rescanAction = rescanAction;
        this.closeAction = closeAction;
        this.deckTracker = deckTracker;
        this.deckTrackerFrame = deckTrackerFrame;
        this.draftTracker = draftTracker;
        this.draftAssistantFrame = draftAssistantFrame;
        this.replayDraftFixtureAction = replayDraftFixtureAction;
        this.gamesPanel = new GameSessionsPanel(coachingAction);
        initialize();
        gamesPanel.addGameSelectionListener(this::selectedGameChanged);
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setSize(1100, 760);
        setLocationByPlatform(true);

        JButton showDeckTracker = new JButton("Show deck tracker");
        showDeckTracker.addActionListener(event -> showSelectedDeckTracker());

        JButton showMatchLog = new JButton("Show match log");
        showMatchLog.addActionListener(event -> showMatchLog());

        JButton showDraftAssistant = new JButton("Show draft assistant");
        showDraftAssistant.addActionListener(event -> draftAssistantFrame.open());

        JButton replayDraftFixture = new JButton("Replay test draft");
        replayDraftFixture.setToolTipText("Replays the bundled Premier Draft fixture through the production ingestion pipeline");
        replayDraftFixture.addActionListener(event -> replayDraftFixtureAction.run());

        JButton rescan = new JButton("Clear all and rescan log file");
        rescan.addActionListener(event -> {
            gamesPanel.clear();
            deckTracker.reset();
            draftTracker.reset();
            rescanAction.run();
            status.setText("Rescanning Player.log from the beginning");
        });

        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(5, 8, 8, 8));
        footer.add(status, BorderLayout.WEST);
        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(showDeckTracker);
        actions.add(showDraftAssistant);
        actions.add(replayDraftFixture);
        actions.add(showMatchLog);
        actions.add(rescan);
        footer.add(actions, BorderLayout.EAST);

        add(gamesPanel, BorderLayout.CENTER);
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
            draftTracker.accept(message);
            gamesPanel.accept(message);
        }
        status.setText("Queued UI messages: " + uiQueue.size());
    }


    private void showSelectedDeckTracker() {
        String matchId = gamesPanel.selectedMatchId();
        int gameNumber = gamesPanel.selectedGameNumber();
        java.util.List<app.deck.model.DeckGameState> states =
                matchId == null || gameNumber <= 0
                        ? currentTimeline()
                        : deckTracker.statesForGame(matchId, gameNumber);
        deckTrackerFrame.showTimeline(states);
    }

    private void selectedGameChanged() {
        if (!deckTrackerFrame.isVisible()) return;
        String matchId = gamesPanel.selectedMatchId();
        int gameNumber = gamesPanel.selectedGameNumber();
        java.util.List<app.deck.model.DeckGameState> states =
                matchId == null || gameNumber <= 0
                        ? currentTimeline()
                        : deckTracker.statesForGame(matchId, gameNumber);
        deckTrackerFrame.selectTimeline(states);
    }

    private java.util.List<app.deck.model.DeckGameState> currentTimeline() {
        app.deck.model.DeckGameState current = deckTracker.currentState();
        return current == null
                ? java.util.List.of()
                : deckTracker.statesForGame(current.matchId(), current.gameNumber());
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

}
