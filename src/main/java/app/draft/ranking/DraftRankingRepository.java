package app.draft.ranking;

import app.draft.model.DraftCardRating;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class DraftRankingRepository {
    private static final Type RATINGS_TYPE =
            new TypeToken<List<DraftCardRating>>() { }.getType();
    private final Gson gson;
    private final Path directory;

    public DraftRankingRepository(Gson gson, Path directory) {
        this.gson = gson;
        this.directory = directory;
    }

    public List<DraftCardRating> load(String setCode) {
        Path file = file(setCode);
        if (!Files.isRegularFile(file)) return List.of();
        try (Reader reader = Files.newBufferedReader(
                file, StandardCharsets.UTF_8)) {
            List<DraftCardRating> ratings = gson.fromJson(reader, RATINGS_TYPE);
            return ratings == null ? List.of() : List.copyOf(ratings);
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not read draft ranking for " + setCode, error);
        }
    }

    public void save(String setCode, List<DraftCardRating> ratings) {
        try {
            Files.createDirectories(directory);
            try (Writer writer = Files.newBufferedWriter(
                    file(setCode), StandardCharsets.UTF_8)) {
                gson.toJson(List.copyOf(ratings), RATINGS_TYPE, writer);
            }
        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not save draft ranking for " + setCode, error);
        }
    }

    private Path file(String setCode) {
        if (setCode == null || !setCode.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("Invalid set code");
        }
        return directory.resolve(
                setCode.toLowerCase() + "-ranking.json");
    }
}
