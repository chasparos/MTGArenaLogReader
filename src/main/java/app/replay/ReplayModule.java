package app.replay;

import app.deck.model.DeckGameState;
import app.deck.tracking.DeckTracker;
import app.deck.ui.DeckTrackerFrame;
import app.draft.tracking.DraftTracker;
import app.draft.ui.DraftAssistantFrame;
import app.model.log.LogMessageInterface;
import app.model.session.MatchSession;
import app.ui.ApplicationModule;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.function.Consumer;

/** Production Replay module hosted by the application shell. */
public final class ReplayModule extends JPanel implements ApplicationModule, AutoCloseable {
    private final BlockingQueue<LogMessageInterface> uiQueue;
    private final GameSessionsPanel gamesPanel;
    private final JLabel status = new JLabel("Running");
    private final DeckTracker deckTracker;
    private final DeckTrackerFrame deckTrackerFrame;
    private final DraftTracker draftTracker;
    private final DraftAssistantFrame draftAssistantFrame;
    private final Runnable rescanAction;
    private final Timer queueTimer;

    public ReplayModule(BlockingQueue<LogMessageInterface> uiQueue,
                        DeckTracker deckTracker,
                        DeckTrackerFrame deckTrackerFrame,
                        DraftTracker draftTracker,
                        DraftAssistantFrame draftAssistantFrame,
                        Consumer<MatchSession> coachingAction,
                        Runnable rescanAction) {
        super(new BorderLayout());
        this.uiQueue = uiQueue;
        this.deckTracker = deckTracker;
        this.deckTrackerFrame = deckTrackerFrame;
        this.draftTracker = draftTracker;
        this.draftAssistantFrame = draftAssistantFrame;
        this.rescanAction = rescanAction;
        gamesPanel = new GameSessionsPanel(coachingAction);
        initialize();
        gamesPanel.addGameSelectionListener(this::selectedGameChanged);
        queueTimer = new Timer(100, event -> drainQueue());
        queueTimer.start();
    }

    @Override public String id() { return "replay"; }
    @Override public String displayName() { return "Replay"; }
    @Override public JComponent component() { return this; }
    @Override public String shellStatus() { return "Watching Player.log"; }

    private void initialize() {
        JButton showDeckTracker = new JButton("Show deck tracker");
        showDeckTracker.addActionListener(event -> showSelectedDeckTracker());
        JButton showMatchLog = new JButton("Show match log");
        showMatchLog.addActionListener(event -> showMatchLog());
        JButton showDraftAssistant = new JButton("Show draft assistant");
        showDraftAssistant.addActionListener(event -> draftAssistantFrame.open());
        JButton rescan = new JButton("Clear all and rescan log file");
        rescan.addActionListener(event -> {
            gamesPanel.clear();
            deckTracker.reset();
            draftTracker.reset();
            rescanAction.run();
            status.setText("Rescanning Player.log from the beginning");
        });

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(showDeckTracker);
        actions.add(showDraftAssistant);
        actions.add(showMatchLog);
        actions.add(rescan);
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBorder(new EmptyBorder(5, 8, 8, 8));
        footer.add(status, BorderLayout.WEST);
        footer.add(actions, BorderLayout.EAST);
        add(gamesPanel, BorderLayout.CENTER);
        add(footer, BorderLayout.SOUTH);
    }

    void drainQueue() {
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
        List<DeckGameState> states = matchId == null || gameNumber <= 0
                ? currentTimeline()
                : deckTracker.statesForGame(matchId, gameNumber);
        deckTrackerFrame.showTimeline(states);
    }

    private void selectedGameChanged() {
        if (!deckTrackerFrame.isVisible()) return;
        String matchId = gamesPanel.selectedMatchId();
        int gameNumber = gamesPanel.selectedGameNumber();
        List<DeckGameState> states = matchId == null || gameNumber <= 0
                ? currentTimeline()
                : deckTracker.statesForGame(matchId, gameNumber);
        deckTrackerFrame.selectTimeline(states);
    }

    private List<DeckGameState> currentTimeline() {
        DeckGameState current = deckTracker.currentState();
        return current == null
                ? List.of()
                : deckTracker.statesForGame(current.matchId(), current.gameNumber());
    }

    private void showMatchLog() {
        String text = gamesPanel.matchLogText()
                + System.lineSeparator() + System.lineSeparator()
                + "Deck tracking" + System.lineSeparator()
                + deckTracker.matchLogText();
        JTextArea area = new JTextArea(text);
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setCaretPosition(0);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(760, 520));
        JOptionPane.showMessageDialog(this, scroll, "Match Log", JOptionPane.PLAIN_MESSAGE);
    }

    @Override
    public void close() {
        queueTimer.stop();
    }
}
