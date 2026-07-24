package app.archive;

import app.export.MatchAiExporter;
import app.model.session.GameModel;
import app.model.session.MatchSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

public final class MatchArchiveStore {
    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);
    private final Path archiveFolder;
    private final MatchAiExporter exporter;

    public MatchArchiveStore(Path archiveFolder, MatchAiExporter exporter) {
        this.archiveFolder = archiveFolder;
        this.exporter = exporter;
    }

    public void archive(MatchSession match) throws IOException {
        Files.createDirectories(archiveFolder);
        String matchId = match.matchState().getMatchId();
        String safeId = matchId == null ? "unknown" : matchId.replaceAll("[^A-Za-z0-9._-]", "_");
        Path file = archiveFolder.resolve(STAMP.format(Instant.now()) + "-" + safeId + ".txt");
        StringBuilder text = new StringBuilder(exporter.export(match));
        text.append(System.lineSeparator()).append(System.lineSeparator()).append("RAW_RECORDS");
        for (GameModel game : match.gameSnapshot()) {
            text.append(System.lineSeparator()).append("GAME ").append(game.getGameNumber());
            for (String record : game.rawRecordSnapshot()) {
                text.append(System.lineSeparator()).append(record);
            }
        }
        Files.writeString(file, text.toString(), StandardCharsets.UTF_8);
    }
}
