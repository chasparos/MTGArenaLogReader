package app.draft.tracking;

import app.draft.model.DraftCardCount;
import app.draft.model.DraftPickState;
import app.draft.model.DraftUiModel;
import app.draft.parsing.DraftLogEvent;
import app.draft.parsing.DraftLogParser;
import app.model.InformationBundle;
import app.model.card.CardInfo;
import app.model.log.LogMessageInterface;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Reconstructs draft-lifetime state from Arena observations and publishes immutable pick snapshots.
 */
public final class DraftTracker {
    private final DraftLogParser parser;
    private final DraftUiModel uiModel;
    private final List<DraftPickState> timeline = new ArrayList<>();
    private final Map<Long, Integer> draftedPool = new LinkedHashMap<>();
    private final Map<Long, CardInfo> cards = new LinkedHashMap<>();
    private List<DraftCardCount> mainDeck = List.of();
    private List<DraftCardCount> sideboard = List.of();
    private String activeDraftId = "";

    public DraftTracker(DraftLogParser parser, DraftUiModel uiModel) {
        this.parser = Objects.requireNonNull(parser, "parser");
        this.uiModel = Objects.requireNonNull(uiModel, "uiModel");
    }

    public void accept(LogMessageInterface message) {
        parser.parse(message.getRawText()).ifPresent(event -> {
            synchronized (this) {
                apply(event);
            }
            message.getModelFuture().thenAccept(model -> {
                if (!(model instanceof InformationBundle bundle) || bundle.getCards().isEmpty()) return;
                synchronized (this) {
                    cards.putAll(bundle.getCards());
                    refreshCards();
                }
            });
        });
    }

    public synchronized void reset() {
        activeDraftId = "";
        timeline.clear();
        draftedPool.clear();
        cards.clear();
        mainDeck = List.of();
        sideboard = List.of();
        uiModel.clear();
    }

    public synchronized List<DraftPickState> timeline() {
        return List.copyOf(timeline);
    }

    private void apply(DraftLogEvent event) {
        if (event instanceof DraftLogEvent.PackOffered offered) {
            beginDraftIfNeeded(offered.draftId());
            if (containsPosition(offered.draftId(), offered.packNumber(), offered.pickNumber())) return;
            timeline.add(new DraftPickState(
                    offered.draftId(), offered.packNumber(), offered.pickNumber(),
                    offered.cardIds(), null, poolCounts(), mainDeck, sideboard, cards));
            publish(true);
            return;
        }
        if (event instanceof DraftLogEvent.PickMade pick) {
            beginDraftIfNeeded(pick.draftId());
            Long selected = pick.cardIds().isEmpty() ? null : pick.cardIds().getFirst();
            int index = findPosition(pick.draftId(), pick.packNumber(), pick.pickNumber());
            if (index >= 0 && selected != null) {
                DraftPickState old = timeline.get(index);
                if (old.selectedCardId() == null) {
                    draftedPool.merge(selected, 1, Integer::sum);
                    timeline.set(index, new DraftPickState(
                            old.draftId(), old.packNumber(), old.pickNumber(),
                            old.offeredCardIds(), selected, poolCounts(), mainDeck, sideboard, cards));
                    publish(true);
                }
            }
            return;
        }
        if (event instanceof DraftLogEvent.DeckSubmitted deck) {
            mainDeck = deck.mainDeck();
            sideboard = deck.sideboard();
            if (!timeline.isEmpty()) {
                int index = timeline.size() - 1;
                DraftPickState old = timeline.get(index);
                timeline.set(index, new DraftPickState(
                        old.draftId(), old.packNumber(), old.pickNumber(), old.offeredCardIds(),
                        old.selectedCardId(), old.draftedPool(), mainDeck, sideboard, cards));
                publish(false);
            }
        }
    }

    private void beginDraftIfNeeded(String draftId) {
        if (activeDraftId.isBlank()) {
            activeDraftId = draftId;
            return;
        }
        if (!activeDraftId.equals(draftId)) reset();
        activeDraftId = draftId;
    }

    private boolean containsPosition(String draftId, int pack, int pick) {
        return findPosition(draftId, pack, pick) >= 0;
    }

    private int findPosition(String draftId, int pack, int pick) {
        for (int i = timeline.size() - 1; i >= 0; i--) {
            DraftPickState state = timeline.get(i);
            if (state.draftId().equals(draftId)
                    && state.packNumber() == pack
                    && state.pickNumber() == pick) return i;
        }
        return -1;
    }

    private List<DraftCardCount> poolCounts() {
        return draftedPool.entrySet().stream()
                .map(entry -> new DraftCardCount(entry.getKey(), entry.getValue()))
                .toList();
    }

    private void refreshCards() {
        for (int i = 0; i < timeline.size(); i++) {
            DraftPickState old = timeline.get(i);
            timeline.set(i, new DraftPickState(
                    old.draftId(), old.packNumber(), old.pickNumber(), old.offeredCardIds(),
                    old.selectedCardId(), old.draftedPool(), old.mainDeck(), old.sideboard(), cards));
        }
        publish(false);
    }

    private void publish(boolean selectLatest) {
        uiModel.replaceTimeline(List.copyOf(timeline), selectLatest);
    }
}
