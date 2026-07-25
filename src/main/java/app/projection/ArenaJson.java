package app.projection;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Null-tolerant accessors for the sparse and version-dependent Arena payloads.
 */
final class ArenaJson {
    private ArenaJson() {
    }

    static JsonObject objectAt(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return new JsonObject();
            current = current.getAsJsonObject().get(key);
        }
        return current != null && current.isJsonObject()
                ? current.getAsJsonObject()
                : new JsonObject();
    }

    static JsonArray arrayAt(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonArray()
                ? value.getAsJsonArray()
                : new JsonArray();
    }

    static String stringAt(JsonObject root, String... path) {
        JsonElement current = root;
        for (String key : path) {
            if (current == null || !current.isJsonObject()) return "";
            current = current.getAsJsonObject().get(key);
        }
        return current == null || current.isJsonNull() ? "" : current.getAsString();
    }

    static int intAt(JsonObject root, String key, int fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : fallback;
    }

    static Integer nullableInt(JsonObject root, String key) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsInt() : null;
    }

    static long longAt(JsonObject root, String key, long fallback) {
        JsonElement value = root.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsLong() : fallback;
    }

    static List<Long> longArray(JsonObject root, String key) {
        List<Long> result = new ArrayList<>();
        for (JsonElement value : arrayAt(root, key)) {
            if (value.isJsonPrimitive()) result.add(value.getAsLong());
        }
        return result;
    }

    static boolean hasType(JsonObject annotation, String expected) {
        for (JsonElement type : arrayAt(annotation, "type")) {
            if (expected.equals(type.getAsString())) return true;
        }
        return false;
    }

    static long detailLong(JsonObject annotation, String key, long fallback) {
        for (JsonElement element : arrayAt(annotation, "details")) {
            if (!element.isJsonObject()) continue;
            JsonObject detail = element.getAsJsonObject();
            if (!key.equals(stringAt(detail, "key"))) continue;
            JsonArray values = arrayAt(detail, "valueInt32");
            if (!values.isEmpty()) return values.get(0).getAsLong();
            values = arrayAt(detail, "valueUint32");
            if (!values.isEmpty()) return values.get(0).getAsLong();
        }
        return fallback;
    }

    static String detailString(JsonObject annotation, String key) {
        for (JsonElement element : arrayAt(annotation, "details")) {
            if (!element.isJsonObject()) continue;
            JsonObject detail = element.getAsJsonObject();
            if (!key.equals(stringAt(detail, "key"))) continue;
            JsonArray values = arrayAt(detail, "valueString");
            if (!values.isEmpty()) return values.get(0).getAsString();
        }
        return "";
    }
}
