package app.ui;

import org.junit.jupiter.api.Test;

import java.awt.datatransfer.DataFlavor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CardDragTransferTest {
    @Test void preservesSourceAndOrderedDistinctIdentities() throws Exception {
        CardDragTransfer transfer = new CardDragTransfer(
                "catalog", List.of("oracle:a", "oracle:b", "oracle:a"));

        CardDragTransfer.Payload payload = CardDragTransfer.read(transfer);

        assertEquals("catalog", payload.source());
        assertEquals(List.of("oracle:a", "oracle:b"), payload.identities());
        assertTrue(transfer.isDataFlavorSupported(DataFlavor.stringFlavor));
        assertEquals("oracle:a" + System.lineSeparator() + "oracle:b",
                transfer.getTransferData(DataFlavor.stringFlavor));
    }
}
