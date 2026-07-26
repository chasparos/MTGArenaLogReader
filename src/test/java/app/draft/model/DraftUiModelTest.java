package app.draft.model;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DraftUiModelTest {
    @Test
    void packNavigationPreservesPickPositionWhenAvailable() {
        DraftUiModel model = new DraftUiModel();
        model.replaceTimeline(List.of(
                state(1, 1), state(1, 2),
                state(2, 1), state(2, 2),
                state(3, 1)), true);

        assertTrue(model.previousPack());
        assertEquals(2, model.selected().packNumber());
        assertEquals(1, model.selected().pickNumber());
        assertTrue(model.previousPack());
        assertEquals(1, model.selected().packNumber());
        assertEquals(1, model.selected().pickNumber());
        assertFalse(model.hasPreviousPack());
        assertTrue(model.nextPack());
        assertEquals(2, model.selected().packNumber());
    }

    private DraftPickState state(int pack, int pick) {
        return new DraftPickState(
                "draft", pack, pick, List.of(), null,
                List.of(), List.of(), List.of(), Map.of());
    }
}
