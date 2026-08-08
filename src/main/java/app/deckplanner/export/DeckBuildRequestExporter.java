package app.deckplanner.export;

import app.deckplanner.candidate.CandidateWorkspaceState;
import app.model.card.CardFaceInfo;
import app.model.card.CardInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.ToIntFunction;

/**
 * Deterministic, token-conscious DP-07 request exporter.
 *
 * <p>The protocol separates authoritative card facts, human-authored Candidate Set intent,
 * and strategic-analysis instructions so downstream models do not confuse inference with
 * observed card text.</p>
 */
public final class DeckBuildRequestExporter {
    public static final String VERSION = "MTGA_DECK_BUILD_REQUEST_V1";

    public String export(String format,
                         String candidateSetName,
                         String note,
                         List<String> identities,
                         CandidateWorkspaceState.Snapshot workspace,
                         Function<String, Optional<CardInfo>> resolver,
                         ToIntFunction<CardInfo> collectionQuantity) {
        List<String> ordered = List.copyOf(identities == null ? List.of() : identities);
        CandidateWorkspaceState.Snapshot categories =
                workspace == null ? CandidateWorkspaceState.defaults() : workspace;
        Function<String, Optional<CardInfo>> cardResolver =
                resolver == null ? ignored -> Optional.empty() : resolver;
        ToIntFunction<CardInfo> quantities =
                collectionQuantity == null ? ignored -> -1 : collectionQuantity;

        StringBuilder out = new StringBuilder(Math.max(4096, ordered.size() * 520));
        out.append(VERSION).append('\n');
        out.append("ENC quoted strings use backslash escaping; embedded newlines are \\\\n\n");
        out.append("SECTIONS authoritative-card-facts | human-design-intent | analysis-instructions\n");
        out.append("FORMAT ").append(q(normalizeFormat(format))).append('\n');
        out.append("SET name=").append(q(blankTo(candidateSetName, "Current Candidates"))).append('\n');
        out.append("NOTE ").append(q(note == null ? "" : note)).append('\n');

        LinkedHashMap<String, String> categoryAliases = categoryAliases(categories, ordered);
        if (!categoryAliases.isEmpty()) {
            StringJoiner joiner = new StringJoiner("|");
            categoryAliases.forEach((id, alias) ->
                    joiner.add(alias + "=" + q(categoryName(categories, id))));
            out.append("CATEGORIES ").append(joiner).append('\n');
        }

        out.append("AUTHORITATIVE\n");
        int aliasNo = 1;
        for (String identity : ordered) {
            String alias = "C" + aliasNo++;
            Optional<CardInfo> resolved = cardResolver.apply(identity);
            String categoryId = categoryFor(categories, identity);
            String categoryAlias = categoryAliases.getOrDefault(categoryId, "-");
            if (resolved.isEmpty()) {
                out.append("CARD ").append(alias)
                        .append(" id=").append(q(identity))
                        .append(" category=").append(categoryAlias)
                        .append(" status=UNRESOLVED\n");
                continue;
            }
            CardInfo card = resolved.get();
            int quantity = quantities.applyAsInt(card);
            appendCard(out, alias, identity, categoryAlias, quantity, card);
        }

        out.append("HUMAN_INTENT\n");
        out.append("The NOTE above is the human's design intent. Use it to guide emphasis; "
                + "do not treat it as card rules.\n");

        out.append("ANALYSIS_INSTRUCTIONS\n");
        out.append("I'm working on designing a MTGArena deck and I am looking at the included set of cards.\n");
        out.append("Treat the supplied card data as authoritative. Do not invent, substitute, or rewrite card rules.\n");
        out.append("Analyze plausible archetype directions and compare them when more than one is credible.\n");
        out.append("Identify synergy packages, interaction, card-advantage and recursion engines, mana-curve and mana-base implications, resilience and weaknesses, and plausible win conditions.\n");
        out.append("Call out candidates that probably should be removed, explain why, and suggest important missing cards or roles when useful.\n");
        out.append("Clearly separate observations grounded in supplied facts from strategic inference or speculation.\n");
        return out.toString();
    }

