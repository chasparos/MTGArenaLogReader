package app.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Project-owned component surface for small ordered card collections.
 *
 * <p>Rows are ordinary Swing components rather than list-cell renderers. The surface owns
 * selection and insertion-point drag/drop mechanics while callers own card-specific rendering,
 * grouping metadata, and persistence. This keeps future group headers and planner annotations
 * possible without coupling them to {@link JList}.</p>
 */
public final class CardCollectionSurface extends JPanel {
    public interface Row {
        String identity();
        JComponent component();
        void setSelected(boolean selected);
    }

    private final List<Row> rows = new ArrayList<>();
    private Consumer<Optional<String>> selectionListener = ignored -> { };
    private BiConsumer<String, Integer> moveHandler = (identity, index) -> { };
    private String selectedIdentity;

    public CardCollectionSurface() {
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setOpaque(true);
        setTransferHandler(new SurfaceTransferHandler());
    }

    public JScrollPane createScrollPane() {
        JScrollPane scroll = new JScrollPane(this,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getViewport().setOpaque(true);
        scroll.getVerticalScrollBar().setUI(new AppScrollBarUI());
        scroll.getVerticalScrollBar().setUnitIncrement(48);
        scroll.getVerticalScrollBar().getModel().addChangeListener(
                event -> syncScrollbarEnabled(scroll.getVerticalScrollBar()));
        syncScrollbarEnabled(scroll.getVerticalScrollBar());
        return scroll;
    }

    public void setRows(List<? extends Row> nextRows) {
        assertEdt();
        String previousSelection = selectedIdentity;
        removeAll();
        rows.clear();
        if (nextRows != null) {
            for (Row row : nextRows) {
                Row accepted = Objects.requireNonNull(row);
                rows.add(accepted);
                installInteraction(accepted);
                add(accepted.component());
            }
        }
        selectedIdentity = rows.stream()
                .map(Row::identity)
                .filter(identity -> Objects.equals(identity, previousSelection))
                .findFirst().orElse(null);
        refreshSelection();
        revalidate();
        repaint();
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

    public void setSelectionListener(Consumer<Optional<String>> listener) {
        selectionListener = listener == null ? ignored -> { } : listener;
    }

    public void setMoveHandler(BiConsumer<String, Integer> handler) {
        moveHandler = handler == null ? (identity, index) -> { } : handler;
    }

    public int insertionIndex(Point point) {
        int y = point == null ? Integer.MAX_VALUE : point.y;
        for (int index = 0; index < rows.size(); index++) {
            Rectangle bounds = rows.get(index).component().getBounds();
            if (y < bounds.y + bounds.height / 2) return index;
        }
        return rows.size();
    }

    private void installInteraction(Row row) {
        JComponent component = row.component();
        MouseAdapter mouse = new MouseAdapter() {
            private Point pressed;

            @Override public void mousePressed(MouseEvent event) {
                pressed = event.getPoint();
                select(row.identity());
            }

            @Override public void mouseDragged(MouseEvent event) {
                if (pressed == null || pressed.distance(event.getPoint()) < 4) return;
                if (event.getComponent() instanceof JComponent source) {
                    source.getTransferHandler().exportAsDrag(source, event, TransferHandler.MOVE);
                }
                pressed = null;
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

    private void select(String identity) {
        selectedIdentity = Objects.equals(selectedIdentity, identity) ? null : identity;
        refreshSelection();
        selectionListener.accept(selectedIdentity());
    }

    private void refreshSelection() {
        for (Row row : rows) {
            row.setSelected(Objects.equals(row.identity(), selectedIdentity));
        }
    }

    private static void syncScrollbarEnabled(JScrollBar scrollBar) {
        BoundedRangeModel range = scrollBar.getModel();
        scrollBar.setEnabled(range.getExtent() < range.getMaximum() - range.getMinimum());
    }

    private static void assertEdt() {
        if (!SwingUtilities.isEventDispatchThread()) {
            throw new IllegalStateException("Card collection surface must be used on EDT");
        }
    }

    private final class SurfaceTransferHandler extends TransferHandler {
        @Override protected Transferable createTransferable(JComponent component) {
            for (Row row : rows) {
                if (row.component() == component) return new StringSelection(row.identity());
            }
            return selectedIdentity().map(StringSelection::new).orElse(null);
        }

        @Override public int getSourceActions(JComponent component) {
            return MOVE;
        }

        @Override public boolean canImport(TransferSupport support) {
            return support.isDrop() && support.isDataFlavorSupported(DataFlavor.stringFlavor);
        }

        @Override public boolean importData(TransferSupport support) {
            if (!canImport(support)) return false;
            try {
                String identity = (String) support.getTransferable()
                        .getTransferData(DataFlavor.stringFlavor);
                Point point = support.getDropLocation().getDropPoint();
                Point surfacePoint = SwingUtilities.convertPoint(
                        support.getComponent(), point, CardCollectionSurface.this);
                moveHandler.accept(identity, insertionIndex(surfacePoint));
                selectedIdentity = identity;
                refreshSelection();
                selectionListener.accept(selectedIdentity());
                return true;
            } catch (Exception error) {
                return false;
            }
        }
    }
}
