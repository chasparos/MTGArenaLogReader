package app.deckplanner.ui;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.consideration.UnderConsiderationModel;
import app.deckplanner.filter.CatalogFilterIndex;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;
import app.ui.AppScrollBarUI;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UnderConsiderationPanelTest {
    @Test
    void resolvedCandidatesUseSharedReplayCardChipWithoutOwnershipText() throws Exception {
        CardInfo card = card("oracle:consider-me", "Consider Me");
        CatalogFilterIndex index = index(card);
        UnderConsiderationModel model =
                new UnderConsiderationModel(List.of("oracle:consider-me"), ignored -> { });
        AtomicReference<JComponent> row = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            UnderConsiderationPanel panel = new UnderConsiderationPanel();
            panel.bind(model, ignored -> 4);
            panel.setEntries(model.resolve(index));
            row.set(panel.candidateRows().getFirst());
        });

        ReplayCardChip chip = find(row.get(), ReplayCardChip.class);
        assertNotNull(chip);
        assertEquals("Consider Me", chip.card().getName());
        assertFalse(componentText(row.get()).toLowerCase().contains("owned"),
                "DP-06 candidate presentation must not invent or expose deferred ownership counts");
    }

    @Test
    void staleCandidatesRemainExplicitRecoverableRows() throws Exception {
        UnderConsiderationModel model =
                new UnderConsiderationModel(List.of("oracle:missing"), ignored -> { });
        AtomicReference<JComponent> row = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            UnderConsiderationPanel panel = new UnderConsiderationPanel();
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index()));
            row.set(panel.candidateRows().getFirst());
        });

        JLabel label = find(row.get(), JLabel.class);
        assertNotNull(label);
        assertTrue(label.getText().contains("stale"));
    }

    @Test
    void candidateSurfaceIsCustomPanelWithProjectScrollbarAndMoveTransferHandler() throws Exception {
        AtomicReference<UnderConsiderationPanel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            UnderConsiderationPanel panel = new UnderConsiderationPanel();
            UnderConsiderationModel model = new UnderConsiderationModel(
                    List.of("oracle:a", "oracle:b"), ignored -> { });
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index(card("oracle:a", "A"), card("oracle:b", "B"))));
            ref.set(panel);
        });

        UnderConsiderationPanel panel = ref.get();
        assertNull(find(panel, JList.class), "candidate surface must not regress to JList");
        assertInstanceOf(AppScrollBarUI.class, panel.candidateScrollPane().getVerticalScrollBar().getUI());
        assertNotNull(panel.candidateSurface().getTransferHandler());
        assertEquals(TransferHandler.MOVE,
                panel.candidateRows().getFirst().getTransferHandler()
                        .getSourceActions(panel.candidateRows().getFirst()));
        assertNotNull(findButton(panel, "Normal MTG sort"));
        assertTrue(findButton(panel, "Normal MTG sort").isEnabled());
    }

    private static String componentText(Component component) {
        if (component instanceof JLabel label) return Optional.ofNullable(label.getText()).orElse("");
        if (!(component instanceof Container container)) return "";
        StringBuilder text = new StringBuilder();
        for (Component child : container.getComponents()) {
            text.append(componentText(child)).append(' ');
        }
        return text.toString();
    }

    private static CatalogFilterIndex index(CardInfo... cards) {
        List<FormatCatalogRepository.CardOutcome> outcomes = java.util.Arrays.stream(cards)
                .map(card -> new FormatCatalogRepository.CardOutcome(card, "SUCCESS", null))
                .toList();
        return new CatalogFilterIndex(new FormatCatalogRepository.Snapshot(
                "run", "standard", 1, Instant.EPOCH, Instant.EPOCH, outcomes));
    }

    private static CardInfo card(String oracleIdentity, String name) {
        CardInfo card = new CardInfo();
        card.setId("printing-" + name);
        card.setOracleId(oracleIdentity.substring("oracle:".length()));
        card.setArenaId(12345L);
        card.setName(name);
        card.setColors(List.of("U"));
        card.setColorIdentity(List.of("U"));
        card.setTypeLine("Instant");
        card.setManaCost("{1}{U}");
        card.setCmc(2.0);
        card.setOracleText("Draw a card.");
        card.setKeywords(List.of());
        return card;
    }

    private static AbstractButton findButton(Container root, String text) {
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button && text.equals(button.getText())) return button;
            if (child instanceof Container container) {
                AbstractButton nested = findButton(container, text);
                if (nested != null) return nested;
            }
        }
        return null;
    }

    private static <T extends Component> T find(Container root, Class<T> type) {
        for (Component child : root.getComponents()) {
            if (type.isInstance(child)) return type.cast(child);
            if (child instanceof Container container) {
                T nested = find(container, type);
                if (nested != null) return nested;
            }
        }
        return null;
    }
}
