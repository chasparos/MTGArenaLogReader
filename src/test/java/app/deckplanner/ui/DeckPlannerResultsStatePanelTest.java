package app.deckplanner.ui;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerResultsStatePanelTest {
    @Test void exposesLoadingEmptyAndFailureStates() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            DeckPlannerResultsStatePanel panel = new DeckPlannerResultsStatePanel();
            panel.updateUI();
            panel.updateUI();
            panel.showState(new DeckPlannerFilterCoordinator.Loading(
                    DeckPlannerFilterCoordinator.Availability.PARTIAL_CACHE));
            assertTrue(panel.isVisible());
            panel.showState(new DeckPlannerFilterCoordinator.Empty(Map.of(),
                    DeckPlannerFilterCoordinator.Availability.READY));
            assertTrue(panel.isVisible());
            panel.showState(new DeckPlannerFilterCoordinator.Failed("boom",
                    DeckPlannerFilterCoordinator.Availability.OFFLINE));
            assertTrue(panel.isVisible());
            panel.showState(new DeckPlannerFilterCoordinator.Content(java.util.List.of(), Map.of(),
                    DeckPlannerFilterCoordinator.Availability.OFFLINE));
            assertTrue(panel.isVisible());
            panel.showState(new DeckPlannerFilterCoordinator.Content(java.util.List.of(), Map.of(),
                    DeckPlannerFilterCoordinator.Availability.READY));
            assertFalse(panel.isVisible());
        });
    }
}
