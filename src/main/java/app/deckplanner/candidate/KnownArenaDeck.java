package app.deckplanner.candidate;

/** Read-only Arena deck option exposed to Deck Planner import UI. */
public record KnownArenaDeck(String id, String name, String deckText) {
    @Override public String toString() {
        return name == null || name.isBlank() ? id : name;
    }
}
