package app.deckplanner.ui;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.ui.AppColors;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.Objects;

/** Theme-aware explicit loading, empty, offline, partial-cache, and failure treatment. */
public final class DeckPlannerResultsStatePanel extends JPanel {
    private final JLabel title = new JLabel();
    private final JLabel detail = new JLabel();
    private final JProgressBar progress = new JProgressBar();

    public DeckPlannerResultsStatePanel() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBorder(new EmptyBorder(28, 28, 28, 28));
        setOpaque(true);
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        detail.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress.setIndeterminate(true);
        progress.setMaximumSize(new Dimension(220, 12));
        add(Box.createVerticalGlue());
        add(title);
        add(Box.createVerticalStrut(8));
        add(detail);
        add(Box.createVerticalStrut(14));
        add(progress);
        add(Box.createVerticalGlue());
        showState(new DeckPlannerFilterCoordinator.Loading(
                DeckPlannerFilterCoordinator.Availability.READY));
    }

    public void showState(DeckPlannerFilterCoordinator.ViewState state) {
        Objects.requireNonNull(state);
        progress.setVisible(state instanceof DeckPlannerFilterCoordinator.Loading);
        if (state instanceof DeckPlannerFilterCoordinator.Loading) {
            title.setText("Filtering cards…");
            detail.setText(availabilityDetail(state.availability()));
        } else if (state instanceof DeckPlannerFilterCoordinator.Empty) {
            title.setText("No cards match these filters");
            detail.setText(state.availability() == DeckPlannerFilterCoordinator.Availability.READY
                    ? "Try removing a color, type, mana range, or tag."
                    : availabilityDetail(state.availability()));
        } else if (state instanceof DeckPlannerFilterCoordinator.Failed failed) {
            title.setText("Could not update results");
            detail.setText(failed.message());
        } else if (state.availability() != DeckPlannerFilterCoordinator.Availability.READY) {
            title.setText(state.availability() == DeckPlannerFilterCoordinator.Availability.OFFLINE
                    ? "Offline catalog" : "Partial catalog");
            detail.setText(availabilityDetail(state.availability()));
        } else {
            title.setText("");
            detail.setText("");
        }
        setVisible(!(state instanceof DeckPlannerFilterCoordinator.Content)
                || state.availability() != DeckPlannerFilterCoordinator.Availability.READY);
        revalidate();
        repaint();
    }

    @Override public void updateUI() {
        super.updateUI();
        Color background = AppColors.color("Panel.background", new Color(0x202328));
        Color foreground = AppColors.color("Label.foreground", Color.WHITE);
        setBackground(background);
        if (title != null) {
            title.setForeground(foreground);
        }
        if (detail != null) {
            detail.setForeground(AppColors.color("Label.disabledForeground", new Color(0xB8BDC7)));
        }
    }

    private static String availabilityDetail(DeckPlannerFilterCoordinator.Availability availability) {
        return switch (availability) {
            case READY -> "Updating results and tag counts.";
            case PARTIAL_CACHE -> "Showing cached cards while the catalog finishes loading.";
            case OFFLINE -> "Offline: filtering the most recent cached catalog.";
        };
    }
}
