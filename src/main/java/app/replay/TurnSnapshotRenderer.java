package app.replay;

import app.model.card.CardInfo;
import app.model.event.GameEvent;
import app.model.game.BoardPermanentSnapshot;
import app.model.game.PlayerTurnSnapshot;

import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Lays out and paints start-of-turn player and battlefield snapshots.
 * General replay chip painting is supplied by the host.
 */
final class TurnSnapshotRenderer {
    interface Host {
        Font font();
        Color foreground();
        Color colorOr(String key, Color fallback);
        Color blend(Color first, Color second, float amount);
        boolean paintSvg(
                Graphics2D graphics, String resource,
                int x, int y, int width, int height);
        int fragmentWidth(Graphics2D graphics, ReplayFragment fragment);
        void paintFragment(
                Graphics2D graphics, ReplayFragment fragment,
                int x, int topY, int lineHeight, GameEvent event);
        void paintPanel(
                Graphics2D graphics, int y, int width, int height, boolean snapshot);
    }

    private static final int CARD_PADDING = 11;
    private static final int LINE_HEIGHT = 36;
    private static final int EVENT_GAP = 9;
    private static final int OUTER_PADDING = 18;
    private final Host host;

    TurnSnapshotRenderer(Host host) {
        this.host = host;
    }

    int paint(Graphics2D graphics, GameEvent event,
              int y, int width, boolean draw) {
        List<PlayerTurnSnapshot> players = event.getTurnSnapshot();
        int innerWidth = width - CARD_PADDING * 2;
        int gap = 14;
        int columnWidth = players.size() <= 1
                ? innerWidth : (innerWidth - gap) / 2;
        List<List<Row>> columns = players.stream()
                .map(player -> rows(graphics, player, columnWidth))
                .toList();
        int lineHeight = Math.max(
                graphics.getFontMetrics().getHeight() + 5, LINE_HEIGHT);
        int contentRows = columns.stream().mapToInt(List::size).max().orElse(1);
        int titleHeight = graphics.getFontMetrics(
                host.font().deriveFont(Font.BOLD)).getHeight() + 8;
        int boxHeight = CARD_PADDING * 2 + titleHeight
                + contentRows * lineHeight;

        if (draw) {
            host.paintPanel(graphics, y, width, boxHeight, true);
            int x = OUTER_PADDING + CARD_PADDING;
            int titleBaseline = y + CARD_PADDING
                    + graphics.getFontMetrics(
                            host.font().deriveFont(Font.BOLD)).getAscent();
            graphics.setFont(host.font().deriveFont(Font.BOLD));
            graphics.setColor(host.colorOr(
                    "Label.foreground", host.foreground()));
            graphics.drawString("Start of first main", x, titleBaseline);
            graphics.setFont(host.font());

            int top = y + CARD_PADDING + titleHeight;
            for (int index = 0; index < columns.size(); index++) {
                int columnX = x + index * (columnWidth + gap);
                paintColumn(
                        graphics, event, columns.get(index),
                        columnX, top, columnWidth, lineHeight);
                if (index > 0) {
                    graphics.setColor(host.blend(
                            host.colorOr(
                                    "Separator.foreground",
                                    new Color(0xAAAAAA)),
                            host.colorOr(
                                    "TextArea.background", Color.WHITE),
                            .45f));
                    int dividerX = columnX - gap / 2;
                    graphics.drawLine(
                            dividerX, top, dividerX,
                            y + boxHeight - CARD_PADDING);
                }
            }
        }
        return y + boxHeight + EVENT_GAP;
    }

