package app.projection;

import java.util.Optional;
import java.util.prefs.Preferences;

/** Persists the last authoritatively observed local Arena player name. */
final class LocalPlayerStore {
    private static final String KEY = "playerName";
    private final Preferences preferences;

    LocalPlayerStore() {
        this(Preferences.userRoot().node("arena-log-viewer/player"));
    }

    LocalPlayerStore(Preferences preferences) {
        this.preferences = preferences;
    }

    Optional<String> load() {
        String value = preferences.get(KEY, "").trim();
        return value.isEmpty() ? Optional.empty() : Optional.of(value);
    }

    void save(String playerName) {
        if (playerName == null || playerName.isBlank()) return;
        preferences.put(KEY, playerName.trim());
    }
}
