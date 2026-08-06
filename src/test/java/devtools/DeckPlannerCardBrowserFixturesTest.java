package devtools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class DeckPlannerCardBrowserFixturesTest {
    @TempDir Path temp;

    @Test
    void writesNarrowNormalAndWidePngEvidence() throws Exception {
        DeckPlannerCardBrowserFixtures.writeStandardFixtures(temp);

        assertImage("card-browser-narrow.png", 360, 640);
        assertImage("card-browser-normal.png", 760, 640);
        assertImage("card-browser-wide.png", 1280, 640);
    }

    private void assertImage(String name, int width, int height) throws Exception {
        Path path = temp.resolve(name);
        assertTrue(Files.size(path) > 0, name);
        var image = ImageIO.read(path.toFile());
        assertNotNull(image, name);
        assertEquals(width, image.getWidth(), name);
        assertEquals(height, image.getHeight(), name);
    }
}
