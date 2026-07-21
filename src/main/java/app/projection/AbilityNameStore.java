package app.projection;

import app.model.event.AbilityReference;

import java.util.prefs.Preferences;

/** Persistent user names for Arena ability IDs.
 * <p><strong>Architectural role:</strong> This type belongs to the projection boundary between ordered Arena observations, canonical game state, and immutable semantic events.</p>
 */
public final class AbilityNameStore {
    private final Preferences preferences = Preferences.userRoot().node("arena-log-viewer/abilities");

    public String find(long sourceGrpId, long abilityGrpId) {
        return preferences.get(key(sourceGrpId, abilityGrpId), "").strip();
    }

    public void put(long sourceGrpId, long abilityGrpId, String name) {
        String key = key(sourceGrpId, abilityGrpId);
        if (name == null || name.isBlank()) preferences.remove(key);
        else preferences.put(key, name.strip());
    }

    public String displayName(AbilityReference reference) {
        if (reference == null) return "";
        return find(reference.getSourceGrpId(), reference.getAbilityGrpId());
    }

    private String key(long sourceGrpId, long abilityGrpId) {
        return sourceGrpId + ":" + abilityGrpId;
    }
}
