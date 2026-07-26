package app.settings;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ThemeServiceTest {
    @Test
    void bothApplicationPalettesAreAvailable() {
        ThemeService service = new ThemeService();

        Properties light = service.loadPalette(ThemeMode.LIGHT);
        Properties dark = service.loadPalette(ThemeMode.DARK);

        assertFalse(light.isEmpty());
        assertFalse(dark.isEmpty());
        assertEquals("#f2f2f2", light.getProperty("Panel.background"));
        assertEquals("#24272c", dark.getProperty("Panel.background"));
    }
}
