package app.coaching.ui;

import app.coaching.application.CoachingService;
import app.coaching.model.CoachingConversation;
import app.coaching.model.CoachingConversationSummary;
import app.coaching.model.CoachingMessage;
import app.coaching.model.CoachingGame;
import app.coaching.model.CoachingContext;
import app.model.event.GameEvent;
import app.model.event.GameEventType;
import app.model.session.MatchSession;
import app.replay.GameView;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;

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
    private final RichConversationView transcript = new RichConversationView();
    private final JTabbedPane games = new JTabbedPane();
    private final Map<Integer, GameView> gameViews = new LinkedHashMap<>();
    private final JTextArea draft = new JTextArea(4, 40);
    private final JLabel status = new JLabel("No coaching match selected");
    private CoachingConversation selected;
    private ManualContext manualContext = ManualContext.match();

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

        transcript.setCoordinator(this::navigateTo);

        JSplitPane workspace = new JSplitPane(
                JSplitPane.HORIZONTAL_SPLIT,
                games,
                conversationPanel());
        workspace.setResizeWeight(0.55);
        workspace.setDividerLocation(430);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, browser, workspace);
        split.setResizeWeight(0.22);
        split.setDividerLocation(230);

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

        JButton copyPrompt = new JButton("Translate to AI-speak into clipboard");
        copyPrompt.setToolTipText("Build a scoped coaching prompt and copy it; no API request is made");
        copyPrompt.addActionListener(event -> copyManualPrompt());

        JButton importReply = new JButton("Translate clipboard from AI-speak");
        importReply.setToolTipText("Persist the clipboard text as the assistant's exact reply");
        importReply.addActionListener(event -> importManualReply());

        JButton copyReconstruction = new JButton("AI reconstruction to clipboard");
        copyReconstruction.setToolTipText("Copy the persisted match reconstruction without instructions");
        copyReconstruction.addActionListener(event -> copyReconstruction());

        JLabel hint = new JLabel(
                "Manual copy/paste coaching only. These actions never call an API.");

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        actions.add(saveDraft);
        actions.add(copyPrompt);
        actions.add(importReply);
        actions.add(copyReconstruction);

        JPanel composerFooter = new JPanel(new BorderLayout(0, 4));
        composerFooter.add(hint, BorderLayout.NORTH);
        composerFooter.add(actions, BorderLayout.SOUTH);

        JPanel composer = new JPanel(new BorderLayout(0, 6));
        composer.setBorder(new EmptyBorder(8, 0, 0, 0));
        composer.add(new JScrollPane(draft), BorderLayout.CENTER);
        composer.add(composerFooter, BorderLayout.SOUTH);

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(transcript, BorderLayout.CENTER);
        panel.add(composer, BorderLayout.SOUTH);
        return panel;
    }

    private void copyManualPrompt() {
        if (selected == null) {
            status.setText("Select a coaching match first");
            return;
        }
        String question = draft.getText().trim();
        if (question.isEmpty()) {
            status.setText("Enter or choose a coaching question first");
            return;
        }

        try {
            String prompt = service.manualPrompt(
                    selected,
                    manualContext.scope(),
                    manualContext.gameNumber(),
                    manualContext.turns(),
                    question);
            String contextLabel = manualContext.label();
            Toolkit.getDefaultToolkit().getSystemClipboard()
                    .setContents(new StringSelection(prompt), null);
            service.saveUserDraft(selected.id(), question, manualContext.toModel());
            draft.setText("");
            selectConversation(selected.id());
            status.setText("AI-speak copied for " + contextLabel
                    + " — paste it into ChatGPT");
        } catch (IllegalArgumentException | IllegalStateException error) {
            status.setText("Could not build coaching context: " + error.getMessage());
        }
    }

    private void importManualReply() {
        if (selected == null) {
            status.setText("Select a coaching match first");
            return;
        }
        try {
            Object value = Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            String reply = value == null ? "" : value.toString().trim();
            if (reply.isEmpty()) {
                status.setText("Clipboard does not contain a coaching reply");
                return;
            }
            service.saveMessage(selected.id(), CoachingMessage.Role.ASSISTANT, reply);
            selectConversation(selected.id());
            status.setText("Assistant reply imported and saved exactly as received");
        } catch (UnsupportedOperationException | IOException
                 | java.awt.datatransfer.UnsupportedFlavorException error) {
            status.setText("Could not read text from clipboard: " + error.getMessage());
        }
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

        service.saveUserDraft(selected.id(), content, manualContext.toModel());
        draft.setText("");
        selectConversation(selected.id());
        status.setText("Question saved locally — no API request made");
    }

    private void loadSelectedConversation() {
        CoachingConversationSummary summary = conversationList.getSelectedValue();
        if (summary == null) {
            selected = null;
            transcript.showConversation("", List.of());
            games.removeAll();
            gameViews.clear();
            return;
        }

        selected = service.conversation(summary.id());
        manualContext = ManualContext.match();
        showGames(selected.games());
        transcript.showConversation(selected.reconstruction(), selected.messages());
        status.setText("Match " + shortMatchId(selected.matchId())
                + " — " + selected.messages().size()
                + (selected.messages().size() == 1 ? " message" : " messages"));
    }


    private void showGames(List<CoachingGame> persistedGames) {
        games.removeAll();
        gameViews.clear();
        if (persistedGames.isEmpty()) {
            JTextArea empty = textArea();
            empty.setText("No human-readable game reconstruction was persisted.");
            games.addTab("No games", new JScrollPane(empty));
            return;
        }
        for (CoachingGame game : persistedGames) {
            games.addTab("Game " + game.gameNumber(), gameComponent(game));
        }
    }

    private JComponent gameComponent(CoachingGame game) {
        if (game.richSnapshot() == null || game.richSnapshot().isBlank()) {
            return textGameFallback(game, "Rich replay unavailable for this previously saved match.");
        }
        try {
            GameView view = new GameView(service.richGame(game));
            view.setCoachingActions(request -> prepareQuestion(game.gameNumber(), request));
            gameViews.put(game.gameNumber(), view);
            JLabel hint = new JLabel(
                    "Select a turn · Ctrl-click toggles · Shift-click selects a range · Right-click to ask");
            hint.setBorder(new EmptyBorder(5, 8, 5, 8));

            JPanel panel = new JPanel(new BorderLayout());
            panel.add(hint, BorderLayout.NORTH);
            panel.add(view, BorderLayout.CENTER);
            return panel;
        } catch (RuntimeException error) {
            return textGameFallback(game, "Rich replay could not be loaded; showing the saved text reconstruction.");
        }
    }

    private void prepareQuestion(int gameNumber, GameView.CoachingRequest request) {
        String context = switch (request.scope()) {
            case MATCH -> "Match";
            case GAME -> "Game " + gameNumber;
            case TURN -> "Game " + gameNumber + ", turn " + onlyTurn(request.turns());
            case SELECTED_TURNS -> "Game " + gameNumber + ", turns " + formatTurns(request.turns());
        };
        manualContext = new ManualContext(request.scope(), gameNumber, request.turns());
        String question = request.question() == null ? "" : request.question();
        draft.setText(question);
        draft.setCaretPosition(draft.getDocument().getLength());
        draft.requestFocusInWindow();
        status.setText("Prepared local coaching question for " + context);
    }

    private int onlyTurn(java.util.Set<Integer> turns) {
        return turns.stream().findFirst().orElseThrow();
    }

    private String formatTurns(java.util.Set<Integer> turns) {
        return turns.stream().sorted()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private JComponent textGameFallback(CoachingGame game, String reason) {
        JTextArea area = textArea();
        area.setText(reason + System.lineSeparator() + System.lineSeparator()
                + game.reconstruction());
        area.setCaretPosition(0);
        return new JScrollPane(area);
    }


    private void copyReconstruction() {
        if (selected == null) {
            status.setText("Select a coaching match first");
            return;
        }
        Toolkit.getDefaultToolkit().getSystemClipboard()
                .setContents(new StringSelection(selected.reconstruction()), null);
        status.setText("AI reconstruction copied to clipboard");
    }

    private void navigateTo(CoachingReference reference) {
        if (selected == null || reference == null) return;

        ReferencedEvent referencedEvent = isEventReference(reference)
                ? referencedEvent(reference.number())
                : null;
        Integer gameNumber = switch (reference.kind()) {
            case GAME -> reference.number();
            case TURN -> selectedGameNumber();
            case EVENT, ABILITY, DECISION, LIFE, RESULT, SNAPSHOT ->
                    referencedEvent == null ? gameForReference(reference) : referencedEvent.gameNumber();
            case CARD, MATCH -> selectedGameNumber();
        };

        if (gameNumber != null) selectGame(gameNumber);

        if (referencedEvent != null) {
            GameView view = gameViews.get(referencedEvent.gameNumber());
            if (view != null) view.navigateToEvent(referencedEvent.event());
            status.setText("Navigated to game " + referencedEvent.gameNumber()
                    + ", turn " + referencedEvent.event().getTurnNumber());
            return;
        }

        Integer turnNumber = switch (reference.kind()) {
            case TURN -> reference.number();
            case EVENT, ABILITY, DECISION, LIFE, RESULT, SNAPSHOT -> turnForReference(reference);
            default -> null;
        };
        if (turnNumber != null && gameNumber != null) {
            GameView view = gameViews.get(gameNumber);
            if (view != null) view.navigateToTurn(turnNumber);
            status.setText("Navigated to game " + gameNumber + ", turn " + turnNumber);
        } else if (gameNumber != null) {
            status.setText("Navigated to game " + gameNumber);
        }
    }

    private boolean isEventReference(CoachingReference reference) {
        return switch (reference.kind()) {
            case EVENT, ABILITY, DECISION, LIFE, RESULT, SNAPSHOT -> true;
            default -> false;
        };
    }

    /**
     * Reproduces the exporter's stable event numbering over the persisted rich
     * snapshots. This keeps navigation tied to semantic events rather than to
     * rendered text or Swing hitboxes.
     */
    private ReferencedEvent referencedEvent(int referenceNumber) {
        int nextReference = 1;
        for (Map.Entry<Integer, GameView> entry : gameViews.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList()) {
            for (GameEvent event : entry.getValue().getModel().snapshot()) {
                if (!isExportedEvent(event)) continue;
                if (nextReference++ == referenceNumber) {
                    return new ReferencedEvent(entry.getKey(), event);
                }
            }
        }
        return null;
    }

    private boolean isExportedEvent(GameEvent event) {
        return event.getType() != GameEventType.MATCH_STARTED
                && event.getType() != GameEventType.GAME_STARTED
                && event.getType() != GameEventType.OPENING_HAND;
    }

    private Integer selectedGameNumber() {
        int index = games.getSelectedIndex();
        if (index < 0) return gameViews.keySet().stream().findFirst().orElse(null);
        String title = games.getTitleAt(index);
        try {
            return Integer.parseInt(title.replaceAll("\\D", ""));
        } catch (NumberFormatException ignored) {
            return gameViews.keySet().stream().findFirst().orElse(null);
        }
    }

    private void selectGame(int gameNumber) {
        for (int index = 0; index < games.getTabCount(); index++) {
            if (games.getTitleAt(index).equals("Game " + gameNumber)) {
                games.setSelectedIndex(index);
                return;
            }
        }
    }

    private Integer gameForReference(CoachingReference reference) {
        String marker = referenceMarker(reference);
        if (marker == null) return selectedGameNumber();
        int game = 0;
        for (String line : selected.reconstruction().split("\\R")) {
            if (line.matches("G\\d+.*")) {
                String digits = line.substring(1).replaceAll("\\D.*", "");
                if (!digits.isEmpty()) game = Integer.parseInt(digits);
            }
            if (line.startsWith(marker)) return game == 0 ? selectedGameNumber() : game;
        }
        return selectedGameNumber();
    }

    private Integer turnForReference(CoachingReference reference) {
        String marker = referenceMarker(reference);
        if (marker == null) return null;
        Integer turn = null;
        for (String line : selected.reconstruction().split("\\R")) {
            if (line.matches("T\\d+.*")) {
                String digits = line.substring(1).replaceAll("\\D.*", "");
                if (!digits.isEmpty()) turn = Integer.parseInt(digits);
            }
            if (line.startsWith(marker)) return turn;
        }
        return null;
    }

    private String referenceMarker(CoachingReference reference) {
        return switch (reference.kind()) {
            case EVENT -> "E#" + reference.number();
            case ABILITY -> "A#" + reference.number();
            case DECISION -> "C#" + reference.number();
            case LIFE -> "L#" + reference.number();
            case RESULT -> "GR#" + reference.number();
            case SNAPSHOT -> "S#" + reference.number();
            default -> null;
        };
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

    private record ReferencedEvent(int gameNumber, GameEvent event) {
    }

    private record ManualContext(
            GameView.CoachingScope scope,
            Integer gameNumber,
            java.util.Set<Integer> turns) {
        private ManualContext {
            turns = turns == null ? java.util.Set.of() : java.util.Set.copyOf(turns);
        }

        static ManualContext match() {
            return new ManualContext(GameView.CoachingScope.MATCH, null, java.util.Set.of());
        }

        CoachingContext toModel() {

            return new CoachingContext(
                    CoachingContext.Scope.valueOf(scope.name()),
                    gameNumber,
                    new TreeSet<>(turns));
        }

        String label() {
            return switch (scope) {
                case MATCH -> "the match";
                case GAME -> "game " + gameNumber;
                case TURN -> "game " + gameNumber + ", turn " + turns.iterator().next();
                case SELECTED_TURNS -> "game " + gameNumber + ", selected turns";
            };
        }
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
