package app.replay;

import app.model.game.GameKey;
import app.model.session.GameModel;
import app.model.session.GameSession;
import app.model.session.MatchSession;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.log.LogMessageInterface;
import app.projection.AbilityNameStore;
import app.routing.GameMessageRouter;
import app.export.GameTextExporter;
import app.export.MatchAiExporter;
import app.archive.MatchArchiveStore;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns one independent GameView per Arena game. Records are routed in their
 * original order, so startup replay and live updates behave identically.
 * <p><strong>Architectural role:</strong> This type belongs to the Swing presentation boundary and consumes structured models and events without owning game reconstruction.</p>
 */
public final class GameSessionsPanel extends JPanel {
    private static final Logger LOG = LoggerFactory.getLogger(GameSessionsPanel.class);
    private static final int MAX_VISIBLE_GAMES = 12;
    private final JTabbedPane gameTabs = new JTabbedPane();
    private final Map<GameKey, GameView> views = new LinkedHashMap<>();
    private final Map<GameKey, JScrollPane> tabComponents = new LinkedHashMap<>();
    private final Map<String, MatchSession> matches = new LinkedHashMap<>();
    private final GameMessageRouter router = new GameMessageRouter();
    private final GameTextExporter exporter = new GameTextExporter();
    private final MatchAiExporter aiExporter = new MatchAiExporter();
    private final MatchArchiveStore archiveStore = new MatchArchiveStore(
            Path.of(System.getProperty("user.home"), ".arena-log-viewer", "archive"),
            aiExporter);
    private final AbilityNameStore abilityNames = new AbilityNameStore();
    private final JLabel status = new JLabel("No games loaded");
    private final Consumer<MatchSession> coachingAction;

    public GameSessionsPanel() {
        this(match -> { });
    }

