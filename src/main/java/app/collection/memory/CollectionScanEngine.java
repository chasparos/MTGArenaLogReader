package app.collection.memory;

import java.util.Map;
import java.util.function.Consumer;

/** Internal scanner seam; Windows process access will replace the fake in MSC-02. */
@FunctionalInterface
public interface CollectionScanEngine {
    ScanResult scan(Consumer<String> progress) throws Exception;

    record ScanResult(boolean complete, Map<Long, Integer> copies,
                      Map<Long, Integer> structuralEvidence, String output) {
        public ScanResult {
            copies = Map.copyOf(copies == null ? Map.of() : copies);
            structuralEvidence = Map.copyOf(structuralEvidence == null ? Map.of() : structuralEvidence);
            output = output == null ? "" : output;
        }
        public ScanResult(boolean complete, Map<Long, Integer> copies, String output) {
            this(complete, copies, Map.of(), output);
        }
    }
}
