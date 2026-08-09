package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Project-owned component surface for small card collections.
 *
 * <p>Rows are ordinary Swing components rather than list-cell renderers. The surface owns
 * selection, wrapping category layout, the project-local scroll surface, and insertion-point
 * drag/drop feedback while callers own card-specific rendering, grouping semantics, and
 * persistence. This keeps planner annotations and future semantic groupings out of Swing list
 * model constraints.</p>
 */
public final class CardCollectionSurface extends JPanel implements Scrollable {
    private static final DataFlavor GROUP_FLAVOR = groupFlavor();

    public interface Row {
        String identity();
        JComponent component();
        void setSelected(boolean selected);
    }

    @FunctionalInterface
    public interface DropHandler {
        void dropped(String source, List<String> identities, int insertionIndex, String groupId);
    }

    @FunctionalInterface
    public interface GroupMoveHandler {
        void moved(String sourceGroupId, String targetGroupId, boolean afterTarget);
    }

    public record Group(String id, String title, List<? extends Row> rows, JComponent trailingHeader, boolean collapsed) {
        public Group(String id, String title, List<? extends Row> rows) {
            this(id, title, rows, null, false);
        }
        public Group(String id, String title, List<? extends Row> rows, JComponent trailingHeader) {
            this(id, title, rows, trailingHeader, false);
        }
        public Group {
            id = id == null ? "" : id;
            title = title == null ? "" : title;
            rows = List.copyOf(rows == null ? List.of() : rows);
        }
    }

    private final List<Row> rows = new ArrayList<>();
    private Consumer<Optional<String>> selectionListener = ignored -> { };
    private BiConsumer<String, Integer> moveHandler = (identity, index) -> { };
    private BiConsumer<List<String>, Integer> moveManyHandler =
            (identities, index) -> identities.forEach(identity -> moveHandler.accept(identity, index));
    private BiConsumer<List<String>, Integer> importHandler = (identities, index) -> { };
    private DropHandler dropHandler;
    private java.util.function.Function<List<String>, Image> dragImageProvider = identities -> null;
    private final java.util.Map<String, String> groupByIdentity = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, JComponent> groupSections = new java.util.LinkedHashMap<>();
    private GroupMoveHandler groupMoveHandler;
    private String transferSource = "surface";
    private final LinkedHashSet<String> selectedIdentities = new LinkedHashSet<>();
    private String selectedIdentity;
    private int selectionAnchor = -1;
    private int dropIndex = -1;
    private String dropGroupId;
    private String groupDropTarget;
    private boolean groupDropAfter;
    private Point dragHoverPoint;
    private int autoScrollDirection;
    private long autoScrollStartedNanos;
    private final Timer autoScrollTimer;
    private JComponent footer;
    private int horizontalGap = 8;
    private int verticalGap = 8;

