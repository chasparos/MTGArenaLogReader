package app.draft.parsing;

import app.draft.model.DraftCardCount;

import java.util.List;

public sealed interface DraftLogEvent permits DraftLogEvent.PackOffered, DraftLogEvent.PickMade, DraftLogEvent.DeckSubmitted {
    record PackOffered(String draftId, int packNumber, int pickNumber, List<Long> cardIds)
            implements DraftLogEvent {
        public PackOffered { cardIds = List.copyOf(cardIds); }
    }

    record PickMade(String draftId, int packNumber, int pickNumber, List<Long> cardIds)
            implements DraftLogEvent {
        public PickMade { cardIds = List.copyOf(cardIds); }
    }

    record DeckSubmitted(List<DraftCardCount> mainDeck, List<DraftCardCount> sideboard)
            implements DraftLogEvent {
        public DeckSubmitted {
            mainDeck = List.copyOf(mainDeck);
            sideboard = List.copyOf(sideboard);
        }
    }
}
