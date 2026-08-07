package app.draft.analysis;

import app.draft.model.DraftCardCount;
import app.model.card.CardInfo;
import app.model.card.MagicCardOrdering;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class DraftCardOrdering {
    public List<DraftCardCount> sort(
            List<DraftCardCount> counts,
            Map<Long, CardInfo> cards) {
        List<DraftCardCount> result = new ArrayList<>(
                counts == null ? List.of() : counts);
        result.sort((left, right) -> MagicCardOrdering.normalComparator().compare(
                cards.get(left.arenaId()), cards.get(right.arenaId())));
        return result;
    }

    public String typeGroup(CardInfo card) {
        return MagicCardOrdering.typeGroup(card);
    }
}
