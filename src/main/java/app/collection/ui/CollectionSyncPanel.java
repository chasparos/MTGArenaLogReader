package app.collection.ui;

import app.collection.CollectionUpdate;
import app.model.card.CardInfo;
import app.replay.ReplayCardChip;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Application-owned collection synchronization wizard; provider details remain behind the protocol. */
public final class CollectionSyncPanel extends JPanel {
    @FunctionalInterface
    public interface CardArtworkSource {
        CompletionStage<Optional<BufferedImage>> load(CollectionUpdate.CardOption card);

        static CardArtworkSource none() {
            return ignored -> CompletableFuture.completedFuture(Optional.empty());
        }
    }

    @FunctionalInterface
    public interface CardPresentationSource {
        Optional<CardInfo> find(CollectionUpdate.CardOption card);

        static CardPresentationSource none() { return ignored -> Optional.empty(); }
    }

    private static final Color ACCENT = new Color(0x6D5BD0);
    private static final Color SUCCESS = new Color(0x56AE78);
    private final CollectionUpdate update;
    private final CardArtworkSource artwork;
    private final CardPresentationSource cardPresentation;
    private final CardLayout pages = new CardLayout();
    private final JPanel pageHost = new JPanel(pages);
    private final JLabel status = new JLabel("We’ll guide you through this safely");
    private final JTextField search = new JTextField();
    private final DefaultListModel<CollectionUpdate.CardOption> matches = new DefaultListModel<>();
    private final JList<CollectionUpdate.CardOption> matchList = new JList<>(matches);
    private final JPopupMenu suggestionsPopup = new JPopupMenu();
    private final JPanel selectedCards = new JPanel();
    private final JButton continueButton = new JButton("Let’s get started");
    private final JButton confirmButton = new JButton("These look right");
    private final JButton cardsCancelButton = new JButton("Cancel");
    private final JButton progressCancelButton = new JButton("Cancel");
    private final JProgressBar progress = new JProgressBar();
    private final JLabel completionHeading = new JLabel("Collection synchronized");
    private final JTextArea completionSummary = text("");
    private final JPanel completionStatistics = new JPanel(new GridLayout(1, 3, 12, 0));
    private final JLabel cardboardWeight = new JLabel("", SwingConstants.CENTER);
    private final Map<Long, Selection> selections = new LinkedHashMap<>();
    private List<CollectionUpdate.CardOption> suggestions = List.of();
    private CollectionUpdate.Session session;
    private int minimumCards = 2;

    public CollectionSyncPanel(CollectionUpdate update) {
        this(update, CardArtworkSource.none(), CardPresentationSource.none());
    }

    public CollectionSyncPanel(CollectionUpdate update, CardArtworkSource artwork) {
        this(update, artwork, CardPresentationSource.none());
    }

