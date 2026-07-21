package app.projection;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AttachmentTrackerTest {

    @Test
    void resolvesAttachmentsThroughLogicalObjectIds() {
        AttachmentTracker tracker = new AttachmentTracker();

        tracker.reconcile(
                annotations(42, 1001, 2001),
                new JsonArray(),
                id -> id == 1001L ? 10L : id == 2001L ? 20L : id);

        assertEquals(20L, tracker.attachedHostFor(10L));
    }

    @Test
    void removesRelationsWhenArenaDeletesTheAnnotation() {
        AttachmentTracker tracker = new AttachmentTracker();
        tracker.reconcile(annotations(42, 1001, 2001), new JsonArray(), id -> id);

        JsonArray deleted = new JsonArray();
        deleted.add(42);
        tracker.reconcile(new JsonArray(), deleted, id -> id);

        assertNull(tracker.attachedHostFor(1001L));
    }

    @Test
    void resetClearsRelationsBetweenMatches() {
        AttachmentTracker tracker = new AttachmentTracker();
        tracker.reconcile(annotations(42, 1001, 2001), new JsonArray(), id -> id);

        tracker.reset();

        assertNull(tracker.attachedHostFor(1001L));
    }

    private JsonArray annotations(long annotationId, long attachedId, long hostId) {
        JsonObject annotation = new JsonObject();
        annotation.addProperty("id", annotationId);
        annotation.addProperty("affectorId", attachedId);

        JsonArray types = new JsonArray();
        types.add("AnnotationType_Attachment");
        annotation.add("type", types);

        JsonArray affectedIds = new JsonArray();
        affectedIds.add(hostId);
        annotation.add("affectedIds", affectedIds);

        JsonArray annotations = new JsonArray();
        annotations.add(annotation);
        return annotations;
    }
}