    private List<Row> rows(
            Graphics2D graphics, PlayerTurnSnapshot player, int width) {
        List<Row> rows = new ArrayList<>();
        rows.add(new Row(null, text(player.getPlayerName()), "", true));
        rows.add(new Row("/svg/ability-lifelink.svg",
                player.getLifeTotal() == null
                        ? "Life ?" : player.getLifeTotal() + " life",
                "", false));
        if (player.getPoisonCounters() != null
                && player.getPoisonCounters() > 0) {
            rows.add(new Row("/svg/ability-toxic.svg",
                    player.getPoisonCounters() + " poison", "", false));
        }
        rows.add(new Row("/svg/multiple.svg",
                player.getHandSize() == null
                        ? "Hand ?" : player.getHandSize() + " cards in hand",
                "", false));

        addKnownZoneRows(
                graphics, rows, width, "/svg/multiple.svg",
                "Known hand", player.getKnownHand());
        addKnownZoneRows(
                graphics, rows, width, "/svg/counter-skull.svg",
                "Graveyard", player.getKnownGraveyard());
        addKnownZoneRows(
                graphics, rows, width, "/svg/ability-foretell.svg",
                "Exile", player.getKnownExile());

        rows.add(new Row("/svg/land.svg", "Battlefield", "", true));
        if (player.getBattlefield().isEmpty()) {
            rows.add(new Row(null, "Empty", "", false));
        } else {
            List<BoardPermanentSnapshot> roots = roots(player.getBattlefield())
                    .stream()
                    .sorted(Comparator.comparing(
                            BoardPermanentSnapshot::getCard,
                            Comparator.nullsLast(cardComparator())))
                    .toList();
            addBattlefieldRows(
                    graphics, rows, width, "Lands",
                    roots.stream().filter(value -> typeGroup(value) == 0).toList());
            addBattlefieldRows(
                    graphics, rows, width, "Creatures",
                    roots.stream().filter(value -> typeGroup(value) == 1).toList());
            addBattlefieldRows(
                    graphics, rows, width, "Other permanents",
                    roots.stream().filter(value -> typeGroup(value) == 2).toList());

            List<BoardPermanentSnapshot> attachments =
                    player.getBattlefield().stream()
                            .filter(value ->
                                    value.getAttachedToLogicalObjectId() != null)
                            .sorted(Comparator.comparing(
                                    BoardPermanentSnapshot::getCard,
                                    Comparator.nullsLast(cardComparator())))
                            .toList();
            addBattlefieldRows(
                    graphics, rows, width, "Attachments", attachments);
        }
        return rows;
    }

    private void addBattlefieldRows(
            Graphics2D graphics, List<Row> rows, int width,
            String group, List<BoardPermanentSnapshot> permanents) {
        if (permanents.isEmpty()) return;
        rows.add(new Row(
                null, group + " (" + permanents.size() + ")", "", true));
        int columns = inferPermanentColumns(graphics, permanents, width);
        for (int offset = 0; offset < permanents.size(); offset += columns) {
            rows.add(Row.permanentGrid(
                    permanents.subList(
                            offset, Math.min(offset + columns, permanents.size())),
                    columns));
        }
    }

    private int inferPermanentColumns(
            Graphics2D graphics,
            List<BoardPermanentSnapshot> permanents,
            int width) {
        int gap = 8;
        int preferred = permanents.stream()
                .filter(permanent -> permanent.getCard() != null)
                .map(permanent -> new CardFragment(
                        permanent.getCard(),
                        permanent.getCard().getName(),
                        roomState(permanent),
                        permanent))
                .mapToInt(fragment -> host.fragmentWidth(graphics, fragment))
                .max().orElse(0);
        for (int columns = Math.min(3, permanents.size());
             columns >= 2; columns--) {
            int cellWidth = (width - gap * (columns - 1)) / columns;
            if (cellWidth >= Math.min(preferred, 205)) return columns;
        }
        return 1;
    }

    private void addKnownZoneRows(
            Graphics2D graphics, List<Row> rows, int width,
            String icon, String zone, List<CardInfo> cards) {
        if (cards.isEmpty()) return;
        List<CardInfo> ordered = cards.stream()
                .filter(Objects::nonNull)
                .sorted(cardComparator())
                .toList();
        rows.add(new Row(
                icon, zone + " (" + cards.size() + " known)", "", true));
        int columns = inferCardColumns(graphics, ordered, width);
        for (int offset = 0; offset < ordered.size(); offset += columns) {
            rows.add(Row.cardGrid(
                    ordered.subList(
                            offset, Math.min(offset + columns, ordered.size())),
                    columns));
        }
    }