    public CollectionSyncPanel(CollectionUpdate update, CardArtworkSource artwork,
                               CardPresentationSource cardPresentation) {
        super(new BorderLayout(12, 12));
        this.update = Objects.requireNonNull(update);
        this.artwork = Objects.requireNonNull(artwork);
        this.cardPresentation = Objects.requireNonNull(cardPresentation);
        setBorder(new EmptyBorder(22, 28, 22, 28));
        add(title(), BorderLayout.NORTH);
        pageHost.setOpaque(false);
        pageHost.add(introduction(), "intro");
        pageHost.add(cardsPage(), "cards");
        pageHost.add(progressPage(), "progress");
        pageHost.add(completionPage(), "complete");
        add(pageHost, BorderLayout.CENTER);
        continueButton.addActionListener(event -> begin());
        confirmButton.addActionListener(event -> confirmCards());
        cardsCancelButton.addActionListener(event -> cancel());
        progressCancelButton.addActionListener(event -> cancel());
        search.getDocument().addDocumentListener((SimpleDocumentListener) this::filterMatches);
        matchList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        matchList.setCellRenderer(new CardOptionRenderer());
        matchList.setFocusable(false);
        matchList.setRequestFocusEnabled(false);
        matchList.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override public void mouseClicked(java.awt.event.MouseEvent event) {
                int index = matchList.locationToIndex(event.getPoint());
                if (index >= 0) {
                    matchList.setSelectedIndex(index);
                    addSelectedMatch();
                }
            }
        });
        installAutocompleteKeys();
        JScrollPane popupScroll = new JScrollPane(matchList);
        popupScroll.setPreferredSize(new Dimension(460, 190));
        popupScroll.setFocusable(false);
        popupScroll.setRequestFocusEnabled(false);
        popupScroll.getVerticalScrollBar().setFocusable(false);
        suggestionsPopup.setFocusable(false);
        suggestionsPopup.setRequestFocusEnabled(false);
        suggestionsPopup.setBorder(BorderFactory.createLineBorder(ACCENT));
        suggestionsPopup.add(popupScroll);
        stylePrimary(continueButton);
        stylePrimary(confirmButton);
        pages.show(pageHost, "intro");
    }

    private JComponent title() {
        JPanel panel = transparent(new BorderLayout(0, 5));
        JLabel heading = new JLabel("Sync your Arena collection");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 24f));
        panel.add(heading, BorderLayout.NORTH);
        status.setForeground(ACCENT);
        status.setFont(status.getFont().deriveFont(Font.BOLD));
        panel.add(status, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent introduction() {
        JPanel panel = transparent(new BorderLayout(12, 20));
        JTextArea explanation = text("Let’s bring your Arena collection into the app. Your cards stay yours: "
                + "we only read the running Arena client, and nothing is changed in Arena.\n\n"
                + "1. Start MTG Arena and sign in.\n"
                + "2. Click Decks, then Collection.\n"
                + "3. We’ll ask you to confirm a few cards and quantities.");
        explanation.setFont(explanation.getFont().deriveFont(16f));
        panel.add(explanation, BorderLayout.CENTER);
        JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT));
        actions.add(continueButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent cardsPage() {
        JPanel panel = transparent(new BorderLayout(10, 10));
        panel.add(text("Search by card name, choose the exact set, then tell us how many you own. "
                + "Two cards are enough; a full playset is especially helpful next time."), BorderLayout.NORTH);
        JPanel body = transparent(new BorderLayout(8, 8));
        JPanel picker = transparent(new BorderLayout(6, 6));
        search.setToolTipText("Type a card name");
        picker.add(labeled("Find a card", search), BorderLayout.NORTH);
        picker.add(text("Start typing, then select the exact printing from the suggestions."), BorderLayout.CENTER);
        body.add(picker, BorderLayout.NORTH);
        selectedCards.setLayout(new BoxLayout(selectedCards, BoxLayout.Y_AXIS));
        selectedCards.setOpaque(false);
        JPanel chosen = transparent(new BorderLayout(6, 6));
        chosen.add(new JLabel("Your selected cards"), BorderLayout.NORTH);
        chosen.add(new JScrollPane(selectedCards), BorderLayout.CENTER);
        body.add(chosen, BorderLayout.CENTER);
        panel.add(body, BorderLayout.CENTER);
        JPanel actions = transparent(new FlowLayout(FlowLayout.RIGHT));
        actions.add(cardsCancelButton);
        actions.add(confirmButton);
        panel.add(actions, BorderLayout.SOUTH);
        return panel;
    }

    private JComponent progressPage() {
        JPanel panel = transparent(new GridBagLayout());
        JPanel content = transparent();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        JLabel message = new JLabel("Safely reading your collection…", SwingConstants.CENTER);
        message.setFont(message.getFont().deriveFont(Font.BOLD, 16f));
        message.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress.setIndeterminate(true);
        progress.setPreferredSize(new Dimension(380, 18));
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        progressCancelButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        content.add(message);
        content.add(Box.createVerticalStrut(16));
        content.add(progress);
        content.add(Box.createVerticalStrut(16));
        content.add(progressCancelButton);
        panel.add(content);
        return panel;
    }

    private JComponent completionPage() {
        JPanel panel = transparent(new BorderLayout(10, 18));
        JPanel hero = transparent();
        hero.setLayout(new BoxLayout(hero, BoxLayout.Y_AXIS));
        JLabel check = new JLabel("✓", SwingConstants.CENTER);
        check.setFont(check.getFont().deriveFont(Font.BOLD, 48f));
        check.setForeground(SUCCESS);
        check.setAlignmentX(Component.CENTER_ALIGNMENT);
        completionHeading.setFont(completionHeading.getFont().deriveFont(Font.BOLD, 24f));
        completionHeading.setAlignmentX(Component.CENTER_ALIGNMENT);
        hero.add(check);
        hero.add(completionHeading);
        panel.add(hero, BorderLayout.NORTH);
        JPanel report = transparent(new BorderLayout(10, 16));
        completionSummary.setFont(completionSummary.getFont().deriveFont(15f));
        report.add(completionSummary, BorderLayout.NORTH);
        completionStatistics.setOpaque(false);
        report.add(completionStatistics, BorderLayout.CENTER);
        cardboardWeight.setFont(cardboardWeight.getFont().deriveFont(Font.ITALIC, 15f));
        cardboardWeight.setForeground(ACCENT);
        report.add(cardboardWeight, BorderLayout.SOUTH);
        panel.add(report, BorderLayout.CENTER);
        return panel;
    }

    private void begin() {
        continueButton.setEnabled(false);
        status.setText("Looking for the running Arena client…");
        session = update.begin(this::receive);
        session.respond(new CollectionUpdate.Continue());
    }

    private void cancel() { if (session != null) session.cancel(); }

    private void receive(CollectionUpdate.Event event) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> receive(event));
            return;
        }
        if (event instanceof CollectionUpdate.Status value) status.setText(value.message());
        else if (event instanceof CollectionUpdate.CardsRequired value) showCards(value);
        else if (event instanceof CollectionUpdate.Completed value) {
            progress.setIndeterminate(false);
            status.setText(value.message());
            cardsCancelButton.setEnabled(false);
            progressCancelButton.setEnabled(false);
            showCompletion(value);
        }
    }

    public void navigationChanged(CollectionNavigationObserver.Step step) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> navigationChanged(step));
            return;
        }
        status.setText(step == CollectionNavigationObserver.Step.DECKS_OPEN
                ? "Thank you! I can see Decks now — click Collection."
                : "Perfect, your Collection is open. We’re ready when you are.");
    }

    private void showCompletion(CollectionUpdate.Completed completed) {
        CollectionUpdate.Summary summary = completed.summary();
        completionHeading.setText(completed.updated() ? "Your collection is ready!" : "Synchronization stopped");
        StringBuilder value = new StringBuilder();
        if (summary.catalogCardsExamined() > 0) {
            value.append("Out of ").append(summary.catalogCardsExamined()).append(" Arena cards, ");
        } else value.append("Great — ");
        value.append("you own copies of ").append(summary.distinctCardsOwned()).append(" different cards");
        if (summary.totalCopies() > 0) value.append(", with ").append(summary.totalCopies()).append(" copies altogether");
        value.append(".");
        completionSummary.setText(value.toString());
        completionSummary.setCaretPosition(0);
        completionStatistics.removeAll();
        completionStatistics.add(statColumn("Top sets", summary.sets(), 3));
        completionStatistics.add(statColumn("Rarity", summary.rarities(), 5));
        completionStatistics.add(statColumn("Colors", summary.colors(), 6));
        cardboardWeight.setText(summary.totalCopies() > 0
                ? "If your collection were real cardboard, it would weigh about "
                + formatCardboardWeight(summary.totalCopies()) + "."
                : "");
        pages.show(pageHost, "complete");
    }

    private static JComponent statColumn(String title, Map<String, Integer> values, int maximum) {
        RoundedPanel column = new RoundedPanel();
        column.setLayout(new BorderLayout(4, 8));
        column.setBorder(new EmptyBorder(12, 14, 12, 14));
        JLabel heading = new JLabel(title);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 15f));
        column.add(heading, BorderLayout.NORTH);
        StringBuilder rows = new StringBuilder();
        values.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(maximum).forEach(entry -> rows.append(entry.getKey()).append(": ")
                .append(entry.getValue()).append('\n'));
        JTextArea content = text(rows.isEmpty() ? "—" : rows.toString().stripTrailing());
        column.add(content, BorderLayout.CENTER);
        return column;
    }

    private static String formatCardboardWeight(int totalCopies) {
        double grams = totalCopies * 1.8d;
        if (grams >= 1000d) return String.format(Locale.ROOT, "%.1f kg", grams / 1000d);
        return Math.round(grams) + " g";
    }

    private void showCards(CollectionUpdate.CardsRequired event) {
        minimumCards = event.minimumCards();
        suggestions = event.suggestions();
        selections.clear();
        selectedCards.removeAll();
        search.setText("");
        filterMatches();
        status.setText(event.instruction());
        pages.show(pageHost, "cards");
    }

    private void filterMatches() {
        String needle = search.getText().strip().toLowerCase(Locale.ROOT);
        matches.clear();
        suggestions.stream().filter(card -> needle.isEmpty() || card.name().toLowerCase(Locale.ROOT).contains(needle))
                .filter(card -> !selections.containsKey(card.arenaId())).limit(10).forEach(matches::addElement);
        if (!matches.isEmpty()) matchList.setSelectedIndex(0);
        if (search.isShowing() && !needle.isEmpty() && !matches.isEmpty()) {
            suggestionsPopup.show(search, 0, search.getHeight());
        } else suggestionsPopup.setVisible(false);
    }

    private void addSelectedMatch() {
        CollectionUpdate.CardOption card = matchList.getSelectedValue();
        if (card == null || selections.containsKey(card.arenaId())) return;
        suggestionsPopup.setVisible(false);
        Selection selection = new Selection(card);
        selections.put(card.arenaId(), selection);
        selectedCards.add(selection.item);
        selectedCards.add(Box.createVerticalStrut(8));
        selectedCards.revalidate();
        selectedCards.repaint();
        search.setText("");
        status.setText("Nice choice! Select how many copies Arena shows.");
    }

    private void installAutocompleteKeys() {
        InputMap input = search.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actions = search.getActionMap();
        input.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_DOWN, 0), "autocomplete.down");
        input.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_UP, 0), "autocomplete.up");
        input.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0), "autocomplete.accept");
        input.put(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ESCAPE, 0), "autocomplete.close");
        actions.put("autocomplete.down", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { moveSuggestion(1); }
        });
        actions.put("autocomplete.up", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { moveSuggestion(-1); }
        });
        actions.put("autocomplete.accept", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { addSelectedMatch(); }
        });
        actions.put("autocomplete.close", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) { suggestionsPopup.setVisible(false); }
        });
    }

    private void moveSuggestion(int direction) {
        if (matches.isEmpty()) return;
        int current = matchList.getSelectedIndex();
        int next = current < 0 ? 0 : Math.floorMod(current + direction, matches.size());
        matchList.setSelectedIndex(next);
        matchList.ensureIndexIsVisible(next);
        if (!suggestionsPopup.isVisible() && search.isShowing()) {
            suggestionsPopup.show(search, 0, search.getHeight());
        }
    }

    private void confirmCards() {
        Map<Long, Integer> verified = new LinkedHashMap<>();
        selections.forEach((id, selection) -> { if (selection.copies > 0) verified.put(id, selection.copies); });
        if (verified.size() < minimumCards) {
            status.setText("Almost there — choose 1–4 copies for at least " + minimumCards + " cards");
            return;
        }
        session.respond(new CollectionUpdate.VerifiedCards(verified));
        pages.show(pageHost, "progress");
        progress.setIndeterminate(true);
        progressCancelButton.setEnabled(true);
        session.respond(new CollectionUpdate.Continue());
    }

    private final class Selection {
        private final CollectionUpdate.CardOption card;
        private final RoundedPanel item = new RoundedPanel();
        private int copies;

        private Selection(CollectionUpdate.CardOption card) {
            this.card = card;
            item.setLayout(new BorderLayout(10, 0));
            item.setBorder(new EmptyBorder(8, 10, 8, 10));
            Dimension fixed = new Dimension(520, 104);
            item.setPreferredSize(fixed);
            item.setMinimumSize(new Dimension(360, 104));
            item.setMaximumSize(new Dimension(Integer.MAX_VALUE, 104));
            ArtworkLabel image = new ArtworkLabel(card.setCode());
            CardInfo cardInfo = cardPresentation.find(card).orElseGet(() -> presentationCard(card));
            ReplayCardChip chip = new ReplayCardChip(cardInfo, false, .9f);
            chip.setToolTipText(card.name() + " — " + card.setCode().toUpperCase(Locale.ROOT)
                    + " #" + card.collectorNumber());
            item.add(chip, BorderLayout.CENTER);
            JPanel right = transparent(new FlowLayout(FlowLayout.RIGHT, 8, 1));
            right.add(image);
            JPanel count = transparent(new FlowLayout(FlowLayout.LEFT, 3, 28));
            ButtonGroup group = new ButtonGroup();
            for (int quantity = 0; quantity <= 4; quantity++) {
                JToggleButton button = new JToggleButton("×" + quantity);
                button.setMargin(new Insets(3, 6, 3, 6));
                int selected = quantity;
                button.addActionListener(event -> copies = selected);
                group.add(button);
                count.add(button);
                if (quantity == 0) button.setSelected(true);
            }
            right.add(count);
            JButton remove = new JButton("×");
            remove.setToolTipText("Remove " + card.name());
            remove.setMargin(new Insets(3, 8, 3, 8));
            remove.addActionListener(event -> removeSelection(card.arenaId()));
            right.add(remove);
            item.add(right, BorderLayout.EAST);
            artwork.load(card).whenComplete((loaded, error) -> {
                if (error == null && loaded != null && loaded.isPresent()) {
                    SwingUtilities.invokeLater(() -> image.setArtwork(loaded.get()));
                }
            });
        }
    }

    private void removeSelection(long arenaId) {
        Selection removed = selections.remove(arenaId);
        if (removed == null) return;
        selectedCards.removeAll();
        selections.values().forEach(selection -> {
            selectedCards.add(selection.item);
            selectedCards.add(Box.createVerticalStrut(8));
        });
        selectedCards.revalidate();
        selectedCards.repaint();
        filterMatches();
        status.setText("Card removed — choose another whenever you’re ready.");
    }

    private static CardInfo presentationCard(CollectionUpdate.CardOption option) {
        CardInfo card = new CardInfo();
        card.setArenaId(option.arenaId());
        card.setName(option.name());
        card.setSet(option.setCode());
        card.setSetName(option.setName());
        card.setCollectorNumber(option.collectorNumber());
        return card;
    }

    private static final class ArtworkLabel extends JComponent {
        private BufferedImage image;
        private final String fallback;
        private ArtworkLabel(String fallback) { this.fallback = fallback; setPreferredSize(new Dimension(62, 86)); }
        private void setArtwork(BufferedImage image) { this.image = image; repaint(); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Shape clip = new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 9, 9);
            g.clip(clip);
            if (image != null) g.drawImage(image, 0, 0, getWidth(), getHeight(), null);
            else {
                g.setPaint(new GradientPaint(0, 0, ACCENT.brighter(), getWidth(), getHeight(), ACCENT.darker()));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setColor(Color.WHITE);
                g.setFont(getFont().deriveFont(Font.BOLD, 13f));
                FontMetrics fm = g.getFontMetrics();
                g.drawString(fallback.toUpperCase(Locale.ROOT), (getWidth() - fm.stringWidth(fallback)) / 2, getHeight() / 2);
            }
            g.dispose();
        }
    }

    private static final class RoundedPanel extends JPanel {
        private RoundedPanel() { setOpaque(false); }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = UIManager.getColor("Panel.background");
            g.setColor(mix(base == null ? Color.DARK_GRAY : base, ACCENT, 0.14f));
            g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.setColor(mix(base == null ? Color.DARK_GRAY : base, ACCENT, 0.45f));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 16, 16);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static final class CardOptionRenderer extends DefaultListCellRenderer {
        @Override public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                                 boolean selected, boolean focus) {
            CollectionUpdate.CardOption card = (CollectionUpdate.CardOption) value;
            String set = card.setName().isBlank() ? card.setCode() : card.setName();
            return super.getListCellRendererComponent(list, card.name() + " — " + set + " #" + card.collectorNumber(),
                    index, selected, focus);
        }
    }

    private interface SimpleDocumentListener extends DocumentListener {
        void changed();
        @Override default void insertUpdate(DocumentEvent event) { changed(); }
        @Override default void removeUpdate(DocumentEvent event) { changed(); }
        @Override default void changedUpdate(DocumentEvent event) { changed(); }
    }

    private static JPanel transparent(LayoutManager layout) { JPanel panel = new JPanel(layout); panel.setOpaque(false); return panel; }
    private static JPanel transparent() { JPanel panel = new JPanel(); panel.setOpaque(false); return panel; }
    private static JComponent labeled(String label, JComponent component) {
        JPanel panel = transparent(new BorderLayout(4, 4)); panel.add(new JLabel(label), BorderLayout.NORTH); panel.add(component); return panel;
    }
    private static void stylePrimary(JButton button) { button.setFont(button.getFont().deriveFont(Font.BOLD)); }
    private static Color mix(Color a, Color b, float amount) {
        return new Color((int) (a.getRed() * (1 - amount) + b.getRed() * amount),
                (int) (a.getGreen() * (1 - amount) + b.getGreen() * amount),
                (int) (a.getBlue() * (1 - amount) + b.getBlue() * amount));
    }
    private static String html(String value) { return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;"); }
    private static JTextArea text(String value) {
        JTextArea area = new JTextArea(value); area.setEditable(false); area.setOpaque(false); area.setLineWrap(true);
        area.setWrapStyleWord(true); area.setFont(UIManager.getFont("Label.font")); return area;
    }
}
