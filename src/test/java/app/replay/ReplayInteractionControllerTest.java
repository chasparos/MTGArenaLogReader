package app.replay;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayInteractionControllerTest {
    @Test
    void delegatesPointerModifiersToTurnSelectionOnlyWhenCoachingIsEnabled() {
        JPanel owner = new JPanel();
        ReplayTurnSelection selection = new ReplayTurnSelection();
        AtomicInteger repaints = new AtomicInteger();
        ReplayInteractionController controller = new ReplayInteractionController(
                owner, selection,                 point -> point.x, point -> null, repaints::incrementAndGet);

        controller.selectTurnAt(mouse(owner, 3, 0));
        assertTrue(selection.isEmpty());

        controller.setCoachingActions(request -> { });
        controller.selectTurnAt(mouse(owner, 3, 0));
        controller.selectTurnAt(mouse(
                owner, 5, InputEvent.SHIFT_DOWN_MASK));
        assertEquals(Set.of(3, 4, 5), selection.snapshot());
        assertTrue(controller.coachingEnabled());

        controller.setCoachingActions(null);
        assertFalse(controller.coachingEnabled());
        assertTrue(selection.isEmpty());
        assertEquals(4, repaints.get());
    }

    private MouseEvent mouse(JPanel owner, int x, int modifiers) {
        return new MouseEvent(
                owner, MouseEvent.MOUSE_PRESSED, 0, modifiers,
                x, 0, 1, false, MouseEvent.BUTTON1);
    }
}
