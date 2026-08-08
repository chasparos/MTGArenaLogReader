package app.deckplanner.ui;

import java.awt.Dimension;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

/** Pure responsive layout model for Deck Planner card components. */
public final class CardGridLayout {
    public static final double CARD_ASPECT = 63.0 / 88.0;
    public static final int READABLE_MINIMUM_CARD_WIDTH = 275;
    public static final int READABLE_MAXIMUM_CARD_WIDTH = 400;

    /** Standard readable browser layout used by production and review surfaces. */
    public static CardGridLayout readableDefaults() {
        return new CardGridLayout(READABLE_MINIMUM_CARD_WIDTH, READABLE_MAXIMUM_CARD_WIDTH,
                18, 22, 20);
    }

    private final int minimumCardWidth;
    private final int maximumCardWidth;
    private final int horizontalGap;
    private final int verticalGap;
    private final int outerPadding;

    public CardGridLayout(int minimumCardWidth, int maximumCardWidth,
                          int horizontalGap, int verticalGap, int outerPadding) {
        if (minimumCardWidth <= 0 || maximumCardWidth < minimumCardWidth) {
            throw new IllegalArgumentException("invalid card width bounds");
        }
        if (horizontalGap < 0 || verticalGap < 0 || outerPadding < 0) {
            throw new IllegalArgumentException("spacing must be non-negative");
        }
        this.minimumCardWidth = minimumCardWidth;
        this.maximumCardWidth = maximumCardWidth;
        this.horizontalGap = horizontalGap;
        this.verticalGap = verticalGap;
        this.outerPadding = outerPadding;
    }

    public Result layout(int itemCount, int viewportWidth) {
        if (itemCount < 0) throw new IllegalArgumentException("itemCount must be non-negative");
        int usable = Math.max(1, viewportWidth - outerPadding * 2);
        int columns = Math.max(1, (usable + horizontalGap) / (minimumCardWidth + horizontalGap));
        int cardWidth = Math.min(maximumCardWidth,
                Math.max(minimumCardWidth, (usable - horizontalGap * (columns - 1)) / columns));
        while (columns > 1 && cardWidth < minimumCardWidth) {
            columns--;
            cardWidth = Math.min(maximumCardWidth,
                    Math.max(minimumCardWidth, (usable - horizontalGap * (columns - 1)) / columns));
        }
        int gridWidth = columns * cardWidth + (columns - 1) * horizontalGap;
        int startX = outerPadding + Math.max(0, (usable - gridWidth) / 2);
        int cardHeight = Math.max(1, (int) Math.round(cardWidth / CARD_ASPECT));
        List<Rectangle> bounds = new ArrayList<>(itemCount);
        for (int index = 0; index < itemCount; index++) {
            int row = index / columns;
            int column = index % columns;
            bounds.add(new Rectangle(startX + column * (cardWidth + horizontalGap),
                    outerPadding + row * (cardHeight + verticalGap), cardWidth, cardHeight));
        }
        int rows = itemCount == 0 ? 0 : ((itemCount - 1) / columns) + 1;
        int contentHeight = rows == 0 ? outerPadding * 2
                : outerPadding * 2 + rows * cardHeight + (rows - 1) * verticalGap;
        return new Result(columns, cardWidth, cardHeight, List.copyOf(bounds),
                new Dimension(Math.max(viewportWidth, gridWidth + outerPadding * 2), contentHeight));
    }

    public record Result(int columns, int cardWidth, int cardHeight,
                         List<Rectangle> bounds, Dimension preferredSize) {
        public Result {
            bounds = List.copyOf(bounds);
            preferredSize = new Dimension(preferredSize);
        }

        public int indexAt(int x, int y) {
            for (int index = 0; index < bounds.size(); index++) {
                if (bounds.get(index).contains(x, y)) return index;
            }
            return -1;
        }
    }
}
