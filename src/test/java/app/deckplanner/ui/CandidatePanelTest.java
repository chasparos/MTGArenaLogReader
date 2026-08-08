package app.deckplanner.ui;

import app.deckplanner.catalog.FormatCatalogRepository;
import app.deckplanner.candidate.CandidateModel;
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

class CandidatePanelTest {
    @Test
    void resolvedCandidatesUseSharedReplayCardChipWithoutOwnershipText() throws Exception {
        CardInfo card = card("oracle:consider-me", "Consider Me");
        CatalogFilterIndex index = index(card);
        CandidateModel model =
                new CandidateModel(List.of("oracle:consider-me"), ignored -> { });
        AtomicReference<JComponent> row = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
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
        CandidateModel model =
                new CandidateModel(List.of("oracle:missing"), ignored -> { });
        AtomicReference<JComponent> row = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index()));
            row.set(panel.candidateRows().getFirst());
        });

        JLabel label = find(row.get(), JLabel.class);
        assertNotNull(label);
        assertTrue(label.getText().contains("stale"));
    }


    @Test
    void selectionUsesChipOutlineInsteadOfRowRectangle() throws Exception {
        CardInfo card = card("oracle:selected", "Selected Candidate");
        CandidateModel model = new CandidateModel(List.of("oracle:selected"), ignored -> { });
        AtomicReference<ReplayCardChip> chipRef = new AtomicReference<>();
        AtomicReference<JComponent> rowRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index(card)));
            JComponent row = panel.candidateRows().getFirst();
            ReplayCardChip chip = find(row, ReplayCardChip.class);
            rowRef.set(row);
            chipRef.set(chip);
            chip.dispatchEvent(new java.awt.event.MouseEvent(chip,
                    java.awt.event.MouseEvent.MOUSE_PRESSED,
                    System.currentTimeMillis(), 0, 4, 4, 1, false));
        });

        assertNotNull(chipRef.get().outlineColor());
        assertFalse(rowRef.get().isOpaque(),
                "candidate selection should not paint a large rectangular row background");
    }


    @Test
    void categoryCountsRefreshWhenCandidateMembershipChanges() throws Exception {
        CardInfo first = card("oracle:first", "First");
        first.setTypeLine("Creature — Elf");
        CardInfo second = card("oracle:second", "Second");
        second.setTypeLine("Creature — Wizard");
        CatalogFilterIndex index = index(first, second);
        CandidateModel model = new CandidateModel(
                List.of("oracle:first", "oracle:second"), ignored -> { });
        AtomicReference<CandidatePanel> panelRef = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index));
            panelRef.set(panel);
        });
        assertTrue(componentText(panelRef.get()).contains("Creatures (2)"));

        model.remove("oracle:second");
        SwingUtilities.invokeAndWait(() ->
                panelRef.get().setEntries(model.resolve(index)));
        assertTrue(componentText(panelRef.get()).contains("Creatures (1)"));
        assertFalse(componentText(panelRef.get()).contains("Creatures (2)"));
    }

    @Test
    void candidateSurfaceIsCustomPanelWithProjectScrollbarAndMoveTransferHandler() throws Exception {
        AtomicReference<CandidatePanel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
            CandidateModel model = new CandidateModel(
                    List.of("oracle:a", "oracle:b"), ignored -> { });
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index(card("oracle:a", "A"), card("oracle:b", "B"))));
            ref.set(panel);
        });

        CandidatePanel panel = ref.get();
        assertNull(find(panel, JList.class), "candidate surface must not regress to JList");
        assertInstanceOf(AppScrollBarUI.class, panel.candidateScrollPane().getVerticalScrollBar().getUI());
        assertNotNull(panel.candidateSurface().getTransferHandler());
        assertEquals(TransferHandler.MOVE,
                panel.candidateRows().getFirst().getTransferHandler()
                        .getSourceActions(panel.candidateRows().getFirst()));
        assertNotNull(findButton(panel, "Normal MTG sort"));
        assertTrue(findButton(panel, "Normal MTG sort").isEnabled());
    }


    @Test
    void candidateSurfaceGroupsPlanningCategoriesAndUsesLargerScalableReplayChips() throws Exception {
        CardInfo creature = card("oracle:creature", "Creature Card");
        creature.setTypeLine("Creature — Wizard");
        CardInfo spell = card("oracle:spell", "Spell Card");
        spell.setTypeLine("Sorcery");
        CardInfo land = card("oracle:land", "Utility Land");
        land.setTypeLine("Land");
        CandidateModel model = new CandidateModel(
                List.of("oracle:creature", "oracle:spell", "oracle:land"), ignored -> { });
        AtomicReference<CandidatePanel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
            panel.bind(model, ignored -> -1);
            panel.setEntries(model.resolve(index(creature, spell, land)));
            ref.set(panel);
        });

        CandidatePanel panel = ref.get();
        String text = componentText(panel);
        assertTrue(text.contains("Creatures (1)"));
        assertTrue(text.contains("Noncreatures (1)"));
        assertTrue(text.contains("Nonbasic Lands (1)"));
        ReplayCardChip chip = find(panel, ReplayCardChip.class);
        assertNotNull(chip);
        assertTrue(chip.presentationScale() > 1f);
        assertTrue(chip.getPreferredSize().height > 38);
    }


    @Test
    void editableCategoryRemovalMovesCardsToImplicitUncategorizedAndShowsInlineAddControl() throws Exception {
        CardInfo creature = card("oracle:creature", "Creature Card");
        creature.setTypeLine("Creature — Wizard");
        CandidateModel model = new CandidateModel(List.of("oracle:creature"), ignored -> { });
        app.deckplanner.candidate.CandidateWorkspaceState workspace =
                app.deckplanner.candidate.CandidateWorkspaceState.transientState();
        AtomicReference<CandidatePanel> ref = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            CandidatePanel panel = new CandidatePanel();
            panel.bind(model, workspace, ignored -> -1);
            panel.setEntries(model.resolve(index(creature)));
            ref.set(panel);
        });

        AbstractButton removeCategory = findButtonByTooltip(ref.get(), "Remove category");
        assertNotNull(removeCategory);
        SwingUtilities.invokeAndWait(removeCategory::doClick);

        assertTrue(componentText(ref.get()).contains("Uncategorized (1)"));
        assertNotNull(findButtonByTooltip(ref.get(), "Add category"));
        assertEquals(app.deckplanner.candidate.CandidateWorkspaceState.UNCATEGORIZED,
                workspace.assignments().get("oracle:creature"));
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


    private static AbstractButton findButtonByTooltip(Container root, String tooltip) {
        for (Component child : root.getComponents()) {
            if (child instanceof AbstractButton button && tooltip.equals(button.getToolTipText())) return button;
            if (child instanceof Container container) {
                AbstractButton nested = findButtonByTooltip(container, tooltip);
                if (nested != null) return nested;
            }
        }
        return null;
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
