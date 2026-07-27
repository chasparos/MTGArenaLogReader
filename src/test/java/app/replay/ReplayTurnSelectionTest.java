package app.replay;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReplayTurnSelectionTest {
    @Test
    void supportsSingleToggleAndRangeSelection() {
        ReplayTurnSelection selection = new ReplayTurnSelection();
        selection.selectOnly(3);
        selection.selectFromPointer(5, true, false);
        assertEquals(Set.of(3, 4, 5), selection.snapshot());

        selection.selectFromPointer(4, false, true);
        assertEquals(Set.of(3, 5), selection.snapshot());

        selection.selectFromPointer(7, false, false);
        assertEquals(Set.of(7), selection.snapshot());
    }

    @Test
    void extendingWithToggleAddsRangeWithoutClearingExistingTurns() {
        ReplayTurnSelection selection = new ReplayTurnSelection();
        selection.selectOnly(2);
        selection.selectFromPointer(5, false, true);
        selection.selectFromPointer(7, true, true);

        assertEquals(Set.of(2, 5, 6, 7), selection.snapshot());
        assertEquals("2–7", selection.compactLabel());

        selection.clear();
        assertTrue(selection.isEmpty());
    }
}
