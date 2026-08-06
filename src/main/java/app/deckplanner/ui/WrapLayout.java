package app.deckplanner.ui;

import java.awt.*;

/** Flow layout whose preferred height follows the available width instead of clipping later rows. */
final class WrapLayout extends FlowLayout {
    WrapLayout(int align, int hgap, int vgap) {
        super(align, hgap, vgap);
    }

    @Override public Dimension preferredLayoutSize(Container target) {
        return layoutSize(target, true);
    }

    @Override public Dimension minimumLayoutSize(Container target) {
        Dimension minimum = layoutSize(target, false);
        minimum.width -= getHgap() + 1;
        return minimum;
    }

    private Dimension layoutSize(Container target, boolean preferred) {
        synchronized (target.getTreeLock()) {
            int width = target.getWidth();
            Container parent = target.getParent();
            if (width <= 0 && parent != null) width = parent.getWidth();
            if (width <= 0) width = Integer.MAX_VALUE;

            Insets insets = target.getInsets();
            int maxWidth = Math.max(1, width - insets.left - insets.right - getHgap() * 2);
            int rowWidth = 0;
            int rowHeight = 0;
            int totalWidth = 0;
            int totalHeight = 0;

            for (Component component : target.getComponents()) {
                if (!component.isVisible()) continue;
                Dimension size = preferred ? component.getPreferredSize() : component.getMinimumSize();
                if (rowWidth > 0 && rowWidth + getHgap() + size.width > maxWidth) {
                    totalWidth = Math.max(totalWidth, rowWidth);
                    totalHeight += rowHeight + getVgap();
                    rowWidth = 0;
                    rowHeight = 0;
                }
                if (rowWidth > 0) rowWidth += getHgap();
                rowWidth += size.width;
                rowHeight = Math.max(rowHeight, size.height);
            }
            totalWidth = Math.max(totalWidth, rowWidth);
            totalHeight += rowHeight;
            totalWidth += insets.left + insets.right + getHgap() * 2;
            totalHeight += insets.top + insets.bottom + getVgap() * 2;
            return new Dimension(totalWidth, totalHeight);
        }
    }
}
