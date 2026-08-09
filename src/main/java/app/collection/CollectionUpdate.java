package app.collection;

import java.util.List;
import java.util.Map;

/** Application-neutral conversation for guiding and running a collection update. */
public interface CollectionUpdate {
    Session begin(Observer observer);

    @FunctionalInterface interface Observer { void onEvent(Event event); }
    interface Session {
        void respond(Response response);
        void cancel();
    }

    sealed interface Event permits Status, CardsRequired, Completed { }
    record Status(String message) implements Event { }
    record CardsRequired(String instruction, int minimumCards, int recommendedCards,
                         List<CardOption> suggestions) implements Event {
        public CardsRequired { suggestions = List.copyOf(suggestions); }
    }
    record Summary(int catalogCardsExamined, int distinctCardsOwned, int totalCopies,
                   Map<String, Integer> colors, Map<String, Integer> rarities,
                   Map<String, Integer> sets) {
        public Summary {
            colors = Map.copyOf(colors);
            rarities = Map.copyOf(rarities);
            sets = Map.copyOf(sets);
        }
        public static Summary basic(int distinctCardsOwned) {
            return new Summary(0, distinctCardsOwned, 0, Map.of(), Map.of(), Map.of());
        }
    }
    record Completed(boolean updated, Summary summary, String message) implements Event {
        public Completed(boolean updated, int distinctCardsOwned, String message) {
            this(updated, Summary.basic(distinctCardsOwned), message);
        }
        public int ownedCardIds() { return summary.distinctCardsOwned(); }
    }

    record CardOption(long arenaId, String name, String setCode,
                      String setName, String collectorNumber) { }

    sealed interface Response permits Continue, VerifiedCards { }
    record Continue() implements Response { }
    record VerifiedCards(Map<Long, Integer> copies) implements Response {
        public VerifiedCards { copies = Map.copyOf(copies); }
    }
}
