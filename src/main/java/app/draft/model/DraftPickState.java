package app.draft.model;

import app.model.card.CardInfo;

import java.util.List;
import java.util.Map;

public record DraftPickState(
        String draftId,
        int packNumber,
        int pickNumber,
        List<Long> offeredCardIds,
        Long selectedCardId,
        List<DraftCardCount> draftedPool,
        List<DraftCardCount> mainDeck,
        List<DraftCardCount> sideboard,
        Map<Long, CardInfo> cards
) {
    public DraftPickState {
        draftId = draftId == null ? "" : draftId;
        offeredCardIds = List.copyOf(offeredCardIds == null ? List.of() : offeredCardIds);
        draftedPool = List.copyOf(draftedPool == null ? List.of() : draftedPool);
        mainDeck = List.copyOf(mainDeck == null ? List.of() : mainDeck);
        sideboard = List.copyOf(sideboard == null ? List.of() : sideboard);
        cards = Map.copyOf(cards == null ? Map.of() : cards);
    }

    public String positionLabel() {
        return "Pack " + packNumber + ", pick " + pickNumber;
    }
}