    private int inferCardColumns(
            Graphics2D graphics, List<CardInfo> cards, int width) {
        int gap = 8;
        int preferred = cards.stream()
                .map(card -> new CardFragment(card, card.getName(), "", null))
                .mapToInt(fragment -> host.fragmentWidth(graphics, fragment))
                .max().orElse(0);
        for (int columns = Math.min(3, cards.size()); columns >= 2; columns--) {
            int cellWidth = (width - gap * (columns - 1)) / columns;
            if (cellWidth >= Math.min(preferred, 190)) return columns;
        }
        return 1;
    }

    private void paintColumn(
            Graphics2D graphics, GameEvent event, List<Row> rows,
            int x, int top, int width, int lineHeight) {
        int y = top;
        for (Row row : rows) {
            int textX = x;
            if (row.iconResource() != null) {
                int iconSize = 14;
                int iconY = y + (lineHeight - iconSize) / 2;
                if (host.paintSvg(
                        graphics, row.iconResource(),
                        textX, iconY, iconSize, iconSize)) {
                    textX += iconSize + 6;
                }
            }
            if (!row.permanents().isEmpty()) {
                paintPermanentGrid(
                        graphics, event, row, x, y, width, lineHeight);
            } else if (!row.cards().isEmpty()) {
                paintCardGrid(graphics, event, row, x, y, width, lineHeight);
            } else if (row.card() != null) {
                paintCardRow(
                        graphics, event, row, x, y, width, lineHeight, textX);
            } else {
                graphics.setFont(row.heading()
                        ? host.font().deriveFont(Font.BOLD)
                        : host.font());
                graphics.setColor(row.heading()
                        ? host.colorOr("Label.foreground", host.foreground())
                        : host.colorOr("TextArea.foreground", host.foreground()));
                graphics.drawString(
                        row.text(), textX, baseline(graphics, y, lineHeight));
                graphics.setFont(host.font());
            }
            y += lineHeight;
        }
    }

    private void paintPermanentGrid(
            Graphics2D graphics, GameEvent event, Row row,
            int x, int y, int width, int lineHeight) {
        int gap = 8;
        int cellWidth = (width - gap * (row.permanentColumns() - 1))
                / row.permanentColumns();
        for (int index = 0; index < row.permanents().size(); index++) {
            BoardPermanentSnapshot permanent = row.permanents().get(index);
            if (permanent.getCard() == null) continue;
            int cellX = x + index * (cellWidth + gap);
            Graphics2D cell = (Graphics2D) graphics.create();
            try {
                cell.clipRect(
                        cellX - 7, y - 10, cellWidth + 14, lineHeight + 20);
                host.paintFragment(
                        cell,
                        new CardFragment(
                                permanent.getCard(),
                                permanent.getCard().getName(),
                                roomState(permanent),
                                permanent),
                        cellX, y, lineHeight, event);
            } finally {
                cell.dispose();
            }
        }
    }

    private void paintCardGrid(
            Graphics2D graphics, GameEvent event, Row row,
            int x, int y, int width, int lineHeight) {
        int gap = 8;
        int cellWidth = (width - gap * (row.cardColumns() - 1))
                / row.cardColumns();
        for (int index = 0; index < row.cards().size(); index++) {
            CardInfo card = row.cards().get(index);
            int cellX = x + index * (cellWidth + gap);
            Graphics2D cell = (Graphics2D) graphics.create();
            try {
                cell.clipRect(
                        cellX - 7, y - 4, cellWidth + 14, lineHeight + 8);
                host.paintFragment(
                        cell,
                        new CardFragment(card, card.getName(), "", null),
                        cellX, y, lineHeight, event);
            } finally {
                cell.dispose();
            }
        }
    }

