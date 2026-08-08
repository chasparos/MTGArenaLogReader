package app.ui;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * JVM-local multi-card drag payload with a text fallback for diagnostics and accessibility.
 */
public final class CardDragTransfer implements Transferable {
    public static final DataFlavor FLAVOR;

    static {
        try {
            FLAVOR = new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType
                    + ";class=" + Payload.class.getName());
        } catch (ClassNotFoundException error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    public record Payload(String source, List<String> identities) {
        public Payload {
            source = source == null ? "" : source;
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            if (identities != null) {
                for (String identity : identities) {
                    if (identity != null && !identity.isBlank()) normalized.add(identity.strip());
                }
            }
            identities = List.copyOf(normalized);
        }
    }

    private final Payload payload;

    public CardDragTransfer(String source, Collection<String> identities) {
        payload = new Payload(source, identities == null ? List.of() : new ArrayList<>(identities));
    }

    public Payload payload() {
        return payload;
    }

    @Override public DataFlavor[] getTransferDataFlavors() {
        return new DataFlavor[] { FLAVOR, DataFlavor.stringFlavor };
    }

    @Override public boolean isDataFlavorSupported(DataFlavor flavor) {
        return Objects.equals(FLAVOR, flavor) || Objects.equals(DataFlavor.stringFlavor, flavor);
    }

    @Override public Object getTransferData(DataFlavor flavor)
            throws UnsupportedFlavorException, IOException {
        if (Objects.equals(FLAVOR, flavor)) return payload;
        if (Objects.equals(DataFlavor.stringFlavor, flavor)) {
            return String.join(System.lineSeparator(), payload.identities());
        }
        throw new UnsupportedFlavorException(flavor);
    }

    public static Payload read(Transferable transferable)
            throws UnsupportedFlavorException, IOException {
        if (transferable != null && transferable.isDataFlavorSupported(FLAVOR)) {
            return (Payload) transferable.getTransferData(FLAVOR);
        }
        throw new UnsupportedFlavorException(FLAVOR);
    }

    private CardDragTransfer() {
        payload = new Payload("", List.of());
    }
}
