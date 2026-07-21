package app.replay;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import java.awt.*;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Small resource-backed SVG painter. Missing assets are deliberately handled
 * by the caller so the replay remains usable when a newly introduced symbol
 * has not yet been synced into resources.
 * <p><strong>Architectural role:</strong> This type belongs to the Swing presentation boundary and consumes structured models and events without owning game reconstruction.</p>
 */
final class SvgAssetRenderer {
    private final Map<String, Optional<SVGDocument>> documents = new HashMap<>();

    boolean paint(Graphics2D graphics, String resourcePath, int x, int y, int width, int height) {
        Optional<SVGDocument> document = documents.computeIfAbsent(resourcePath, this::load);
        if (document.isEmpty()) return false;

        Graphics2D copy = (Graphics2D) graphics.create();
        try {
            copy.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            copy.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            document.get().render(null, copy, new ViewBox(x, y, width, height));
            return true;
        } finally {
            copy.dispose();
        }
    }

    private Optional<SVGDocument> load(String resourcePath) {
        try {
            URL url = SvgAssetRenderer.class.getResource(resourcePath);
            return url == null ? Optional.empty() : Optional.ofNullable(new SVGLoader().load(url));
        } catch (RuntimeException error) {
            return Optional.empty();
        }
    }
}