    private void paintCardRow(
            Graphics2D graphics, GameEvent event, Row row,
            int x, int y, int width, int lineHeight, int initialTextX) {
        int textX = initialTextX;
        if (!row.text().isBlank()) {
            graphics.setColor(host.colorOr(
                    "TextArea.foreground", host.foreground()));
            graphics.drawString(row.text(), textX, baseline(graphics, y, lineHeight));
            textX += graphics.getFontMetrics().stringWidth(row.text());
        }
        String state = row.suffix().startsWith("unlocked:")
                ? row.suffix() : "";
        CardFragment fragment = new CardFragment(
                row.card(), row.card().getName(), state, row.permanent());
        int chipWidth = Math.min(
                host.fragmentWidth(graphics, fragment),
                Math.max(80, width - (textX - x)));
        if (chipWidth > 0) {
            host.paintFragment(
                    graphics, fragment, textX, y, lineHeight, event);
        }
        if (!row.suffix().isBlank() && state.isBlank()) {
            int suffixX = textX + host.fragmentWidth(graphics, fragment) + 6;
            graphics.setColor(host.colorOr(
                    "Label.disabledForeground", host.foreground()));
            graphics.drawString(
                    row.suffix(), suffixX, baseline(graphics, y, lineHeight));
        }
    }

    private int baseline(Graphics2D graphics, int y, int lineHeight) {
        return y + (lineHeight - graphics.getFontMetrics().getHeight()) / 2
                + graphics.getFontMetrics().getAscent();
    }

    private Comparator<CardInfo> cardComparator() {
        return Comparator.comparingInt(this::cardTypeRank)
                .thenComparing(
                        card -> text(card.getTypeLine()),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(
                        card -> text(card.getName()),
                        String.CASE_INSENSITIVE_ORDER);
    }

    private int cardTypeRank(CardInfo card) {
        String typeLine = text(card.getTypeLine()).toLowerCase(Locale.ROOT);
        if (typeLine.contains("land")) return 0;
        if (typeLine.contains("creature")) return 1;
        if (typeLine.contains("enchantment")) return 2;
        if (typeLine.contains("artifact")) return 3;
        if (typeLine.contains("planeswalker")) return 4;
        if (typeLine.contains("battle")) return 5;
        if (typeLine.contains("instant")) return 6;
        if (typeLine.contains("sorcery")) return 7;
        return 8;
    }

    private int typeGroup(BoardPermanentSnapshot permanent) {
        CardInfo card = permanent.getCard();
        String typeLine = card == null
                ? "" : text(card.getTypeLine()).toLowerCase(Locale.ROOT);
        if (typeLine.contains("land")) return 0;
        if (typeLine.contains("creature")) return 1;
        return 2;
    }

    private List<BoardPermanentSnapshot> roots(
            List<BoardPermanentSnapshot> battlefield) {
        Set<Long> ids = new HashSet<>();
        battlefield.forEach(permanent ->
                ids.add(permanent.getLogicalObjectId()));
        return battlefield.stream()
                .filter(permanent ->
                        permanent.getAttachedToLogicalObjectId() == null
                                || !ids.contains(
                                        permanent.getAttachedToLogicalObjectId()))
                .toList();
    }

    private String roomState(BoardPermanentSnapshot permanent) {
        return permanent.getUnlockedRoomHalves().isEmpty()
                ? ""
                : "unlocked: "
                        + String.join(", ", permanent.getUnlockedRoomHalves());
    }

    private String text(String value) {
        return value == null ? "" : value;
    }

    private record Row(
            String iconResource,
            String text,
            String suffix,
            boolean heading,
            CardInfo card,
            BoardPermanentSnapshot permanent,
            List<CardInfo> cards,
            int cardColumns,
            List<BoardPermanentSnapshot> permanents,
            int permanentColumns) {
        private Row(
                String iconResource, String text,
                String suffix, boolean heading) {
            this(iconResource, text, suffix, heading,
                    null, null, List.of(), 1, List.of(), 1);
        }

        private static Row cardGrid(List<CardInfo> cards, int columns) {
            return new Row(
                    null, "", "", false, null, null,
                    List.copyOf(cards), columns, List.of(), 1);
        }

        private static Row permanentGrid(
                List<BoardPermanentSnapshot> permanents, int columns) {
            return new Row(
                    null, "", "", false, null, null,
                    List.of(), 1, List.copyOf(permanents), columns);
        }
    }
}
