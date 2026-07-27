package app.ui;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A reusable, self-scrolling virtual list for expensive custom-painted rows.
 * Rows receive an estimated height immediately and are rendered sequentially
 * to immutable back buffers on a worker thread. Only visible buffers are
 * painted on the EDT; interaction regions are retained in row-local space.
 */
public abstract class AsyncVirtualListPanel<T, C> extends JComponent {
    private static final int SCROLLBAR_WIDTH = 12;
    private static final int MIN_THUMB_HEIGHT = 28;
    private static final int UNIT_SCROLL = 48;

    public record Item<T>(Object key, T value, int estimatedHeight) {
        public Item {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(value, "value");
            estimatedHeight = Math.max(1, estimatedHeight);
        }
    }

    public record HitRegion<C>(Shape shape, C context) {
        public HitRegion {
            Objects.requireNonNull(shape, "shape");
            Objects.requireNonNull(context, "context");
        }
    }

    public record RenderedItem<C>(BufferedImage image, int height,
                                  List<HitRegion<C>> hitRegions) {
        public RenderedItem {
            Objects.requireNonNull(image, "image");
            height = Math.max(1, height);
            hitRegions = hitRegions == null ? List.of() : List.copyOf(hitRegions);
        }
    }

    public record LocatedItem<T, C>(int index, Item<T> item, Rectangle bounds,
                                    RenderedItem<C> rendered) {}

    private final ExecutorService renderer;
    private final AtomicLong generation = new AtomicLong();
    private final Map<Object, Cached<C>> cacheByKey = new HashMap<>();
    private List<Item<T>> items = List.of();
    private FenwickTree heights = new FenwickTree(0);
    private int contentHeight;
    private int scrollY;
    private int renderWidth = -1;
    private boolean draggingThumb;
    private int dragOffset;

    protected AsyncVirtualListPanel(String workerName) {
        ThreadFactory factory = task -> {
            Thread thread = new Thread(task, workerName);
            thread.setDaemon(true);
            return thread;
        };
        renderer = Executors.newSingleThreadExecutor(factory);
        setOpaque(true);
        setFocusable(true);
        enableEvents(AWTEvent.MOUSE_WHEEL_EVENT_MASK
                | AWTEvent.MOUSE_EVENT_MASK
                | AWTEvent.MOUSE_MOTION_EVENT_MASK
                | AWTEvent.COMPONENT_EVENT_MASK);
    }

    protected abstract RenderedItem<C> renderItem(Item<T> item, int width);

    protected void paintPlaceholder(Graphics2D graphics, Item<T> item,
                                    Rectangle bounds) {
        graphics.setColor(getBackground());
        graphics.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
    }

    protected void paintItemOverlay(Graphics2D graphics,
                                    LocatedItem<T, C> item) {}

    protected final void setItems(List<Item<T>> replacement) {
        assertEdt();
        List<Item<T>> next = replacement == null ? List.of() : List.copyOf(replacement);
        Map<Object, Cached<C>> retained = new HashMap<>();
        int[] values = new int[next.size()];
        List<Integer> toRender = new ArrayList<>();
        for (int index = 0; index < next.size(); index++) {
            Item<T> item = next.get(index);
            Cached<C> cached = cacheByKey.get(item.key());
            if (cached != null && cached.width == usableWidth()) {
                retained.put(item.key(), cached);
                values[index] = cached.rendered.height();
            } else {
                values[index] = item.estimatedHeight();
                toRender.add(index);
            }
        }
        items = next;
        cacheByKey.clear();
        cacheByKey.putAll(retained);
        heights = FenwickTree.from(values);
        contentHeight = heights.total();
        scrollY = clampScroll(scrollY);
        long targetGeneration = generation.incrementAndGet();
        renderWidth = usableWidth();
        scheduleRenderWorker(next, toRender, renderWidth, targetGeneration);
        repaint();
    }

    protected final List<Item<T>> itemsSnapshot() { return items; }

