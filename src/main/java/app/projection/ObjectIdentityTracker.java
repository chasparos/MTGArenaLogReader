package app.projection;

import app.model.game.GameObjectState;
import app.model.game.GameState;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Maintains stable logical identities for Arena object instances.
 *
 * <p><strong>Architectural role:</strong> This collaborator belongs to the
 * projection layer. It records Arena-observed object-id changes and provides
 * identity-aware lookup helpers to other projectors. It does not infer zone
 * changes, emit events, or decide how an object should be presented.</p>
 */
final class ObjectIdentityTracker {
    private static final String OBJECT_ID_CHANGED = "AnnotationType_ObjectIdChanged";

    private final GameState state;

    ObjectIdentityTracker(GameState state) {
        this.state = state;
    }

    void observeIdChanges(JsonArray annotations) {
        for (JsonElement element : annotations) {
            if (!element.isJsonObject()) continue;

            JsonObject annotation = element.getAsJsonObject();
            if (!hasType(annotation, OBJECT_ID_CHANGED)) continue;

            long oldId = detailLong(annotation, "orig_id");
            long newId = detailLong(annotation, "new_id");
            if (oldId < 0 || newId < 0) continue;

            long logicalId = logicalIdOf(oldId);
            state.getLogicalIds().put(oldId, logicalId);
            state.getLogicalIds().put(newId, logicalId);
            state.getCurrentInstanceByLogicalId().put(logicalId, newId);

            GameObjectState previous = state.getObjects().get(oldId);
            if (previous != null && !state.getObjects().containsKey(newId)) {
                GameObjectState copy = previous.copy();
                copy.setInstanceId(newId);
                copy.setLogicalObjectId(logicalId);
                state.getObjects().put(newId, copy);
            }
        }
    }

    GameObjectState copyForObservation(long instanceId) {
        GameObjectState previous = state.getObjects().get(instanceId);
        GameObjectState current = previous == null ? new GameObjectState() : previous.copy();

        long logicalId = logicalIdOf(instanceId);
        current.setInstanceId(instanceId);
        current.setLogicalObjectId(logicalId);
        state.getLogicalIds().putIfAbsent(instanceId, logicalId);
        state.getCurrentInstanceByLogicalId().merge(logicalId, instanceId, Math::max);
        return current;
    }

    long logicalIdOf(long instanceId) {
        return state.getLogicalIds().getOrDefault(instanceId, instanceId);
    }

    boolean isCurrent(GameObjectState object) {
        long currentInstanceId = state.getCurrentInstanceByLogicalId()
                .getOrDefault(object.getLogicalObjectId(), object.getInstanceId());
        return currentInstanceId == object.getInstanceId();
    }

    GameObjectState findIncludingAliases(long instanceId) {
        GameObjectState direct = state.getObjects().get(instanceId);
        if (direct != null) return direct;

        long logicalId = logicalIdOf(instanceId);
        Long currentId = state.getCurrentInstanceByLogicalId().get(logicalId);
        if (currentId != null) {
            GameObjectState current = state.getObjects().get(currentId);
            if (current != null) return current;
        }

        for (GameObjectState candidate : state.getObjects().values()) {
            if (candidate.getLogicalObjectId() == logicalId) return candidate;
        }
        return null;
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

    private static long detailLong(JsonObject annotation, String key) {
        JsonElement detailsElement = annotation.get("details");
        if (detailsElement == null || !detailsElement.isJsonArray()) return -1;

        for (JsonElement element : detailsElement.getAsJsonArray()) {
            if (!element.isJsonObject()) continue;
            JsonObject detail = element.getAsJsonObject();
            JsonElement detailKey = detail.get("key");
            if (detailKey == null || !key.equals(detailKey.getAsString())) continue;

            JsonElement values = detail.get("valueInt32");
            if (values != null && values.isJsonArray() && !values.getAsJsonArray().isEmpty()) {
                return values.getAsJsonArray().get(0).getAsLong();
            }
            values = detail.get("valueUint32");
            if (values != null && values.isJsonArray() && !values.getAsJsonArray().isEmpty()) {
                return values.getAsJsonArray().get(0).getAsLong();
            }
        }
        return -1;
    }
}
