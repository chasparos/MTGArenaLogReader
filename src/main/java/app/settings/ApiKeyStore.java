package app.settings;

import java.util.Optional;

/**
 * Persists the OpenAI API key outside the application installation.
 */
public interface ApiKeyStore {
    boolean isConfigured();

    Optional<char[]> load();

    void save(char[] apiKey);

    void clear();
}