    protected final void invalidateRenderedItems() {
        assertEdt();
        cacheByKey.clear();
        int[] estimates = items.stream().mapToInt(Item::estimatedHeight).toArray();
        heights = FenwickTree.from(estimates);
        contentHeight = heights.total();
        scrollY = clampScroll(scrollY);
        long targetGeneration = generation.incrementAndGet();
        renderWidth = usableWidth();
        List<Integer> toRender = new ArrayList<>();
        for (int index = 0; index < items.size(); index++) toRender.add(index);
        scheduleRenderWorker(items, toRender, renderWidth, targetGeneration);
        repaint();
    }

    protected final LocatedItem<T, C> itemAt(Point point) {
        if (point == null || items.isEmpty() || point.x >= usableWidth()) return null;
        int contentY = point.y + scrollY;
        int index = heights.indexAt(contentY);
        if (index < 0 || index >= items.size()) return null;
        int y = heights.prefix(index);
        int height = heights.valueAt(index);
        Cached<C> cached = cacheByKey.get(items.get(index).key());
        return new LocatedItem<>(index, items.get(index),
                new Rectangle(0, y - scrollY, usableWidth(), height),
                cached == null ? null : cached.rendered);
    }

    protected final C contextAt(Point point) {
        LocatedItem<T, C> located = itemAt(point);
        if (located == null || located.rendered() == null) return null;
        int localX = point.x - located.bounds().x;
        int localY = point.y - located.bounds().y;
        for (HitRegion<C> region : located.rendered().hitRegions()) {
            if (region.shape().contains(localX, localY)) return region.context();
        }
        return null;
    }

    protected final void scrollItemToTop(Object key, int inset) {
        int index = indexOfKey(key);
        if (index < 0) return;
        setScrollY(Math.max(0, heights.prefix(index) - Math.max(0, inset)));
    }

    protected final void revealItem(Object key, int inset) {
        int index = indexOfKey(key);
        if (index < 0) return;
        int top = heights.prefix(index);
        int bottom = top + heights.valueAt(index);
        int viewTop = scrollY;
        int viewBottom = scrollY + getHeight();
        if (top < viewTop) setScrollY(Math.max(0, top - inset));
        else if (bottom > viewBottom) setScrollY(bottom - getHeight() + inset);
    }

    protected final int scrollPosition() { return scrollY; }

    protected final void setScrollPosition(int value) { setScrollY(value); }

