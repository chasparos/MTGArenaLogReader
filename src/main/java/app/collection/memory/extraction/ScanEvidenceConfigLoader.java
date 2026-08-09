package app.collection.memory.extraction;

import com.google.gson.Gson;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Loads scanner-owned known-ID evidence and explicit user-confirmed anchors. */
public final class ScanEvidenceConfigLoader {
    public record Config(String version, Set<Long> knownArenaIds,
                         List<CandidateBlockExtractor.Anchor> anchors) {
        public Config {
            version = Objects.requireNonNull(version);
            knownArenaIds = Set.copyOf(knownArenaIds);
            anchors = List.copyOf(anchors);
        }
    }

    public Config load(Path knownIdsFile, String anchorText) throws IOException {
        Objects.requireNonNull(knownIdsFile);
        Path absolute = knownIdsFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(absolute)) {
            throw new IllegalArgumentException("Known-ID file does not exist: " + absolute
                    + ". Click 'Build from Arena install' first.");
        }
        KnownIdsDocument document;
        try (var reader = Files.newBufferedReader(absolute)) {
            document = new Gson().fromJson(reader, KnownIdsDocument.class);
        }
        if (document == null || document.version == null || document.version.isBlank()) {
            throw new IllegalArgumentException("Known-ID file requires a non-blank version");
        }
        if (document.arenaIds == null || document.arenaIds.isEmpty()) {
            throw new IllegalArgumentException("Known-ID file contains no Arena IDs");
        }
        LinkedHashSet<Long> known = new LinkedHashSet<>();
        for (Long id : document.arenaIds) {
            if (id == null || id < 1_000 || id >= 900_000) {
                throw new IllegalArgumentException("Known-ID file contains an invalid Arena ID: " + id);
            }
            if (!known.add(id)) throw new IllegalArgumentException("Known-ID file contains duplicate ID " + id);
        }
        List<CandidateBlockExtractor.Anchor> anchors = parseAnchors(anchorText, known);
        return new Config(document.version.trim(), known, anchors);
    }

    private static List<CandidateBlockExtractor.Anchor> parseAnchors(
            String text, Set<Long> known) {
        LinkedHashMap<Long, Integer> anchors = new LinkedHashMap<>();
        int lineNumber = 0;
        for (String raw : Objects.requireNonNullElse(text, "").lines().toList()) {
            lineNumber++;
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            String[] fields = line.split("\\s*[,=]\\s*", -1);
            if (fields.length != 2) throw new IllegalArgumentException(
                    "Anchor line " + lineNumber + " must be arenaId=copies");
            long id;
            int copies;
            try {
                id = Long.parseLong(fields[0]);
                copies = Integer.parseInt(fields[1]);
            } catch (NumberFormatException error) {
                throw new IllegalArgumentException("Anchor line " + lineNumber + " is not numeric");
            }
            if (!known.contains(id)) throw new IllegalArgumentException(
                    "Anchor ID " + id + " is absent from the known-ID file");
            if (copies < 1 || copies > 400) throw new IllegalArgumentException(
                    "Anchor copies must be between 1 and 400 on line " + lineNumber);
            if (anchors.putIfAbsent(id, copies) != null) throw new IllegalArgumentException(
                    "Duplicate anchor ID " + id);
        }
        if (anchors.size() < 2) throw new IllegalArgumentException("At least two anchors are required");
        return anchors.entrySet().stream()
                .map(entry -> new CandidateBlockExtractor.Anchor(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static final class KnownIdsDocument {
        String version;
        List<Long> arenaIds;
    }
}
