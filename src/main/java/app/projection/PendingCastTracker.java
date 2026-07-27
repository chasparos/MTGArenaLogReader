package app.projection;

import app.model.game.GameObjectState;
import com.google.gson.JsonElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Correlates Arena's provisional Limbo casting flow with later stack movement
 * or cancellation. It owns correlation only and does not create semantic events.
 */
final class PendingCastTracker {
    record PendingCast(long instanceId, long grpId, int seatId, String name) {}

    private final ObjectIdentityTracker identities;
    private final Map<Long, PendingCast> casts = new LinkedHashMap<>();

    PendingCastTracker(ObjectIdentityTracker identities) {
        this.identities = identities;
    }

    void remember(long instanceId, long grpId, int seatId, String name) {
        casts.put(instanceId, new PendingCast(instanceId, grpId, seatId, name));
    }

    PendingCast removeMostRecent() {
        PendingCast latest = null;
        for (PendingCast candidate : casts.values()) latest = candidate;
        if (latest != null) casts.remove(latest.instanceId());
        return latest;
    }

    PendingCast removeFor(long instanceId, GameObjectState object) {
        PendingCast direct = casts.remove(instanceId);
        if (direct != null) return direct;

        long logicalId = identities.logicalIdOf(instanceId);
        for (Map.Entry<Long, PendingCast> entry : new ArrayList<>(casts.entrySet())) {
            PendingCast candidate = entry.getValue();
            boolean sameLogicalObject =
                    identities.logicalIdOf(entry.getKey()) == logicalId;
            boolean sameKnownCard = object != null
                    && object.getGrpId() > 0
                    && candidate.grpId() > 0
                    && object.getGrpId() == candidate.grpId();
            if (sameLogicalObject || sameKnownCard) {
                casts.remove(entry.getKey());
                return candidate;
            }
        }
        return null;
    }

    void clear() {
        casts.clear();
    }

    static boolean containsCancelAction(JsonElement element) {
        if (element == null || element.isJsonNull()) return false;
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            String value = element.getAsString();
            return value.startsWith("ActionType_Cancel")
                    || value.startsWith("SelectAction_Cancel")
                    || value.equals("Cancel");
        }
        if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                if (containsCancelAction(child)) return true;
            }
            return false;
        }
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : element.getAsJsonObject().entrySet()) {
                if (containsCancelAction(entry.getValue())) return true;
            }
        }
        return false;
    }
}