    public GameSessionsPanel(Consumer<MatchSession> coachingAction) {
        super(new BorderLayout());
        this.coachingAction = coachingAction;

        JButton copy = new JButton("Copy selected game");
        copy.addActionListener(event -> copySelectedGame());

        JButton copyRaw = new JButton("Copy raw game log");
        copyRaw.addActionListener(event -> copySelectedRawGame());

        JButton copyMatchForAi = new JButton("Copy match for AI");
        copyMatchForAi.setToolTipText("Copy a compact semantic reconstruction of the selected match");
        copyMatchForAi.addActionListener(event -> copySelectedMatchForAi());

        JButton coachMatch = new JButton("Coach match…");
        coachMatch.setToolTipText("Persist this match and open its local coaching conversation");
        coachMatch.addActionListener(event -> openSelectedMatchForCoaching());

        JButton closeMatch = new JButton("Close match");
        closeMatch.setToolTipText("Close every game tab belonging to the selected match");
        closeMatch.addActionListener(event -> closeSelectedMatch());

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBorder(new EmptyBorder(6, 8, 6, 8));
        toolbar.add(status, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(copy);
        actions.add(copyMatchForAi);
        actions.add(coachMatch);
        actions.add(copyRaw);
        actions.add(closeMatch);
        toolbar.add(actions, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
        add(gameTabs, BorderLayout.CENTER);
    }

    public void accept(LogMessageInterface message) {
        router.route(message).ifPresent(key -> {
            GameView view = views.computeIfAbsent(key, this::createGameView);
            view.getModel().addRawRecord(message.getRawText());
            view.accept(message);
            enforceVisibleGameLimit();
            updateStatus();
        });
    }

    public void clear() {
        views.values().forEach(GameView::clear);
        views.clear();
        tabComponents.clear();
        matches.clear();
        gameTabs.removeAll();
        status.setText("No games loaded");
    }

    public String matchLogText() {
        List<GameEvent> events = new ArrayList<>();
        views.values().forEach(view -> view.getModel().snapshot().stream()
                .filter(event -> isMatchEvent(event.getType()))
                .forEach(events::add));
        events.sort(Comparator.comparingLong(GameEvent::getSequence));
        if (events.isEmpty()) return "No match-level events have been projected.";
        StringBuilder out = new StringBuilder();
        for (GameEvent event : events) {
            out.append('#').append(event.getSequence()).append("  ")
                    .append(event.getType()).append("  ")
                    .append(event.getText() == null ? "" : event.getText())
                    .append(System.lineSeparator());
        }
        return out.toString();
    }

    private boolean isMatchEvent(GameEventType type) {
        return type == GameEventType.MATCH_STARTED
                || type == GameEventType.GAME_STARTED
                || type == GameEventType.GAME_RESULT
                || type == GameEventType.MATCH_SCORE
                || type == GameEventType.MATCH_RESULT;
    }

    private GameView createGameView(GameKey key) {
        MatchSession match = matches.computeIfAbsent(
                key.getMatchId(), matchId -> new MatchSession(matchId, abilityNames));
        GameSession game = match.game(key.getGameNumber());
        LOG.info("Created replay session for match {} game {}", key.getMatchId(), key.getGameNumber());

        GameView view = new GameView(game, abilityNames);
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        tabComponents.put(key, scrollPane);
        gameTabs.addTab(humanReadableTitle(key), scrollPane);
        gameTabs.setSelectedComponent(scrollPane);
        view.setModelChangedListener(() -> updateTabTitle(key));
        return view;
    }





    private void updateStatus() {
        status.setText(views.size() + (views.size() == 1 ? " game" : " games") + " loaded");
    }

    private void updateTabTitle(GameKey key) {
        JScrollPane component = tabComponents.get(key);
        if (component == null) return;
        int index = gameTabs.indexOfComponent(component);
        if (index >= 0) gameTabs.setTitleAt(index, humanReadableTitle(key));
    }

    private String humanReadableTitle(GameKey key) {
        MatchSession match = matches.get(key.getMatchId());
        GameView view = views.get(key);
        String localPlayer = view == null ? null : view.getModel().getOpeningHandPlayer();
        String opponent = match == null ? null : match.matchState().playerSnapshot().values().stream()
                .filter(name -> name != null && !name.isBlank())
                .filter(name -> localPlayer == null || !name.equals(localPlayer))
                .findFirst()
                .orElse(null);
        return opponent == null
                ? "Game " + key.getGameNumber()
                : "vs " + opponent + ", game " + key.getGameNumber();
    }

    private void closeSelectedMatch() {
        String matchId = selectedMatchId();
        if (matchId == null) {
            status.setText("No match selected");
            return;
        }
        removeMatch(matchId, false);
    }

    private void enforceVisibleGameLimit() {
        while (views.size() > MAX_VISIBLE_GAMES && !matches.isEmpty()) {
            String oldestMatchId = matches.keySet().iterator().next();
            removeMatch(oldestMatchId, true);
        }
    }

    private void removeMatch(String matchId, boolean archive) {
        MatchSession match = matches.get(matchId);
        if (match == null) return;
        if (archive) {
            try {
                archiveStore.archive(match);
            } catch (IOException error) {
                LOG.warn("Could not archive match {}", matchId, error);
            }
        }

        List<GameKey> keys = views.keySet().stream()
                .filter(key -> Objects.equals(key.getMatchId(), matchId))
                .toList();
        for (GameKey key : keys) {
            GameView view = views.remove(key);
            if (view != null) view.clear();
            JScrollPane component = tabComponents.remove(key);
            if (component != null) gameTabs.remove(component);
        }
        matches.remove(matchId);
        updateStatus();
    }

    public void addGameSelectionListener(Runnable listener) {
        gameTabs.addChangeListener(event -> listener.run());
    }

    public String selectedMatchId() {
        GameView view = selectedGameView();
        return view == null ? null : view.getModel().getMatchId();
    }

    public int selectedGameNumber() {
        GameView view = selectedGameView();
        return view == null ? 0 : view.getModel().getGameNumber();
    }

    private GameView selectedGameView() {
        Component selected = gameTabs.getSelectedComponent();
        if (!(selected instanceof JScrollPane scrollPane)
                || !(scrollPane.getViewport().getView() instanceof GameView view)) {
            return null;
        }
        return view;
    }

    private void copySelectedGame() {
        Component selected = gameTabs.getSelectedComponent();
        if (!(selected instanceof JScrollPane scrollPane) ||
                !(scrollPane.getViewport().getView() instanceof GameView view)) {
            status.setText("No game selected");
            return;
        }

        String text = exporter.export(view.getModel());
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        status.setText("Copied Game " + view.getModel().getGameNumber() +
                " — " + view.getModel().snapshot().size() + " events");
    }



    private void openSelectedMatchForCoaching() {
        GameView view = selectedGameView();
        if (view == null) {
            status.setText("No match selected");
            return;
        }

        MatchSession match = matches.get(view.getModel().getMatchId());
        if (match == null) {
            status.setText("Selected match is unavailable");
            return;
        }

        coachingAction.accept(match);
        status.setText("Saved match " + shortMatchId(view.getModel().getMatchId())
                + " for coaching");
    }

    private void copySelectedMatchForAi() {
        GameView view = selectedGameView();
        if (view == null) {
            status.setText("No match selected");
            return;
        }

        MatchSession match = matches.get(view.getModel().getMatchId());
        if (match == null) {
            status.setText("Selected match is unavailable");
            return;
        }

        String text = aiExporter.export(match);
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        int gameCount = match.gameSnapshot().size();
        status.setText("Copied compact AI report for match "
                + shortMatchId(view.getModel().getMatchId())
                + " — " + gameCount + (gameCount == 1 ? " game" : " games"));
    }

    private String shortMatchId(String matchId) {
        if (matchId == null || matchId.isBlank()) return "unknown";
        return matchId.length() <= 8 ? matchId : matchId.substring(0, 8);
    }

    private void copySelectedRawGame() {
        Component selected = gameTabs.getSelectedComponent();
        if (!(selected instanceof JScrollPane scrollPane) ||
                !(scrollPane.getViewport().getView() instanceof GameView view)) {
            status.setText("No game selected");
            return;
        }

        String text = exporter.exportRaw(view.getModel());
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(text), null);
        status.setText("Copied raw log for Game " + view.getModel().getGameNumber() +
                " — " + view.getModel().rawRecordSnapshot().size() + " records");
    }
}
