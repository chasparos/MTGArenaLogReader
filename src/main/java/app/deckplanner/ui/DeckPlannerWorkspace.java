package app.deckplanner.ui;

import app.deckplanner.application.DeckPlannerFilterCoordinator;
import app.deckplanner.filter.CatalogFilterIndex;
import app.deckplanner.filter.DeckPlannerFilterModel;
import app.deckplanner.filter.IndexedCatalogCard;
import app.ui.AppColors;
import app.ui.AppScrollBarUI;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Reusable DP-05 workspace that composes filter controls, asynchronous filtering, explicit result
 * states, and the responsive DP-04 browser without taking ownership of catalog acquisition.
 */
public final class DeckPlannerWorkspace extends JPanel implements AutoCloseable {
    private static final String CONTENT = "content";
    private static final String STATE = "state";

    private final DeckPlannerFilterPanel filters;
    private final CardBrowserPanel browser;
    private final CardBrowserScrollPane browserScrollPane;
    private final DeckPlannerResultsStatePanel statePanel = new DeckPlannerResultsStatePanel();
    private final JPanel resultCards = new JPanel(new CardLayout());
    private final JLabel availabilityBanner = new JLabel();
    private final DeckPlannerFilterCoordinator coordinator;

    public DeckPlannerWorkspace(DeckPlannerFilterModel model,
                                CatalogFilterIndex index,
                                CardBrowserPanel.ImageSource imageSource,
                                ScheduledExecutorService scheduler,
                                Executor worker,
                                Duration debounce,
                                DeckPlannerFilterCoordinator.Availability availability) {
        Objects.requireNonNull(model);
        Objects.requireNonNull(index);
        Objects.requireNonNull(imageSource);
        assertEdt();

        setLayout(new BorderLayout());
        setOpaque(true);
        filters = new DeckPlannerFilterPanel(model, allTags(index.cards()));
        browser = new CardBrowserPanel(CardGridLayout.readableDefaults(),
                new ViewportImageWindow(240), imageSource);
        browserScrollPane = new CardBrowserScrollPane(browser);

        JScrollPane filterScroll = new JScrollPane(filters,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        filterScroll.setBorder(BorderFactory.createEmptyBorder());
        filterScroll.getVerticalScrollBar().setUI(new AppScrollBarUI());
        filterScroll.getViewport().setBackground(filters.getBackground());
        filterScroll.setPreferredSize(new Dimension(350, 600));
        filterScroll.setMinimumSize(new Dimension(310, 300));

        availabilityBanner.setOpaque(true);
        availabilityBanner.setBorder(new EmptyBorder(6, 10, 6, 10));
        availabilityBanner.setVisible(false);

        JPanel content = new JPanel(new BorderLayout());
        content.add(availabilityBanner, BorderLayout.NORTH);
        content.add(browserScrollPane, BorderLayout.CENTER);
        resultCards.add(content, CONTENT);
        resultCards.add(statePanel, STATE);

        add(filterScroll, BorderLayout.WEST);
        add(resultCards, BorderLayout.CENTER);
        refreshThemeColors();

        coordinator = new DeckPlannerFilterCoordinator(model,
                state -> new DeckPlannerFilterCoordinator.Result(
                        index.filter(state.filters()), index.tagCloud(state.filters())),
                scheduler, worker, debounce);
        coordinator.setListener(this::showResult);
        coordinator.setAvailability(availability);
    }

    public void start() {
        assertEdt();
        coordinator.start();
    }

    public CardBrowserPanel browser() { return browser; }
    public DeckPlannerFilterPanel filters() { return filters; }

    public void setAvailability(DeckPlannerFilterCoordinator.Availability availability) {
        coordinator.setAvailability(availability);
    }

    @Override public void updateUI() {
        super.updateUI();
        if (availabilityBanner != null) refreshThemeColors();
    }

    private void showResult(DeckPlannerFilterCoordinator.ViewState state) {
        assertEdt();
        statePanel.showState(state);
        if (state instanceof DeckPlannerFilterCoordinator.Content content) {
            filters.setTagCloud(content.tagCloud());
            browserScrollPane.setCards(toBrowserCards(content.cards()));
            showAvailability(content.availability());
            showCard(CONTENT);
        } else if (state instanceof DeckPlannerFilterCoordinator.Empty empty) {
            filters.setTagCloud(empty.tagCloud());
            browserScrollPane.setCards(List.of());
            showAvailability(empty.availability());
            showCard(STATE);
        } else if (state instanceof DeckPlannerFilterCoordinator.Loading loading) {
            showAvailability(loading.availability());
            showCard(STATE);
        } else if (state instanceof DeckPlannerFilterCoordinator.Failed failed) {
            showAvailability(failed.availability());
            showCard(STATE);
        }
    }

    private void showAvailability(DeckPlannerFilterCoordinator.Availability availability) {
        availabilityBanner.setVisible(availability != DeckPlannerFilterCoordinator.Availability.READY);
        availabilityBanner.setText(switch (availability) {
            case READY -> "";
            case PARTIAL_CACHE -> "Partial catalog — showing cached cards while loading continues";
            case OFFLINE -> "Offline — showing the most recent cached catalog";
        });
    }

    private void showCard(String name) {
        ((CardLayout) resultCards.getLayout()).show(resultCards, name);
        resultCards.revalidate();
        resultCards.repaint();
    }

    private void refreshThemeColors() {
        Color background = AppColors.color("Panel.background", new Color(0x202328));
        Color foreground = AppColors.color("Label.foreground", Color.WHITE);
        setBackground(background);
        resultCards.setBackground(background);
        availabilityBanner.setBackground(AppColors.color("TextField.background", new Color(0x30343A)));
        availabilityBanner.setForeground(foreground);
    }

    private static Collection<app.deckplanner.filter.SemanticTag> allTags(List<IndexedCatalogCard> cards) {
        return cards.stream().flatMap(card -> card.tags().stream()).collect(
                java.util.stream.Collectors.toCollection(java.util.TreeSet::new));
    }

    private static List<CardBrowserPanel.BrowserCard> toBrowserCards(List<IndexedCatalogCard> cards) {
        return cards.stream().map(card -> new CardBrowserPanel.BrowserCard(
                card.group().identity(), card.group().preferredPrinting().getName())).toList();
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Deck Planner workspace must be used on the EDT");
        }
    }

    @Override public void close() {
        coordinator.close();
    }
}
