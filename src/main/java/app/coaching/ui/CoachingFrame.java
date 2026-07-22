package app.coaching.ui;

import app.coaching.application.CoachingService;
import app.coaching.model.CoachingConversation;
import app.coaching.model.CoachingConversationSummary;
import app.coaching.model.CoachingMessage;
import app.model.session.MatchSession;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Browses explicitly persisted match reconstructions and their coaching transcript.
 *
 * <p>API submission is intentionally absent from this first increment. User drafts
 * can be saved without spending tokens and will become part of the conversation
 * sent by a later integration.</p>
 */
public final class CoachingFrame extends JFrame {
    private static final DateTimeFormatter TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final CoachingService service;
    private final DefaultListModel<CoachingConversationSummary> conversations =
            new DefaultListModel<>();
    private final JList<CoachingConversationSummary> conversationList =
            new JList<>(conversations);
    private final JTextArea transcript = textArea();
    private final JTextArea reconstruction = textArea();
    private final JTextArea draft = new JTextArea(4, 40);
    private final JLabel status = new JLabel("No coaching match selected");
    private CoachingConversation selected;

    public CoachingFrame(CoachingService service) {
        super("Match Coaching");
        this.service = service;
        initialize();
        reloadList(null);
    }

    public void open(MatchSession match) {
        CoachingConversation conversation = service.saveForCoaching(match);
        reloadList(conversation.id());
        setVisible(true);
        toFront();
    }

    private void initialize() {
        setDefaultCloseOperation(WindowConstants.HIDE_ON_CLOSE);
        setSize(980, 700);
        setLocationByPlatform(true);

        conversationList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        conversationList.setCellRenderer(new ConversationRenderer());
        conversationList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) loadSelectedConversation();
        });

        JScrollPane browser = new JScrollPane(conversationList);
        browser.setPreferredSize(new Dimension(250, 0));

        JTabbedPane details = new JTabbedPane();
        details.addTab("Conversation", conversationPanel());
        details.addTab("Match reconstruction", new JScrollPane(reconstruction));

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browser, details);
        split.setResizeWeight(0.24);
        split.setDividerLocation(250);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(8, 8, 8, 8));
        root.add(split, BorderLayout.CENTER);
        root.add(status, BorderLayout.SOUTH);
        setContentPane(root);
    }

    private JPanel conversationPanel() {
        draft.setLineWrap(true);
        draft.setWrapStyleWord(true);

        JButton saveDraft = new JButton("Save question draft");
        saveDraft.setToolTipText("Save locally without contacting OpenAI or spending tokens");
        saveDraft.addActionListener(event -> saveDraft());

        JLabel hint = new JLabel(
                "Drafts are local only. No API request is made in this version.");

        JPanel composerFooter = new JPanel(new BorderLayout(8, 0));
        composerFooter.add(hint, BorderLayout.CENTER);
        composerFooter.add(saveDraft, BorderLayout.EAST);

        JPanel composer = new JPanel(new BorderLayout(0, 6));
        composer.setBorder(new EmptyBorder(8, 0, 0, 0));
        composer.add(new JScrollPane(draft), BorderLayout.CENTER);
        composer.add(composerFooter, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JScrollPane(transcript), BorderLayout.CENTER);
        panel.add(composer, BorderLayout.SOUTH);
        return panel;
    }

    private void saveDraft() {
        if (selected == null) {
            status.setText("Select a coaching match first");
            return;
        }
        String content = draft.getText().trim();
        if (content.isEmpty()) {
            status.setText("Question draft is empty");
            return;
        }

        service.saveUserDraft(selected.id(), content);
        draft.setText("");
        selectConversation(selected.id());
        status.setText("Question saved locally — no API request made");
    }

    private void loadSelectedConversation() {
        CoachingConversationSummary summary = conversationList.getSelectedValue();
        if (summary == null) {
            selected = null;
            transcript.setText("");
            reconstruction.setText("");
            return;
        }

        selected = service.conversation(summary.id());
        reconstruction.setText(selected.reconstruction());
        reconstruction.setCaretPosition(0);
        transcript.setText(formatTranscript(selected.messages()));
        transcript.setCaretPosition(transcript.getDocument().getLength());
        status.setText("Match " + shortMatchId(selected.matchId())
                + " — " + selected.messages().size()
                + (selected.messages().size() == 1 ? " message" : " messages"));
    }

    private String formatTranscript(List<CoachingMessage> messages) {
        if (messages.isEmpty()) {
            return """
                    No coaching conversation yet.

                    Save a question draft below. It stays local and costs nothing.
                    """;
        }

        StringBuilder out = new StringBuilder();
        for (CoachingMessage message : messages) {
            out.append(message.role())
                    .append("  ")
                    .append(TIME.format(message.createdAt()))
                    .append(System.lineSeparator())
                    .append(message.content())
                    .append(System.lineSeparator())
                    .append(System.lineSeparator());
        }
        return out.toString();
    }

    private void reloadList(Long selectId) {
        conversations.clear();
        for (CoachingConversationSummary summary : service.conversations()) {
            conversations.addElement(summary);
        }
        if (selectId != null) selectConversation(selectId);
    }

    private void selectConversation(long id) {
        for (int index = 0; index < conversations.size(); index++) {
            if (conversations.get(index).id() == id) {
                conversationList.setSelectedIndex(index);
                conversationList.ensureIndexIsVisible(index);
                loadSelectedConversation();
                return;
            }
        }
    }

    private static JTextArea textArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        return area;
    }

    private String shortMatchId(String matchId) {
        if (matchId == null || matchId.isBlank()) return "unknown";
        return matchId.length() <= 8 ? matchId : matchId.substring(0, 8);
    }

    private static final class ConversationRenderer
            extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(
                JList<?> list,
                Object value,
                int index,
                boolean selected,
                boolean focused) {
            super.getListCellRendererComponent(list, value, index, selected, focused);
            if (value instanceof CoachingConversationSummary summary) {
                String match = summary.matchId().length() <= 8
                        ? summary.matchId()
                        : summary.matchId().substring(0, 8);
                setText("<html><b>" + match + "</b><br>"
                        + TIME.format(summary.updatedAt()) + " · "
                        + summary.messageCount() + " messages</html>");
                setBorder(new EmptyBorder(6, 8, 6, 8));
            }
            return this;
        }
    }
}