    public CardCollectionSurface() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setTransferHandler(new SurfaceTransferHandler());
        autoScrollTimer = new Timer(40, event -> autoScrollTick());
        autoScrollTimer.setRepeats(true);
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                revalidateGroupBodies();
            }
        });
    }

    public JScrollPane createScrollPane() {
        JScrollPane scroll = new JScrollPane(this,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(true);
        scroll.getVerticalScrollBar().setUI(new AppScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(52);
        scroll.getVerticalScrollBar().getModel().addChangeListener(
                event -> syncScrollbarEnabled(scroll.getVerticalScrollBar()));
        syncScrollbarEnabled(scroll.getVerticalScrollBar());
        return scroll;
    }

    public void setWrapGaps(int horizontal, int vertical) {
        horizontalGap = Math.max(0, horizontal);
        verticalGap = Math.max(0, vertical);
    }

    public void setRows(List<? extends Row> nextRows) {
        setGroups(List.of(new Group("", "", nextRows)));
    }

    public void setGroups(List<Group> groups) {
        assertEdt();
        String previousSelection = selectedIdentity;
        removeAll();
        rows.clear();
        groupByIdentity.clear();
        groupSections.clear();
        clearDropMarker();

        if (groups != null) {
            for (Group group : groups) {
                if (group == null || group.rows().isEmpty()) continue;
                JPanel body = new JPanel(new WrapLayout(FlowLayout.LEFT, horizontalGap, verticalGap));
                body.setName("card-collection-group-body-" + group.id());
                body.setOpaque(false);
                body.setAlignmentX(Component.LEFT_ALIGNMENT);

                JPanel section = new JPanel(new BorderLayout(0, 1));
                section.setName("card-collection-group-section-" + group.id());
                section.setOpaque(false);
                section.setAlignmentX(Component.LEFT_ALIGNMENT);
                section.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
                groupSections.put(group.id(), section);
                if (!group.title().isBlank() || group.trailingHeader() != null) {
                    JPanel header = new JPanel(new BorderLayout(4, 0));
                    header.setName("card-collection-group-header-" + group.id());
                    header.setOpaque(false);
                    JLabel heading = new JLabel(group.title());
                    heading.setName("card-collection-group-" + group.id());
                    heading.setFont(heading.getFont().deriveFont(Font.BOLD));
                    heading.setBorder(BorderFactory.createEmptyBorder(3, 2, 0, 2));
                    header.add(heading, BorderLayout.CENTER);
                    installGroupWhitespaceInteraction(heading, null, group.id());
                    if (group.trailingHeader() != null) header.add(group.trailingHeader(), BorderLayout.EAST);
                    section.add(header, BorderLayout.NORTH);
                }

                for (Row row : group.rows()) {
                    Row accepted = Objects.requireNonNull(row);
                    rows.add(accepted);
                    groupByIdentity.put(accepted.identity(), group.id());
                    installInteraction(accepted);
                    body.add(accepted.component());
                }
                body.setVisible(!group.collapsed());
                section.add(body, BorderLayout.CENTER);
                installGroupWhitespaceInteraction(section, null, group.id());
                installGroupWhitespaceInteraction(body, null, group.id());
                if (section.getComponentCount() > 1 && section.getComponent(0) instanceof JComponent header) {
                    installGroupWhitespaceInteraction(header, null, group.id());
                }
                add(section);
            }
        }

        if (footer != null) {
            footer.setAlignmentX(Component.LEFT_ALIGNMENT);
            footer.setMaximumSize(new Dimension(Integer.MAX_VALUE,
                    Math.max(1, footer.getPreferredSize().height)));
            add(footer);
        }

        selectedIdentities.retainAll(rows.stream().map(Row::identity).toList());
        if (selectedIdentities.isEmpty() && previousSelection != null
                && rows.stream().anyMatch(row -> Objects.equals(row.identity(), previousSelection))) {
            selectedIdentities.add(previousSelection);
        }
        if (selectedIdentities.isEmpty()) {
            selectedIdentity = null;
            selectionAnchor = -1;
        } else {
            selectedIdentity = rows.stream().map(Row::identity)
                    .filter(selectedIdentities::contains).reduce((left, right) -> right).orElse(null);
            selectionAnchor = indexOfIdentity(selectedIdentity);
        }
        refreshSelection();
        revalidate();
        repaint();
    }

    private void revalidateGroupBodies() {
        for (Component child : getComponents()) {
            if (!(child instanceof Container section)) continue;
            for (Component nested : section.getComponents()) {
                if (nested instanceof JComponent component
                        && component.getName() != null
                        && component.getName().startsWith("card-collection-group-body-")) {
                    component.revalidate();
                }
            }
        }
        revalidate();
    }


    public void setFooter(JComponent footer) {
        this.footer = footer;
    }

    public List<String> identities() {
        return rows.stream().map(Row::identity).toList();
    }

    public List<JComponent> rowComponents() {
        return rows.stream().map(Row::component).toList();
    }

    public Optional<String> selectedIdentity() {
        return Optional.ofNullable(selectedIdentity);
    }

    public List<String> selectedIdentities() {
        return rows.stream().map(Row::identity).filter(selectedIdentities::contains).toList();
    }

    public void setSelectionListener(Consumer<Optional<String>> listener) {
        selectionListener = listener == null ? ignored -> { } : listener;
    }

    public void setMoveHandler(BiConsumer<String, Integer> handler) {
        moveHandler = handler == null ? (identity, index) -> { } : handler;
        moveManyHandler = (identities, index) -> {
            int target = index;
            for (String identity : identities) moveHandler.accept(identity, target++);
        };
    }

    public void setMoveManyHandler(BiConsumer<List<String>, Integer> handler) {
        moveManyHandler = handler == null ? (identities, index) -> { } : handler;
    }

    public void setImportHandler(BiConsumer<List<String>, Integer> handler) {
        importHandler = handler == null ? (identities, index) -> { } : handler;
    }

    public void setDropHandler(DropHandler handler) {
        dropHandler = handler;
    }

    public void setGroupMoveHandler(GroupMoveHandler handler) {
        groupMoveHandler = handler;
    }

    public void setTransferSource(String source) {
        transferSource = source == null || source.isBlank() ? "surface" : source;
    }

    public void setDragImageProvider(java.util.function.Function<List<String>, Image> provider) {
        dragImageProvider = provider == null ? identities -> null : provider;
    }

    public int insertionIndex(Point point) {
        if (rows.isEmpty()) return 0;
        Point wanted = point == null ? new Point(Integer.MAX_VALUE, Integer.MAX_VALUE) : point;
        for (int index = 0; index < rows.size(); index++) {
            Rectangle bounds = rowBounds(rows.get(index));
            int centerY = bounds.y + bounds.height / 2;
            if (wanted.y < centerY) return index;
            if (wanted.y <= bounds.y + bounds.height
                    && wanted.x < bounds.x + bounds.width / 2) {
                return index;
            }
        }
        return rows.size();
    }

    private String groupForInsertion(int insertionIndex) {
        if (rows.isEmpty()) return null;
        int index = insertionIndex >= rows.size()
                ? rows.size() - 1
                : Math.max(0, insertionIndex);
        return groupByIdentity.get(rows.get(index).identity());
    }

    private String groupAt(Point point) {
        if (point == null) return null;
        for (var entry : groupSections.entrySet()) {
            JComponent section = entry.getValue();
            if (!section.isVisible()) continue;
            Rectangle bounds = SwingUtilities.convertRectangle(
                    section.getParent(), section.getBounds(), this);
            if (bounds.contains(point)) return entry.getKey();
        }
        return null;
    }

    private int insertionIndex(Point point, String groupId) {
        if (groupId == null) return insertionIndex(point);
        List<Integer> indices = new ArrayList<>();
        for (int index = 0; index < rows.size(); index++) {
            if (Objects.equals(groupId, groupByIdentity.get(rows.get(index).identity()))) {
                indices.add(index);
            }
        }
        if (indices.isEmpty()) return rows.size();
        Point wanted = point == null ? new Point(Integer.MAX_VALUE, Integer.MAX_VALUE) : point;
        for (int index : indices) {
            Rectangle bounds = rowBounds(rows.get(index));
            int centerY = bounds.y + bounds.height / 2;
            if (wanted.y < centerY) return index;
            if (wanted.y <= bounds.y + bounds.height
                    && wanted.x < bounds.x + bounds.width / 2) {
                return index;
            }
        }
        return indices.getLast() + 1;
    }

    private Rectangle rowBounds(Row row) {
        JComponent component = row.component();
        Container parent = component.getParent();
        if (parent == null) return component.getBounds();
        return SwingUtilities.convertRectangle(parent, component.getBounds(), this);
    }

    private void installInteraction(Row row) {
        JComponent component = row.component();
        MouseAdapter mouse = new MouseAdapter() {
            private Point pressed;
            private boolean preserveSelectionForDrag;

            @Override public void mousePressed(MouseEvent event) {
                pressed = event.getPoint();
                preserveSelectionForDrag = selectedIdentities.contains(row.identity())
                        && !event.isControlDown() && !event.isShiftDown()
                        && selectedIdentities.size() > 1;
                if (!preserveSelectionForDrag) {
                    select(row.identity(), event.isControlDown(), event.isShiftDown());
                }
            }

            @Override public void mouseReleased(MouseEvent event) {
                if (preserveSelectionForDrag && pressed != null) {
                    select(row.identity(), false, false);
                }
                pressed = null;
                preserveSelectionForDrag = false;
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (pressed == null || pressed.distance(event.getPoint()) < 4) return;
                if (event.getComponent() instanceof JComponent source) {
                    TransferHandler handler = source.getTransferHandler();
                    if (handler instanceof SurfaceTransferHandler surfaceHandler) {
                        Image image = dragImageProvider.apply(selectedIdentities());
                        surfaceHandler.setDragImage(image);
                        if (image != null) surfaceHandler.setDragImageOffset(
                                new Point(Math.min(24, image.getWidth(null) / 3),
                                        Math.min(18, image.getHeight(null) / 3)));
                    }
                    handler.exportAsDrag(source, event, TransferHandler.MOVE);
                }
                pressed = null;
                preserveSelectionForDrag = false;
            }
        };
        installMouseInteraction(component, mouse);
    }

    private void installMouseInteraction(Component component, MouseAdapter mouse) {
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
        if (component instanceof JComponent swing) {
            swing.setTransferHandler(new SurfaceTransferHandler());
        }
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                installMouseInteraction(child, mouse);
            }
        }
    }

    private void installGroupWhitespaceInteraction(JComponent component,
                                                   JComponent ignoredChild,
                                                   String groupId) {
        if (groupMoveHandler == null || component == null || groupId == null) return;
        GroupWhitespaceTransferHandler handler = new GroupWhitespaceTransferHandler(groupId);
        component.setTransferHandler(handler);
        MouseAdapter mouse = new MouseAdapter() {
            private Point pressed;

            @Override public void mousePressed(MouseEvent event) {
                pressed = event.getPoint();
            }

            @Override public void mouseReleased(MouseEvent event) {
                pressed = null;
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (pressed == null || pressed.distance(event.getPoint()) < 5) return;
                handler.exportAsDrag(component, event, TransferHandler.MOVE);
                pressed = null;
            }
        };
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
    }

    private void select(String identity, boolean control, boolean shift) {
        int index = indexOfIdentity(identity);
        if (index < 0) return;
        if (shift) {
            int anchor = selectionAnchor >= 0 ? selectionAnchor : index;
            if (!control) selectedIdentities.clear();
            int from = Math.min(anchor, index);
            int to = Math.max(anchor, index);
            for (int current = from; current <= to; current++) {
                selectedIdentities.add(rows.get(current).identity());
            }
        } else if (control) {
            if (!selectedIdentities.remove(identity)) selectedIdentities.add(identity);
            selectionAnchor = index;
        } else {
            selectedIdentities.clear();
            selectedIdentities.add(identity);
            selectionAnchor = index;
        }
        selectedIdentity = selectedIdentities.contains(identity) ? identity
                : selectedIdentities.stream().reduce((left, right) -> right).orElse(null);
        refreshSelection();
        selectionListener.accept(selectedIdentity());
    }

    private int indexOfIdentity(String identity) {
        for (int index = 0; index < rows.size(); index++) {
            if (Objects.equals(rows.get(index).identity(), identity)) return index;
        }
        return -1;
    }

    private void refreshSelection() {
        for (Row row : rows) {
            row.setSelected(selectedIdentities.contains(row.identity()));
        }
    }

    private void setDropIndex(int value) {
        int normalized = Math.max(0, Math.min(value, rows.size()));
        if (dropIndex == normalized) return;
        dropIndex = normalized;
        repaint();
    }

    private void clearDropMarker() {
        if (dropIndex == -1 && dropGroupId == null
                && groupDropTarget == null && autoScrollDirection == 0) return;
        dropIndex = -1;
        dropGroupId = null;
        groupDropTarget = null;
        stopAutoScroll();
        repaint();
    }

    private void updateAutoScroll(Point point) {
        dragHoverPoint = point;
        Rectangle visible = getVisibleRect();
        if (point == null || visible.height <= 0) {
            stopAutoScroll();
            return;
        }
        int zone = Math.max(28, Math.min(52, visible.height / 6));
        int direction = point.y < visible.y + zone ? -1
                : point.y > visible.y + visible.height - zone ? 1 : 0;
        if (direction == 0) {
            stopAutoScroll();
            repaint(visible);
            return;
        }
        if (autoScrollDirection != direction) {
            autoScrollDirection = direction;
            autoScrollStartedNanos = System.nanoTime();
        }
        if (!autoScrollTimer.isRunning()) autoScrollTimer.start();
        repaint(visible);
    }

    private void stopAutoScroll() {
        autoScrollDirection = 0;
        dragHoverPoint = null;
        if (autoScrollTimer.isRunning()) autoScrollTimer.stop();
    }

    private void autoScrollTick() {
        if (autoScrollDirection == 0 || !isShowing()) {
            stopAutoScroll();
            return;
        }
        double elapsed = Math.min(2.0,
                Math.max(0.0, (System.nanoTime() - autoScrollStartedNanos) / 1_000_000_000.0));
        double progress = elapsed / 2.0;
        int step = 4 + (int) Math.round(24 * progress * progress);
        Rectangle visible = getVisibleRect();
        int maxY = Math.max(0, getHeight() - visible.height);
        int targetY = Math.max(0, Math.min(maxY, visible.y + autoScrollDirection * step));
        if (targetY == visible.y) return;
        scrollRectToVisible(new Rectangle(visible.x, targetY,
                Math.max(1, visible.width), Math.max(1, visible.height)));
    }

    @Override
    protected void paintChildren(Graphics graphics) {
        super.paintChildren(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            Color marker = AppColors.color("List.selectionBackground", new Color(0x6D7F9B));
            g.setColor(marker);
            g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

            if (groupDropTarget != null) {
                JComponent section = groupSections.get(groupDropTarget);
                if (section != null && section.getParent() != null) {
                    Rectangle bounds = SwingUtilities.convertRectangle(
                            section.getParent(), section.getBounds(), this);
                    int y = groupDropAfter ? bounds.y + bounds.height : bounds.y;
                    g.drawLine(Math.max(4, bounds.x + 4), y,
                            Math.max(bounds.x + 8, bounds.x + bounds.width - 4), y);
                }
            } else if (dropIndex >= 0 && !rows.isEmpty()) {
                Rectangle anchor;
                int x;
                if (dropIndex >= rows.size()) {
                    anchor = rowBounds(rows.getLast());
                    x = anchor.x + anchor.width + 2;
                } else {
                    anchor = rowBounds(rows.get(dropIndex));
                    if (dropIndex > 0) {
                        Rectangle previous = rowBounds(rows.get(dropIndex - 1));
                        boolean sameVisualRow = Math.abs(
                                (previous.y + previous.height / 2) - (anchor.y + anchor.height / 2))
                                < Math.max(previous.height, anchor.height) / 2;
                        x = sameVisualRow
                                ? (previous.x + previous.width + anchor.x) / 2
                                : Math.max(2, anchor.x - 2);
                    } else {
                        x = Math.max(2, anchor.x - 2);
                    }
                }
                int y1 = anchor.y + 2;
                int y2 = anchor.y + Math.max(3, anchor.height - 2);
                g.drawLine(x, y1, x, y2);
            }

            if (autoScrollDirection != 0) {
                Rectangle visible = getVisibleRect();
                int width = 46;
                int height = 22;
                int x = visible.x + Math.max(2, (visible.width - width) / 2);
                int y = autoScrollDirection < 0
                        ? visible.y + 5 : visible.y + visible.height - height - 5;
                Color background = AppColors.color("Panel.background", new Color(0x202328));
                g.setColor(new Color(background.getRed(), background.getGreen(),
                        background.getBlue(), 210));
                g.fillRoundRect(x, y, width, height, height, height);
                g.setColor(marker);
                Path2D arrow = new Path2D.Float();
                if (autoScrollDirection < 0) {
                    arrow.moveTo(x + width / 2.0, y + 6);
                    arrow.lineTo(x + width / 2.0 - 7, y + 15);
                    arrow.lineTo(x + width / 2.0 + 7, y + 15);
                } else {
                    arrow.moveTo(x + width / 2.0, y + height - 6);
                    arrow.lineTo(x + width / 2.0 - 7, y + height - 15);
                    arrow.lineTo(x + width / 2.0 + 7, y + height - 15);
                }
                arrow.closePath();
                g.fill(arrow);
            }
        } finally {
            g.dispose();
        }
    }

    @Override public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
        return 52;
    }

    @Override public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
        return orientation == SwingConstants.VERTICAL
                ? Math.max(52, visibleRect.height - 52)
                : Math.max(120, visibleRect.width - 120);
    }

    @Override public boolean getScrollableTracksViewportWidth() {
        return true;
    }

    @Override public boolean getScrollableTracksViewportHeight() {
        return false;
    }

    private static void syncScrollbarEnabled(JScrollBar scrollBar) {
        BoundedRangeModel range = scrollBar.getModel();
        scrollBar.setEnabled(range.getExtent() < range.getMaximum() - range.getMinimum());
    }

    private static DataFlavor groupFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=" + String.class.getName());
        } catch (ClassNotFoundException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Card collection surface must be used on EDT");
        }
    }

    private final class GroupWhitespaceTransferHandler extends TransferHandler {
        private final String groupId;

        GroupWhitespaceTransferHandler(String groupId) {
            this.groupId = groupId;
        }

        @Override protected Transferable createTransferable(JComponent component) {
            return new Transferable() {
                @Override public DataFlavor[] getTransferDataFlavors() {
                    return new DataFlavor[] { GROUP_FLAVOR };
                }

                @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
                    return GROUP_FLAVOR.equals(flavor);
                }

                @Override public Object getTransferData(DataFlavor flavor)
                        throws java.awt.datatransfer.UnsupportedFlavorException {
                    if (!GROUP_FLAVOR.equals(flavor)) {
                        throw new java.awt.datatransfer.UnsupportedFlavorException(flavor);
                    }
                    return groupId;
                }
            };
        }

        @Override public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override public boolean canImport(TransferSupport support) {
            if (!support.isDrop()) return false;
            boolean groupMove = groupMoveHandler != null
                    && support.isDataFlavorSupported(GROUP_FLAVOR);
            boolean cardMove = support.isDataFlavorSupported(CardDragTransfer.FLAVOR);
            if (!groupMove && !cardMove) {
                clearDropMarker();
                return false;
            }
            Point local = support.getDropLocation().getDropPoint();
            Point surfacePoint = SwingUtilities.convertPoint(
                    support.getComponent(), local, CardCollectionSurface.this);
            updateAutoScroll(surfacePoint);
            if (groupMove) {
                JComponent section = groupSections.get(groupId);
                Rectangle bounds = section == null || section.getParent() == null
                        ? new Rectangle(surfacePoint.x, surfacePoint.y, 1, 1)
                        : SwingUtilities.convertRectangle(
                                section.getParent(), section.getBounds(), CardCollectionSurface.this);
                groupDropTarget = groupId;
                groupDropAfter = surfacePoint.y >= bounds.y + bounds.height / 2;
                dropIndex = -1;
                repaint();
            } else {
                groupDropTarget = null;
                dropGroupId = groupId;
                setDropIndex(insertionIndex(surfacePoint, groupId));
            }
            return true;
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                if (support.isDataFlavorSupported(GROUP_FLAVOR)) {
                    String sourceGroup = (String) support.getTransferable()
                            .getTransferData(GROUP_FLAVOR);
                    if (groupMoveHandler != null && !Objects.equals(sourceGroup, groupId)) {
                        groupMoveHandler.moved(sourceGroup, groupId, groupDropAfter);
                    }
                    return true;
                }
                CardDragTransfer.Payload payload = CardDragTransfer.read(support.getTransferable());
                int insertion = dropIndex < 0 ? rows.size() : dropIndex;
                if (dropHandler != null) {
                    dropHandler.dropped(payload.source(), payload.identities(), insertion, groupId);
                }
                return true;
            } catch (Exception error) {
                return false;
            } finally {
                clearDropMarker();
            }
        }

        @Override protected void exportDone(JComponent source, Transferable data, int action) {
            clearDropMarker();
        }
    }

    private final class SurfaceTransferHandler extends TransferHandler {
        @Override protected Transferable createTransferable(JComponent component) {
            String dragged = rows.stream()
                    .filter(row -> row.component() == component
                            || SwingUtilities.isDescendingFrom(component, row.component()))
                    .map(Row::identity).findFirst().orElse(selectedIdentity);
            if (dragged == null) return null;
            if (!selectedIdentities.contains(dragged)) {
                selectedIdentities.clear();
                selectedIdentities.add(dragged);
                selectedIdentity = dragged;
                selectionAnchor = indexOfIdentity(dragged);
                refreshSelection();
                selectionListener.accept(selectedIdentity());
            }
            return new CardDragTransfer(transferSource, selectedIdentities());
        }

        @Override public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override public boolean canImport(TransferSupport support) {
            boolean accepted = support.isDrop()
                    && support.isDataFlavorSupported(CardDragTransfer.FLAVOR);
            if (!accepted) {
                clearDropMarker();
                return false;
            }
            try {
                CardDragTransfer.Payload payload = CardDragTransfer.read(support.getTransferable());
                if (payload.identities().isEmpty()) {
                    clearDropMarker();
                    return false;
                }
            } catch (Exception error) {
                clearDropMarker();
                return false;
            }
            Point point = support.getDropLocation().getDropPoint();
            Point surfacePoint = SwingUtilities.convertPoint(
                    support.getComponent(), point, CardCollectionSurface.this);
            dropGroupId = groupAt(surfacePoint);
            setDropIndex(insertionIndex(surfacePoint, dropGroupId));
            updateAutoScroll(surfacePoint);
            return true;
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                CardDragTransfer.Payload payload = CardDragTransfer.read(support.getTransferable());
                int insertion = dropIndex < 0 ? rows.size() : dropIndex;
                String groupId = dropGroupId != null ? dropGroupId : groupForInsertion(insertion);
                if (dropHandler != null) {
                    dropHandler.dropped(payload.source(), payload.identities(), insertion, groupId);
                } else if (Objects.equals(payload.source(), transferSource)) {
                    moveManyHandler.accept(payload.identities(), insertion);
                } else {
                    importHandler.accept(payload.identities(), insertion);
                }
                selectedIdentities.clear();
                selectedIdentities.addAll(payload.identities());
                selectedIdentities.retainAll(rows.stream().map(Row::identity).toList());
                selectedIdentity = selectedIdentities.stream().reduce((left, right) -> right).orElse(null);
                selectionAnchor = indexOfIdentity(selectedIdentity);
                refreshSelection();
                selectionListener.accept(selectedIdentity());
                return true;
            } catch (Exception error) {
                return false;
            } finally {
                clearDropMarker();
            }
        }

        @Override protected void exportDone(JComponent source, Transferable data, int action) {
            clearDropMarker();
        }
    }

    /** Flow layout whose preferred height follows the viewport width and wraps instead of overflowing. */
    private static final class WrapLayout extends FlowLayout {
        WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int width = target.getWidth();
                JViewport viewport = (JViewport) SwingUtilities.getAncestorOfClass(
                        JViewport.class, target);
                if (viewport != null && viewport.getExtentSize().width > 0) {
                    width = viewport.getExtentSize().width;
                }
                Container parent = target.getParent();
                if (width <= 0 && parent != null) width = parent.getWidth();
                if (width <= 0) {
                    Container surface = (Container) SwingUtilities.getAncestorOfClass(
                            CardCollectionSurface.class, target);
                    if (surface != null) width = surface.getWidth();
                }
                if (width <= 0) width = 640;

                Insets insets = target.getInsets();
                int maxWidth = Math.max(1,
                        width - insets.left - insets.right - getHgap() * 2);
                int rowWidth = 0;
                int rowHeight = 0;
                int totalWidth = 0;
                int totalHeight = 0;

                for (Component component : target.getComponents()) {
                    if (!component.isVisible()) continue;
                    Dimension size = preferred
                            ? component.getPreferredSize() : component.getMinimumSize();
                    int componentWidth = Math.min(size.width, maxWidth);
                    if (rowWidth > 0 && rowWidth + getHgap() + componentWidth > maxWidth) {
                        totalWidth = Math.max(totalWidth, rowWidth);
                        totalHeight += rowHeight + getVgap();
                        rowWidth = 0;
                        rowHeight = 0;
                    }
                    if (rowWidth > 0) rowWidth += getHgap();
                    rowWidth += componentWidth;
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
}
