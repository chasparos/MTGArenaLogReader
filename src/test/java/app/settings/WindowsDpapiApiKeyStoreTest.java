package app.settings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

import java.util.Arrays;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
class WindowsDpapiApiKeyStoreTest {
    private final Preferences preferences = Preferences.userRoot()
            .node("arena-log-viewer-tests/" + System.nanoTime());
    private final WindowsDpapiApiKeyStore store =
            new WindowsDpapiApiKeyStore(preferences);

    @AfterEach
    void cleanUp() throws Exception {
        preferences.removeNode();
    }

    @Test
    void roundTripsKeyAsDpapiCiphertext() {
        char[] expected = "test-api-key-not-a-secret".toCharArray();
        store.save(expected);

        assertTrue(store.isConfigured());
        assertFalse(preferences.get("openai-api-key-dpapi", "")
                .contains("test-api-key-not-a-secret"));

        char[] actual = store.load().orElseThrow();
        try {
            assertArrayEquals(expected, actual);
        } finally {
            Arrays.fill(expected, '\0');
            Arrays.fill(actual, '\0');
        }
    }

    @Test
    void clearRemovesStoredKey() {
        char[] value = "temporary".toCharArray();
        try {
            store.save(value);
        } finally {
            Arrays.fill(value, '\0');
        }

        store.clear();

        assertFalse(store.isConfigured());
        assertTrue(store.load().isEmpty());
    }
}
