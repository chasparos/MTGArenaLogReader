package app.replay;

import app.model.event.GameEvent;

import javax.swing.*;
import java.awt.Component;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.function.Function;

/**
 * Owns replay coaching menus, standard questions, pointer-selection dispatch,
 * and pointer-selection dispatch.
 */
final class ReplayInteractionController {
    private final Component owner;
    private final ReplayTurnSelection selection;
    private final Function<Point, Integer> turnAt;
    private final Function<Point, GameEvent> eventAt;
    private final Runnable repaint;
    private GameView.CoachingActions coachingActions;

    ReplayInteractionController(
            Component owner,
            ReplayTurnSelection selection,
            Function<Point, Integer> turnAt,
            Function<Point, GameEvent> eventAt,
            Runnable repaint) {
        this.owner = owner;
        this.selection = selection;
        this.turnAt = turnAt;
        this.eventAt = eventAt;
        this.repaint = repaint;
    }

    void setCoachingActions(GameView.CoachingActions actions) {
        coachingActions = actions;
        if (actions == null) selection.clear();
        repaint.run();
    }

    boolean coachingEnabled() {
        return coachingActions != null;
    }

    void selectTurnAt(MouseEvent mouse) {
        if (!coachingEnabled()) return;
        Integer turn = turnAt.apply(mouse.getPoint());
        if (turn == null) return;
        selection.selectFromPointer(
                turn,
                mouse.isShiftDown(),
                mouse.isControlDown() || mouse.isMetaDown());
        repaint.run();
    }

    void showContextMenu(MouseEvent mouse) {
        if (!coachingEnabled()) return;

        Integer turn = turnAt.apply(mouse.getPoint());
        if (turn == null) {
            GameEvent event = eventAt.apply(mouse.getPoint());
            turn = event == null ? null : event.getTurnNumber();
        }
        if (turn != null && !selection.contains(turn)) {
            selection.selectOnly(turn);
            repaint.run();
        }

        JPopupMenu menu = new JPopupMenu();
        addContextItem(menu, "Ask about this match",
                GameView.CoachingScope.MATCH, Set.of(), null);
        addContextItem(menu, "Ask about this game",
                GameView.CoachingScope.GAME, Set.of(), null);
        if (turn != null) {
            addContextItem(menu, "Ask about turn " + turn,
                    GameView.CoachingScope.TURN, Set.of(turn), null);
        }
        if (!selection.isEmpty()) {
            addContextItem(menu, selection.size() == 1
                            ? "Ask about selected turn"
                            : "Ask about selected turns " + selection.compactLabel(),
                    GameView.CoachingScope.SELECTED_TURNS,
                    selection.snapshot(), null);
        }

        menu.addSeparator();
        JMenu standard = new JMenu("Standard questions");
        addStandardQuestions(standard, turn);
        menu.add(standard);

        menu.show(owner, mouse.getX(), mouse.getY());
    }

    private void addStandardQuestions(JMenu menu, Integer turn) {
        addContextItem(menu, "What deck is my opponent using?",
                GameView.CoachingScope.MATCH, Set.of(),
                "What deck is my opponent using, and what should I know about decks of this kind?");
        addContextItem(menu, "Was my starting hand keep correct?",
                GameView.CoachingScope.GAME, Set.of(),
                "Was keeping my starting hand correct? Explain the important factors and alternatives.");
        if (turn != null) {
            addContextItem(menu, "Could I have played this turn differently?",
                    GameView.CoachingScope.TURN, Set.of(turn),
                    "Could I have played this turn differently? Focus on realistic alternatives using only known information.");
            addContextItem(menu, "Review attacks and blocks",
                    GameView.CoachingScope.TURN, Set.of(turn),
                    "Were the attacks and blocks on this turn correct? Explain better lines, if any.");
        }
        if (!selection.isEmpty()) {
            addContextItem(menu, "Review selected turns",
                    GameView.CoachingScope.SELECTED_TURNS, selection.snapshot(),
                    "Review these turns as one sequence. Identify the most important decision and a better line, if one existed.");
        }
    }

    private void addContextItem(
            JComponent menu, String label, GameView.CoachingScope scope,
            Set<Integer> turns, String question) {
        JMenuItem item = new JMenuItem(label);
        item.addActionListener(ignored -> coachingActions.request(
                new GameView.CoachingRequest(scope, turns, question)));
        menu.add(item);
    }

}
