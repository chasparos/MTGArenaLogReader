package app.projection;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArenaJsonTest {
    @Test
    void safelyTraversesSparsePayloads() {
        JsonObject root = JsonParser.parseString("""
                {"payload":{"seat":2,"ids":[10,20],"name":"player"}}
                """).getAsJsonObject();

        JsonObject payload = ArenaJson.objectAt(root, "payload");

        assertEquals(2, ArenaJson.intAt(payload, "seat", -1));
        assertEquals("player", ArenaJson.stringAt(root, "payload", "name"));
        assertEquals(List.of(10L, 20L), ArenaJson.longArray(payload, "ids"));
        assertEquals(0, ArenaJson.objectAt(root, "missing", "path").size());
        assertTrue(ArenaJson.arrayAt(root, "missing").isEmpty());
    }

    @Test
    void readsAnnotationDetailsAcrossIntegerRepresentations() {
        JsonObject annotation = JsonParser.parseString("""
                {
                  "type":["AnnotationType_DamageDealt"],
                  "details":[
                    {"key":"amount","valueInt32":[3]},
                    {"key":"label","valueString":["combat"]}
                  ]
                }
                """).getAsJsonObject();

        assertTrue(ArenaJson.hasType(annotation, "AnnotationType_DamageDealt"));
        assertEquals(3, ArenaJson.detailLong(annotation, "amount", -1));
        assertEquals("combat", ArenaJson.detailString(annotation, "label"));
    }
}
