package app.coaching.ui;

import app.coaching.model.CoachingContext;
import app.coaching.model.CoachingMessage;

import javax.swing.JEditorPane;
import javax.swing.JScrollPane;
import javax.swing.event.HyperlinkEvent;
import java.awt.Font;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Read-only coaching transcript with lightweight Markdown and clickable protocol references.
 */
public final class RichConversationView extends JScrollPane {
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                    .withZone(ZoneId.systemDefault());
    private static final Pattern REFERENCE =
            Pattern.compile("\\[(?:MATCH|(?:G|T|E|A|C|L|GR|S)#?\\d+|c\\d+(?:#\\d+)?) ]"
                    .replace("?) ]", "?)]"));
    private static final Pattern CARD_DICTIONARY =
            Pattern.compile("^CARD c(\\d+)=(.+?)(?:@\\d+)?$", Pattern.MULTILINE);

    private final JEditorPane document = new JEditorPane();
    private Consumer<CoachingReference> coordinator = ignored -> { };
    private Map<Integer, String> cardNames = Map.of();

    public RichConversationView() {
        document.setEditable(false);
        document.setContentType("text/html");
        document.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
        document.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        document.addHyperlinkListener(event -> {
            if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) return;
            String description = event.getDescription();
            if (description == null || !description.startsWith("ref:")) return;
            CoachingReference.parse(description.substring(4)).ifPresent(coordinator);
        });
        setViewportView(document);
    }

    public void setCoordinator(Consumer<CoachingReference> coordinator) {
        this.coordinator = coordinator == null ? ignored -> { } : coordinator;
    }

    public void showConversation(String reconstruction, List<CoachingMessage> messages) {
        cardNames = readCardNames(reconstruction);
        document.setText(render(messages == null ? List.of() : messages));
        document.setCaretPosition(document.getDocument().getLength());
    }

    private String render(List<CoachingMessage> messages) {
        StringBuilder html = new StringBuilder("""
                <html><head><style>
                body { font-family: sans-serif; margin: 10px; }
                .message { margin: 0 0 14px 0; padding: 10px; border: 1px solid #b8b8b8; }
                .meta { font-size: 10px; color: #666666; margin-bottom: 6px; }
                .context { margin-bottom: 7px; }
                .chip { background: #e6e8eb; border: 1px solid #a8adb3;
                        padding: 1px 4px; text-decoration: none; color: #202428; }
                h1 { font-size: 18px; margin: 9px 0 5px 0; }
                h2 { font-size: 16px; margin: 8px 0 4px 0; }
                h3 { font-size: 14px; margin: 7px 0 3px 0; }
                p { margin: 4px 0; }
                ul { margin-top: 4px; margin-bottom: 6px; }
                </style></head><body>
                """);

        if (messages.isEmpty()) {
            html.append("<p>No coaching conversation yet.</p>")
                    .append("<p>Save a question draft below. It stays local and costs nothing.</p>");
        } else {
            for (CoachingMessage message : messages) {
                html.append("<div class='message'><div class='meta'><b>")
                        .append(escape(message.role().name()))
                        .append("</b> · ")
                        .append(escape(TIME.format(message.createdAt())))
                        .append("</div>");
                appendContext(html, message.context());
                html.append(markdown(message.content())).append("</div>");
            }
        }
        return html.append("</body></html>").toString();
    }

    private void appendContext(StringBuilder html, CoachingContext context) {
        if (context == null) return;
        html.append("<div class='context'><b>Context:</b> ");
        switch (context.scope()) {
            case MATCH -> html.append(referenceLink("[MATCH]", "Match"));
            case GAME -> html.append(referenceLink("[G" + context.gameNumber() + "]",
                    "Game " + context.gameNumber()));
            case TURN -> {
                html.append(referenceLink("[G" + context.gameNumber() + "]",
                        "Game " + context.gameNumber()));
                html.append(" · ");
                int turn = context.turns().first();
                html.append(referenceLink("[T" + turn + "]", "Turn " + turn));
            }
            case SELECTED_TURNS -> {
                html.append(referenceLink("[G" + context.gameNumber() + "]",
                        "Game " + context.gameNumber()));
                html.append(" · Turns ");
                boolean first = true;
                for (int turn : context.turns()) {
                    if (!first) html.append(", ");
                    html.append(referenceLink("[T" + turn + "]", String.valueOf(turn)));
                    first = false;
                }
            }
        }
        html.append("</div>");
    }

    private String markdown(String source) {
        StringBuilder out = new StringBuilder();
        boolean listOpen = false;
        for (String rawLine : source.split("\\R", -1)) {
            String line = rawLine.strip();
            if (line.isEmpty()) {
                if (listOpen) {
                    out.append("</ul>");
                    listOpen = false;
                }
                continue;
            }

            int heading = headingLevel(line);
            if (heading > 0) {
                if (listOpen) {
                    out.append("</ul>");
                    listOpen = false;
                }
                String content = line.substring(heading).strip();
                out.append("<h").append(heading).append(">")
                        .append(renderInline(content))
                        .append("</h").append(heading).append(">");
            } else if (line.startsWith("- ") || line.startsWith("* ") || line.startsWith("• ")) {
                if (!listOpen) {
                    out.append("<ul>");
                    listOpen = true;
                }
                out.append("<li>").append(renderInline(line.substring(2))).append("</li>");
            } else {
                if (listOpen) {
                    out.append("</ul>");
                    listOpen = false;
                }
                out.append("<p>").append(renderInline(line)).append("</p>");
            }
        }
        if (listOpen) out.append("</ul>");
        return out.toString();
    }

    private int headingLevel(String line) {
        int count = 0;
        while (count < line.length() && count < 3 && line.charAt(count) == '#') count++;
        return count > 0 && count < line.length() && Character.isWhitespace(line.charAt(count))
                ? count
                : 0;
    }

    private String renderInline(String text) {
        Matcher matcher = REFERENCE.matcher(text);
        StringBuilder rendered = new StringBuilder();
        int cursor = 0;
        while (matcher.find()) {
            rendered.append(escape(text.substring(cursor, matcher.start())));
            String token = matcher.group();
            rendered.append(referenceLink(token, referenceLabel(token)));
            cursor = matcher.end();
        }
        rendered.append(escape(text.substring(cursor)));
        return rendered.toString();
    }

    private String referenceLabel(String token) {
        Matcher card = Pattern.compile("\\[c(\\d+)(?:#\\d+)?]").matcher(token);
        if (!card.matches()) return token;
        String name = cardNames.get(Integer.parseInt(card.group(1)));
        return name == null ? token : name + " " + token;
    }

    private String referenceLink(String token, String label) {
        return "<a class='chip' href='ref:" + escapeAttribute(token) + "'>"
                + escape(label) + "</a>";
    }

    private Map<Integer, String> readCardNames(String reconstruction) {
        if (reconstruction == null || reconstruction.isBlank()) return Map.of();
        Map<Integer, String> names = new LinkedHashMap<>();
        Matcher matcher = CARD_DICTIONARY.matcher(reconstruction);
        while (matcher.find()) {
            names.put(Integer.parseInt(matcher.group(1)), matcher.group(2).strip());
        }
        return Map.copyOf(names);
    }

    private String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String escapeAttribute(String value) {
        return escape(value).replace("'", "&#39;");
    }
}
