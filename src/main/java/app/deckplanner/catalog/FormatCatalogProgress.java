package app.deckplanner.catalog;

public record FormatCatalogProgress(String format, String runId, Phase phase,
                                    int processedCards, int attempts,
                                    String detail) {
    public enum Phase { STARTING, FETCHING_PAGE, ENRICHING, RETRYING, PUBLISHING, COMPLETE, CANCELLED }

    public interface Listener {
        void onProgress(FormatCatalogProgress progress);

        static Listener ignoring() { return ignored -> { }; }
    }
}
