package devtools.ui;

import com.github.weisj.jsvg.SVGDocument;
import com.github.weisj.jsvg.parser.SVGLoader;
import com.github.weisj.jsvg.view.ViewBox;

import java.awt.Component;
import java.awt.Graphics2D;
import java.net.URL;

/** Loads a bundled SVG once and renders it through JSVG at control scale. */
public final class SvgIconPainter {
    private final SVGDocument document;

    public SvgIconPainter(String resourcePath) {
        URL resource = SvgIconPainter.class.getResource(resourcePath);
        if (resource == null) {
            throw new IllegalArgumentException("Missing SVG resource: " + resourcePath);
        }
        document = new SVGLoader().load(resource);
        if (document == null) {
            throw new IllegalArgumentException("Unreadable SVG resource: " + resourcePath);
        }
    }

    public void paint(Component component, Graphics2D graphics, int x, int y, int width, int height) {
        document.render(component, graphics, new ViewBox(x, y, width, height));
    }
}
