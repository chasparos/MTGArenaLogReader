package app.deckplanner.ui;

import app.ui.AppColors;
import app.ui.AppScrollBarUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Scroll coordinator for {@link CardBrowserPanel}. It owns viewport notifications and preserves a
 * logical card anchor while responsive width changes or a filtered result is replaced.
 */
public final class CardBrowserScrollPane extends JScrollPane {
    private final CardBrowserPanel browser;
    private Optional<CardBrowserPanel.ScrollAnchor> anchor = Optional.empty();
    private boolean restoring;

    public CardBrowserScrollPane(CardBrowserPanel browser) {
        super(java.util.Objects.requireNonNull(browser));
        this.browser = browser;
        setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        setBorder(BorderFactory.createEmptyBorder());
        setBackground(AppColors.color("ScrollPane.background", browser.getBackground()));
        getViewport().setOpaque(true);
        getViewport().setBackground(AppColors.color("Viewport.background", browser.getBackground()));
        installApplicationScrollBars();
        getVerticalScrollBar().setUnitIncrement(48);
        getVerticalScrollBar().getModel().addChangeListener(event -> syncVerticalScrollBar());
        syncVerticalScrollBar();
        getViewport().addChangeListener(event -> viewportChanged());
        getViewport().addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                Optional<CardBrowserPanel.ScrollAnchor> retained = anchor;
                SwingUtilities.invokeLater(() -> restore(retained));
            }
        });
    }


    @Override public void doLayout() {
        super.doLayout();
        syncVerticalScrollBar();
    }

    @Override public void updateUI() {
        super.updateUI();
        if (browser != null) {
            setBackground(AppColors.color("ScrollPane.background", browser.getBackground()));
            getViewport().setBackground(AppColors.color("Viewport.background", browser.getBackground()));
            installApplicationScrollBars();
        }
    }

    private void syncVerticalScrollBar() {
        int contentHeight = browser == null ? 0 : browser.getPreferredSize().height;
        int viewportHeight = getViewport() == null ? 0 : getViewport().getExtentSize().height;
        getVerticalScrollBar().setEnabled(contentHeight > viewportHeight);
    }

    private void installApplicationScrollBars() {
        getVerticalScrollBar().setUI(new AppScrollBarUI());
        getHorizontalScrollBar().setUI(new AppScrollBarUI());
        getVerticalScrollBar().setPreferredSize(new Dimension(11, 0));
        getHorizontalScrollBar().setPreferredSize(new Dimension(0, 11));
    }

    public CardBrowserPanel browser() {
        return browser;
    }

    /** Replaces the result set while retaining the current logical top-card anchor when possible. */
    public void setCards(List<CardBrowserPanel.BrowserCard> cards) {
        assertEdt();
        Optional<CardBrowserPanel.ScrollAnchor> retained = browser.captureScrollAnchor(getViewport().getViewRect());
        browser.setCards(cards);
        restore(retained);
        syncVerticalScrollBar();
    }

    private void viewportChanged() {
        if (restoring) return;
        Rectangle visible = getViewport().getViewRect();
        browser.updateViewport(visible);
        anchor = browser.captureScrollAnchor(visible);
    }

    private void restore(Optional<CardBrowserPanel.ScrollAnchor> retained) {
        assertEdt();
        if (retained == null || retained.isEmpty()) {
            viewportChanged();
            return;
        }
        OptionalInt resolved = browser.resolveScrollAnchorY(retained.get());
        if (resolved.isEmpty()) {
            viewportChanged();
            return;
        }
        JViewport viewport = getViewport();
        int maxY = Math.max(0, browser.getPreferredSize().height - viewport.getExtentSize().height);
        int y = Math.min(maxY, resolved.getAsInt());
        restoring = true;
        try {
            viewport.setViewPosition(new Point(0, y));
        } finally {
            restoring = false;
        }
        browser.updateViewport(viewport.getViewRect());
        anchor = browser.captureScrollAnchor(viewport.getViewRect());
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Swing mutation must run on EDT");
        }
    }
}
