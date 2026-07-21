package app.projection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks Arena attachment annotations as logical-object relationships.
 *
 * <p><strong>Architectural role:</strong> This collaborator belongs to the
 * projection layer. It translates persistent Arena attachment annotations
 * into stable logical-object links consumed by battlefield snapshots. It does
 * not create events, mutate game objects, or decide how attachments are
 * rendered.</p>
 */
final class AttachmentTracker {
    private static final String ATTACHMENT_TYPE = "AnnotationType_Attachment";

    private final Map<Long, AttachmentRelation> relationsByAnnotationId = new LinkedHashMap<>();

    void reset() {
        relationsByAnnotationId.clear();
    }

    void reconcile(JsonArray persistentAnnotations,
                   JsonArray deletedAnnotationIds,
                   Map<Long, Long> logicalIds) {
        removeDeleted(deletedAnnotationIds);
        observe(persistentAnnotations, logicalIds);
    }

    Long attachedHostFor(long attachedLogicalId) {
        for (AttachmentRelation relation : relationsByAnnotationId.values()) {
            if (relation.attachedLogicalId() == attachedLogicalId) {
                return relation.hostLogicalId();
            }
        }
        return null;
    }

    private void removeDeleted(JsonArray deletedAnnotationIds) {
        for (JsonElement deleted : deletedAnnotationIds) {
            if (deleted.isJsonPrimitive()) {
                relationsByAnnotationId.remove(deleted.getAsLong());
            }
        }
    }

    private void observe(JsonArray annotations, Map<Long, Long> logicalIds) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;

            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, ATTACHMENT_TYPE)) continue;

            long annotationId = longValue(annotation, "id");
            long attachedInstanceId = longValue(annotation, "affectorId");
            JsonArray affectedIds = arrayValue(annotation, "affectedIds");
            if (annotationId < 0 || attachedInstanceId < 0 || affectedIds.isEmpty()) continue;

            long hostInstanceId = affectedIds.get(0).getAsLong();
            long attachedLogicalId = logicalIds.getOrDefault(attachedInstanceId, attachedInstanceId);
            long hostLogicalId = logicalIds.getOrDefault(hostInstanceId, hostInstanceId);
            relationsByAnnotationId.put(
                    annotationId,
                    new AttachmentRelation(attachedLogicalId, hostLogicalId));
        }
    }

    private static boolean hasType(JsonObject annotation, String expectedType) {
        JsonElement typeElement = annotation.get("type");
        if (typeElement == null || typeElement.isJsonNull()) return false;
        if (typeElement.isJsonArray()) {
            for (JsonElement element : typeElement.getAsJsonArray()) {
                if (element.isJsonPrimitive() && expectedType.equals(element.getAsString())) {
                    return true;
                }
            }
            return false;
        }
        return typeElement.isJsonPrimitive() && expectedType.equals(typeElement.getAsString());
    }

    private static long longValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : -1;
    }

    private static JsonArray arrayValue(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : new JsonArray();
    }

    private record AttachmentRelation(long attachedLogicalId, long hostLogicalId) {}
}
