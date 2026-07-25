package app.draft;

import app.draft.model.DraftPickState;
import app.draft.model.DraftUiModel;
import app.draft.parsing.DraftLogParser;
import app.draft.tracking.DraftTracker;
import app.log.LogMessageParser;
import app.model.InformationBundle;
import app.model.log.LogMessageInterface;
import app.model.log.RawLogEntry;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DraftLogFixtureTest {
    @Test
    void completePremierDraftProducesFortyTwoImmutablePickStates() throws Exception {
        DraftUiModel uiModel = new DraftUiModel();
        DraftTracker tracker = new DraftTracker(new DraftLogParser(), uiModel);
        replayFixture(tracker);

        List<DraftPickState> timeline = tracker.timeline();
        assertEquals(42, timeline.size());
        assertEquals(14, timeline.stream().filter(state -> state.packNumber() == 1).count());
        assertEquals(14, timeline.stream().filter(state -> state.packNumber() == 2).count());
        assertEquals(14, timeline.stream().filter(state -> state.packNumber() == 3).count());
        assertTrue(timeline.stream().allMatch(state -> state.selectedCardId() != null));

        DraftPickState first = timeline.getFirst();
        DraftPickState last = timeline.getLast();
        assertEquals(1, first.packNumber());
        assertEquals(1, first.pickNumber());
        assertEquals(1, first.draftedPool().stream().mapToInt(entry -> entry.quantity()).sum());
        assertEquals(3, last.packNumber());
        assertEquals(14, last.pickNumber());
        assertEquals(42, last.draftedPool().stream().mapToInt(entry -> entry.quantity()).sum());

        assertThrows(UnsupportedOperationException.class, () -> first.offeredCardIds().add(1L));
        assertThrows(UnsupportedOperationException.class, () -> timeline.add(first));
    }

    @Test
    void duplicateDraftNotificationsDoNotCreateDuplicatePickStates() throws Exception {
        DraftUiModel uiModel = new DraftUiModel();
        DraftTracker tracker = new DraftTracker(new DraftLogParser(), uiModel);
        replayFixture(tracker);

        assertEquals(42, tracker.timeline().size(), "The fixture contains duplicate Draft.Notify records");
    }

    @Test
    void latestDeckSubmissionIsAttachedToTheFinalPickState() throws Exception {
        DraftUiModel uiModel = new DraftUiModel();
        DraftTracker tracker = new DraftTracker(new DraftLogParser(), uiModel);
        replayFixture(tracker);

        DraftPickState finalState = tracker.timeline().getLast();
        assertFalse(finalState.mainDeck().isEmpty());
        assertFalse(finalState.sideboard().isEmpty());
        assertEquals(40, finalState.mainDeck().stream().mapToInt(entry -> entry.quantity()).sum());
    }

    @Test
    void browserMovesAcrossHistoricalPickStatesWithoutMutatingThem() throws Exception {
        DraftUiModel uiModel = new DraftUiModel();
        DraftTracker tracker = new DraftTracker(new DraftLogParser(), uiModel);
        replayFixture(tracker);

        assertEquals(41, uiModel.selectedIndex());
        DraftPickState latest = uiModel.selected();
        assertTrue(uiModel.previous());
        assertEquals(40, uiModel.selectedIndex());
        assertNotSame(latest, uiModel.selected());
        assertTrue(uiModel.next());
        assertSame(latest, uiModel.selected());
    }

    private void replayFixture(DraftTracker tracker) throws Exception {
        LogMessageParser parser = new LogMessageParser(new Gson());
        try (InputStream input = getClass().getResourceAsStream("/logs/premier-draft.log")) {
            assertNotNull(input);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                long sequence = 1;
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    LogMessageInterface message = parser.parse(new RawLogEntry(sequence++, Instant.EPOCH, line));
                    message.getModelFuture().complete(new InformationBundle());
                    tracker.accept(message);
                }
            }
        }
    }
}