    @Override protected final void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            configureGraphics(g);
            int width = usableWidth();
            if (width != renderWidth) {
                renderWidth = width;
                SwingUtilities.invokeLater(this::invalidateRenderedItems);
            }
            if (items.isEmpty()) {
                paintEmpty(g, new Rectangle(0, 0, width, getHeight()));
                paintScrollbar(g);
                return;
            }
            int first = heights.indexAt(scrollY);
            if (first < 0) first = 0;
            int y = heights.prefix(first) - scrollY;
            for (int index = first; index < items.size() && y < getHeight(); index++) {
                Item<T> item = items.get(index);
                int height = heights.valueAt(index);
                Rectangle bounds = new Rectangle(0, y, width, height);
                Cached<C> cached = cacheByKey.get(item.key());
                g.setColor(getBackground());
                g.fillRect(bounds.x, bounds.y, bounds.width, bounds.height);
                if (cached == null || cached.width != width) {
                    paintPlaceholder(g, item, bounds);
                } else {
                    g.drawImage(cached.rendered.image(), bounds.x, bounds.y, null);
                }
                paintItemOverlay(g, new LocatedItem<>(index, item, bounds,
                        cached == null ? null : cached.rendered));
                y += height;
            }
            paintScrollbar(g);
        } finally {
            g.dispose();
        }
    }

    protected void paintEmpty(Graphics2D graphics, Rectangle bounds) {}

    protected void configureGraphics(Graphics2D graphics) {
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    }

    @Override protected void processMouseWheelEvent(MouseWheelEvent event) {
        int amount = event.getUnitsToScroll() * UNIT_SCROLL / 3;
        setScrollY(scrollY + amount);
        event.consume();
    }

    @Override protected void processMouseEvent(MouseEvent event) {
        super.processMouseEvent(event);
        if (event.getID() == MouseEvent.MOUSE_PRESSED
                && SwingUtilities.isLeftMouseButton(event)
                && thumbBounds().contains(event.getPoint())) {
            draggingThumb = true;
            dragOffset = event.getY() - thumbBounds().y;
            event.consume();
        } else if (event.getID() == MouseEvent.MOUSE_RELEASED) {
            draggingThumb = false;
        }
    }

    @Override protected void processMouseMotionEvent(MouseEvent event) {
        super.processMouseMotionEvent(event);
        if (draggingThumb && event.getID() == MouseEvent.MOUSE_DRAGGED) {
            Rectangle track = trackBounds();
            Rectangle thumb = thumbBounds();
            int travel = Math.max(1, track.height - thumb.height);
            int thumbY = Math.max(track.y,
                    Math.min(track.y + travel, event.getY() - dragOffset));
            double ratio = (thumbY - track.y) / (double) travel;
            setScrollY((int) Math.round(ratio * maxScroll()));
            event.consume();
        }
    }

    @Override protected void processComponentEvent(ComponentEvent event) {
        super.processComponentEvent(event);
        if (event.getID() == ComponentEvent.COMPONENT_RESIZED
                && usableWidth() != renderWidth) {
            invalidateRenderedItems();
        }
    }

    @Override public void removeNotify() {
        renderer.shutdownNow();
        super.removeNotify();
    }

    private void scheduleRenderWorker(List<Item<T>> snapshot,
                                      List<Integer> indices,
                                      int width,
                                      long targetGeneration) {
        if (snapshot.isEmpty() || indices.isEmpty() || width <= 0) return;
        List<Item<T>> stableItems = List.copyOf(snapshot);
        List<Integer> stableIndices = List.copyOf(indices);
        renderer.execute(() -> renderLoop(
                targetGeneration, stableItems, stableIndices, width));
    }

    private void renderLoop(long targetGeneration,
                            List<Item<T>> snapshot,
                            List<Integer> indices,
                            int width) {
        for (int index : indices) {
            if (Thread.currentThread().isInterrupted()
                    || targetGeneration != generation.get()) return;
            Item<T> item = snapshot.get(index);
            RenderedItem<C> rendered;
            try {
                rendered = renderItem(item, width);
            } catch (RuntimeException error) {
                rendered = fallback(item, width, error);
            }
            RenderedItem<C> completed = rendered;
            SwingUtilities.invokeLater(() -> acceptRendered(
                    targetGeneration, index, item, width, completed));
        }
    }

    protected RenderedItem<C> fallback(Item<T> item, int width,
                                       RuntimeException error) {
        BufferedImage image = new BufferedImage(Math.max(1, width),
                item.estimatedHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            configureGraphics(g);
            g.setColor(getForeground());
            g.drawString("Unable to render item", 12, 22);
        } finally {
            g.dispose();
        }
        return new RenderedItem<>(image, image.getHeight(), List.of());
    }

    private void acceptRendered(long targetGeneration, int index, Item<T> item,
                                int width, RenderedItem<C> rendered) {
        if (targetGeneration != generation.get() || index >= items.size()
                || !Objects.equals(items.get(index).key(), item.key())
                || width != renderWidth) return;
        cacheByKey.put(item.key(), new Cached<>(width, rendered));
        int oldHeight = heights.valueAt(index);
        int delta = rendered.height() - oldHeight;
        if (delta != 0) {
            heights.add(index, delta);
            contentHeight += delta;
            scrollY = clampScroll(scrollY);
        }
        int y = heights.prefix(index) - scrollY;
        if (delta == 0) {
            repaint(0, y, usableWidth(), rendered.height());
        } else {
            // A height correction moves every following row. Repaint the whole
            // viewport so pixels and hit geometry cannot remain at stale positions.
            repaint();
        }
        repaint(trackBounds());
    }

    private int indexOfKey(Object key) {
        for (int index = 0; index < items.size(); index++) {
            if (Objects.equals(items.get(index).key(), key)) return index;
        }
        return -1;
    }

    private void setScrollY(int value) {
        int next = clampScroll(value);
        if (next == scrollY) return;
        scrollY = next;
        repaint();
    }

    private int clampScroll(int value) {
        return Math.max(0, Math.min(value, maxScroll()));
    }

    private int maxScroll() { return Math.max(0, contentHeight - getHeight()); }

    private int usableWidth() {
        return Math.max(1, getWidth() - SCROLLBAR_WIDTH - 2);
    }

    private Rectangle trackBounds() {
        return new Rectangle(Math.max(0, getWidth() - SCROLLBAR_WIDTH), 0,
                SCROLLBAR_WIDTH, Math.max(1, getHeight()));
    }

    private Rectangle thumbBounds() {
        Rectangle track = trackBounds();
        if (contentHeight <= getHeight() || contentHeight <= 0) {
            return new Rectangle(track.x + 2, track.y + 2,
                    Math.max(4, track.width - 4), Math.max(1, track.height - 4));
        }
        int height = Math.max(MIN_THUMB_HEIGHT,
                (int) Math.round(track.height * (getHeight() / (double) contentHeight)));
        height = Math.min(track.height, height);
        int travel = Math.max(0, track.height - height);
        int y = track.y + (int) Math.round(travel * (scrollY / (double) maxScroll()));
        return new Rectangle(track.x + 2, y,
                Math.max(4, track.width - 4), height);
    }

    private void paintScrollbar(Graphics2D g) {
        Rectangle track = trackBounds();
        Color base = getBackground();
        g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), 150));
        g.fillRect(track.x, track.y, track.width, track.height);
        if (contentHeight <= getHeight()) return;
        Rectangle thumb = thumbBounds();
        Color foreground = getForeground();
        g.setColor(new Color(foreground.getRed(), foreground.getGreen(),
                foreground.getBlue(), draggingThumb ? 180 : 110));
        g.fillRoundRect(thumb.x, thumb.y, thumb.width, thumb.height, 8, 8);
    }

    private void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Virtual list mutations must run on the EDT");
        }
    }

    private record Cached<C>(int width, RenderedItem<C> rendered) {}

    private static final class FenwickTree {
        private final int[] tree;
        private final int[] values;

        private FenwickTree(int size) {
            tree = new int[size + 1];
            values = new int[size];
        }

        static FenwickTree from(int[] values) {
            FenwickTree result = new FenwickTree(values.length);
            for (int index = 0; index < values.length; index++) {
                result.values[index] = values[index];
                result.addInternal(index, values[index]);
            }
            return result;
        }

        void add(int index, int delta) {
            values[index] += delta;
            addInternal(index, delta);
        }

        int valueAt(int index) { return values[index]; }

        int prefix(int count) {
            int sum = 0;
            for (int index = count; index > 0; index -= index & -index) {
                sum += tree[index];
            }
            return sum;
        }

        int total() { return prefix(values.length); }

        int indexAt(int y) {
            if (values.length == 0) return -1;
            int target = Math.max(0, Math.min(y, Math.max(0, total() - 1)));
            int index = 0;
            int sum = 0;
            int bit = Integer.highestOneBit(values.length);
            while (bit != 0) {
                int next = index + bit;
                if (next <= values.length && sum + tree[next] <= target) {
                    index = next;
                    sum += tree[next];
                }
                bit >>= 1;
            }
            return Math.min(index, values.length - 1);
        }

        private void addInternal(int index, int delta) {
            for (int cursor = index + 1; cursor < tree.length;
                 cursor += cursor & -cursor) {
                tree[cursor] += delta;
            }
        }
    }
}
