package app.projection;

import app.model.event.ObjectReference;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TargetDecisionTrackerTest {
    @Test
    void correlatesSelectedTargetAndPreservesLegalAlternatives() {
        TargetDecisionTracker tracker = new TargetDecisionTracker(
                (id, cards) -> reference(id),
                ObjectReference::name);

        tracker.observeRequest(JsonParser.parseString("""
                {
                  "msgId": 42,
                  "selectTargetsReq": {
                    "sourceId": 10,
                    "targets": [{
                      "minTargets": 1,
                      "maxTargets": 1,
                      "targets": [
                        {"targetInstanceId": 20, "legalAction": "Select"},
                        {"targetInstanceId": 30, "legalAction": "Select"}
                      ]
                    }]
                  }
                }
                """).getAsJsonObject(), Map.of());

        var resolved = tracker.resolveResponse(JsonParser.parseString("""
                {
                  "respId": 42,
                  "selectTargetsResp": {
                    "target": {
                      "targets": [{"targetInstanceId": 30}]
                    }
                  }
                }
                """).getAsJsonObject(), Map.of()).orElseThrow();

        assertEquals("Object 10 chooses Object 30", resolved.text());
        assertEquals(30, resolved.observation().selected().get(0).arenaInstanceId());
        assertEquals(20, resolved.observation().alternatives().get(0).arenaInstanceId());
        assertEquals(1, resolved.observation().minimumSelections());
        assertEquals(1, resolved.observation().maximumSelections());
        assertEquals(3, resolved.references().size());
    }

    @Test
    void ignoresResponsesWithoutAMatchingRequest() {
        TargetDecisionTracker tracker = new TargetDecisionTracker(
                (id, cards) -> reference(id),
                ObjectReference::name);

        assertTrue(tracker.resolveResponse(JsonParser.parseString("""
                {"respId": 999, "selectTargetsResp": {}}
                """).getAsJsonObject(), Map.of()).isEmpty());
    }

    private ObjectReference reference(long id) {
        return id < 0 ? null : new ObjectReference(
                id, id, id + 1000, "Object " + id, null, null);
    }
}
