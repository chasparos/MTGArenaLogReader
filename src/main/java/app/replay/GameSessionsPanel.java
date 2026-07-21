package app.replay;

import app.model.game.GameKey;
import app.model.session.GameModel;
import app.model.session.GameSession;
import app.model.session.MatchSession;
import app.model.log.LogMessageInterface;
import app.projection.AbilityNameStore;
import app.routing.GameMessageRouter;
import app.export.GameTextExporter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns one independent GameView per Arena game. Records are routed in their
 * original order, so startup replay and live updates behave identically.
 * <p><strong>Architectural role:</strong> This type belongs to the Swing presentation boundary and consumes structured models and events without owning game reconstruction.</p>
 */
public final class GameSessionsPanel extends JPanel {
    private final JTabbedPane gameTabs = new JTabbedPane();
    private final Map<GameKey, GameView> views = new LinkedHashMap<>();
    private final Map<String, MatchSession> matches = new LinkedHashMap<>();
    private final GameMessageRouter router = new GameMessageRouter();
    private final GameTextExporter exporter = new GameTextExporter();
    private final AbilityNameStore abilityNames = new AbilityNameStore();
    private final JLabel status = new JLabel("No games loaded");

    public GameSessionsPanel() {
        super(new BorderLayout());

        JButton copy = new JButton("Copy selected game");
        copy.addActionListener(event -> copySelectedGame());

        JButton copyRaw = new JButton("Copy raw game log");
        copyRaw.addActionListener(event -> copySelectedRawGame());

        JButton clear = new JButton("Clear games");
        clear.addActionListener(event -> clear());

        JPanel toolbar = new JPanel(new BorderLayout(8, 0));
        toolbar.setBorder(new EmptyBorder(6, 8, 6, 8));
        toolbar.add(status, BorderLayout.WEST);

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(copy);
        actions.add(copyRaw);
        actions.add(clear);
        toolbar.add(actions, BorderLayout.EAST);

        add(toolbar, BorderLayout.NORTH);
        add(gameTabs, BorderLayout.CENTER);
    }

    public void accept(LogMessageInterface message) {
        router.route(message).ifPresent(key -> {
            GameView view = views.computeIfAbsent(key, this::createGameView);
            view.getModel().addRawRecord(message.getRawText());
            view.accept(message);
            status.setText(views.size() + (views.size() == 1 ? " game" : " games") + " loaded");
        });
    }

    public void clear() {
        views.values().forEach(GameView::clear);
        views.clear();
        matches.clear();
        gameTabs.removeAll();
        status.setText("No games loaded");
    }

    private GameView createGameView(GameKey key) {
        MatchSession match = matches.computeIfAbsent(
                key.getMatchId(), matchId -> new MatchSession(matchId, abilityNames));
        GameSession game = match.game(key.getGameNumber());

        GameView view = new GameView(game.model(), abilityNames, game.projector());
        JScrollPane scrollPane = new JScrollPane(view);
        scrollPane.getVerticalScrollBar().setUnitIncrement(20);
        gameTabs.addTab(key.displayName(), scrollPane);
        gameTabs.setSelectedComponent(scrollPane);
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
