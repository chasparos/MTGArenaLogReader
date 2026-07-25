package app.settings;

import com.sun.jna.platform.win32.Crypt32Util;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Stores an API key encrypted with Windows DPAPI for the current Windows user.
 *
 * <p>The Preferences value contains only DPAPI ciphertext. Windows controls the
 * encryption key; the application never persists a separate master password.</p>
 */
public final class WindowsDpapiApiKeyStore implements ApiKeyStore {
    private static final String VALUE_NAME = "openai-api-key-dpapi";
    private final Preferences preferences;

    public WindowsDpapiApiKeyStore() {
        this(Preferences.userRoot().node("arena-log-viewer/secrets"));
    }

    WindowsDpapiApiKeyStore(Preferences preferences) {
        this.preferences = preferences;
    }

    @Override
    public boolean isConfigured() {
        return preferences.get(VALUE_NAME, null) != null;
    }

    @Override
    public Optional<char[]> load() {
        String encoded = preferences.get(VALUE_NAME, null);
        if (encoded == null || encoded.isBlank()) return Optional.empty();

        byte[] encrypted = Base64.getDecoder().decode(encoded);
        byte[] plaintext = null;
        try {
            plaintext = Crypt32Util.cryptUnprotectData(encrypted);
            return Optional.of(new String(plaintext, StandardCharsets.UTF_8).toCharArray());
        } finally {
            Arrays.fill(encrypted, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    @Override
    public void save(char[] apiKey) {
        if (apiKey == null || apiKey.length == 0) {
            throw new IllegalArgumentException("API key cannot be empty");
        }

        byte[] plaintext = new String(apiKey).getBytes(StandardCharsets.UTF_8);
        byte[] encrypted = null;
        try {
            encrypted = Crypt32Util.cryptProtectData(plaintext);
            preferences.put(VALUE_NAME, Base64.getEncoder().encodeToString(encrypted));
        } finally {
            Arrays.fill(plaintext, (byte) 0);
            if (encrypted != null) Arrays.fill(encrypted, (byte) 0);
        }
    }

    @Override
    public void clear() {
        preferences.remove(VALUE_NAME);
    }
}
