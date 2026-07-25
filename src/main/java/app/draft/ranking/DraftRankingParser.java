package app.draft.ranking;

import app.draft.model.DraftCardRating;
import app.draft.model.DraftTier;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DraftRankingParser {
    public List<DraftCardRating> parse(String response) {
        if (response == null || response.isBlank()) {
            throw new IllegalArgumentException("Ranking response is empty");
        }
        List<DraftCardRating> result = new ArrayList<>();
        for (String rawLine : response.split("\\R")) {
            String line = rawLine.strip();
            if (line.startsWith("-")) line = line.substring(1).strip();
            if (!line.toLowerCase(Locale.ROOT).contains("tier=")) continue;
            Map<String, String> fields = fields(line);
            long arenaId = number(fields.get("arenaid"));
            String name = fields.getOrDefault("name", "");
            DraftTier tier = DraftTier.parse(fields.get("tier"));
            if (tier == null || (arenaId <= 0 && name.isBlank())) {
                throw new IllegalArgumentException(
                        "Invalid ranking line: " + rawLine);
            }
            result.add(new DraftCardRating(
                    arenaId,
                    name,
                    tier,
                    fields.getOrDefault("note", "")));
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException(
                    "No ranking rows were found in the clipboard");
        }
        return List.copyOf(result);
    }

    private Map<String, String> fields(String line) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : line.split(";")) {
            int equals = part.indexOf('=');
            if (equals <= 0) continue;
            result.put(
                    part.substring(0, equals).strip().toLowerCase(Locale.ROOT),
                    part.substring(equals + 1).strip());
        }
        return result;
    }

    private long number(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
