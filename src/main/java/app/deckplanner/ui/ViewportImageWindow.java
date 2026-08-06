package app.deckplanner.ui;

import java.awt.Rectangle;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Calculates visible and directional-prefetch image request sets without Swing side effects. */
public final class ViewportImageWindow {
    private final int prefetchPixels;

    public ViewportImageWindow(int prefetchPixels) {
        if (prefetchPixels < 0) throw new IllegalArgumentException("prefetchPixels must be non-negative");
        this.prefetchPixels = prefetchPixels;
    }

    public Window select(List<Rectangle> cardBounds, Rectangle viewport, int scrollDirection) {
        if (cardBounds == null || viewport == null) throw new NullPointerException();

        LinkedHashSet<Integer> visibleIndices = new LinkedHashSet<>();
        for (int index = 0; index < cardBounds.size(); index++) {
            if (cardBounds.get(index).intersects(viewport)) visibleIndices.add(index);
        }

        LinkedHashSet<Integer> requestedIndices = new LinkedHashSet<>(visibleIndices);
        if (scrollDirection >= 0) {
            addNearestRowBelow(cardBounds, viewport, requestedIndices);
        }
        if (scrollDirection <= 0) {
            addNearestRowAbove(cardBounds, viewport, requestedIndices);
        }
        return new Window(visibleIndices, requestedIndices);
    }

    private void addNearestRowBelow(List<Rectangle> cardBounds,
                                    Rectangle viewport,
                                    LinkedHashSet<Integer> requestedIndices) {
        int viewportBottom = viewport.y + viewport.height;
        int nearestY = Integer.MAX_VALUE;
        for (Rectangle bounds : cardBounds) {
            if (bounds.y < viewportBottom || bounds.y - viewportBottom > prefetchPixels) continue;
            nearestY = Math.min(nearestY, bounds.y);
        }
        if (nearestY == Integer.MAX_VALUE) return;
        for (int index = 0; index < cardBounds.size(); index++) {
            if (cardBounds.get(index).y == nearestY) requestedIndices.add(index);
        }
    }

    private void addNearestRowAbove(List<Rectangle> cardBounds,
                                    Rectangle viewport,
                                    LinkedHashSet<Integer> requestedIndices) {
        int nearestBottom = Integer.MIN_VALUE;
        for (Rectangle bounds : cardBounds) {
            int bottom = bounds.y + bounds.height;
            if (bottom > viewport.y || viewport.y - bottom > prefetchPixels) continue;
            nearestBottom = Math.max(nearestBottom, bottom);
        }
        if (nearestBottom == Integer.MIN_VALUE) return;
        for (int index = 0; index < cardBounds.size(); index++) {
            Rectangle bounds = cardBounds.get(index);
            if (bounds.y + bounds.height == nearestBottom) requestedIndices.add(index);
        }
    }

    public record Window(Set<Integer> visibleIndices, Set<Integer> requestedIndices) {
        public Window {
            visibleIndices = immutableOrderedCopy(visibleIndices);
            requestedIndices = immutableOrderedCopy(requestedIndices);
            if (!requestedIndices.containsAll(visibleIndices)) {
                throw new IllegalArgumentException("requested window must include all visible cards");
            }
        }

        private static Set<Integer> immutableOrderedCopy(Set<Integer> source) {
            return Collections.unmodifiableSet(new LinkedHashSet<>(source));
        }
    }
}