    private static void appendCard(StringBuilder out, String alias, String identity,
                                   String categoryAlias, int quantity, CardInfo card) {
        out.append("CARD ").append(alias)
                .append(" id=").append(q(identity))
                .append(" category=").append(categoryAlias)
                .append(" qty=").append(quantity)
                .append(" name=").append(q(card.getName()))
                .append(" scryfall=").append(q(card.getId()))
                .append(" oracle=").append(q(card.getOracleId()))
                .append(" arena=").append(card.getArenaId() == null ? 0 : card.getArenaId())
                .append(" mana=").append(q(card.getManaCost()))
                .append(" mv=").append(number(card.getCmc()))
                .append(" type=").append(q(card.effectiveTypeLine()))
                .append(" text=").append(q(card.effectiveOracleText()))
                .append(" colors=").append(list(card.getColors()))
                .append(" identity=").append(list(card.getColorIdentity()))
                .append(" pt=").append(q(pair(card.getPower(), card.getToughness())))
                .append(" loyalty=").append(q(card.getLoyalty()))
                .append(" defense=").append(q(card.getDefense()))
                .append(" keywords=").append(list(card.getKeywords()))
                .append(" produces=").append(list(card.getProducedMana()))
                .append('\n');

        List<CardFaceInfo> faces = card.getCardFaces() == null ? List.of() : card.getCardFaces();
        for (int index = 0; index < faces.size(); index++) {
            CardFaceInfo face = faces.get(index);
            if (face == null) continue;
            out.append("FACE ").append(alias).append('.').append(index + 1)
                    .append(" name=").append(q(face.getName()))
                    .append(" mana=").append(q(face.getManaCost()))
                    .append(" type=").append(q(face.getTypeLine()))
                    .append(" text=").append(q(face.getOracleText()))
                    .append(" colors=").append(list(face.getColors()))
                    .append(" pt=").append(q(pair(face.getPower(), face.getToughness())))
                    .append(" loyalty=").append(q(face.getLoyalty()))
                    .append(" defense=").append(q(face.getDefense()))
                    .append('\n');
        }
    }

    private static LinkedHashMap<String, String> categoryAliases(
            CandidateWorkspaceState.Snapshot workspace, List<String> identities) {
        LinkedHashMap<String, String> aliases = new LinkedHashMap<>();
        int next = 1;
        java.util.LinkedHashSet<String> used = new java.util.LinkedHashSet<>();
        for (String identity : identities) used.add(categoryFor(workspace, identity));
        for (CandidateWorkspaceState.Category category : workspace.categories()) {
            if (used.contains(category.id())) aliases.put(category.id(), "G" + next++);
        }
        boolean needsUncategorized = used.contains(CandidateWorkspaceState.UNCATEGORIZED);
        if (needsUncategorized && !aliases.containsKey(CandidateWorkspaceState.UNCATEGORIZED)) {
            aliases.put(CandidateWorkspaceState.UNCATEGORIZED, "G" + next);
        }
        return aliases;
    }

    private static String categoryFor(CandidateWorkspaceState.Snapshot workspace, String identity) {
        String assigned = workspace.assignments().get(identity);
        return assigned == null || assigned.isBlank()
                ? CandidateWorkspaceState.UNCATEGORIZED : assigned;
    }

    private static String categoryName(CandidateWorkspaceState.Snapshot workspace, String id) {
        if (CandidateWorkspaceState.UNCATEGORIZED.equals(id)) return "Uncategorized";
        return workspace.categories().stream()
                .filter(category -> category.id().equals(id))
                .map(CandidateWorkspaceState.Category::name)
                .findFirst()
                .orElse(id);
    }

    private static String normalizeFormat(String format) {
        return format == null || format.isBlank()
                ? "standard" : format.strip().toLowerCase(Locale.ROOT);
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.strip();
    }

    private static String number(Double value) {
        if (value == null || !Double.isFinite(value)) return "?";
        if (Math.rint(value) == value) return Long.toString(value.longValue());
        return Double.toString(value);
    }

    private static String pair(String left, String right) {
        if ((left == null || left.isBlank()) && (right == null || right.isBlank())) return "";
        return blankTo(left, "?") + "/" + blankTo(right, "?");
    }

    private static String list(List<String> values) {
        if (values == null || values.isEmpty()) return "-";
        StringJoiner joiner = new StringJoiner(",");
        for (String value : values) if (value != null && !value.isBlank()) joiner.add(escapeAtom(value));
        String result = joiner.toString();
        return result.isEmpty() ? "-" : result;
    }

    private static String escapeAtom(String value) {
        return value.replace("\\", "\\\\")
                .replace(",", "\\,")
                .replace("\r", " ")
                .replace("\n", " ");
    }

    private static String q(String value) {
        if (value == null) return "\"\"";
        String escaped = value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .replace("\n", "\\n");
        return "\"" + escaped + "\"";
    }
}
