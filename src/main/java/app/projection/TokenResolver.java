package app.projection;

import app.model.card.CardInfo;
import app.model.card.CardRelatedPart;
import app.model.game.GameObjectState;

import java.util.Map;

/**
 * Resolves Arena-observed token objects to enriched related-card metadata when
 * the source card identifies one unambiguous token candidate.
 *
 * <p>This collaborator performs token matching only. It does not infer zone
 * changes, create game events, or mutate canonical game state.</p>
 */
final class TokenResolver {
    CardInfo resolve(GameObjectState token,
                     Map<Long, CardInfo> knownCards,
                     Map<String, CardInfo> knownRelatedCards) {
        CardInfo source = knownCards.get(token.getObjectSourceGrpId());
        if (source == null || source.getAllParts() == null) {
            return null;
        }

        CardInfo best = null;
        int bestScore = Integer.MIN_VALUE;
        int secondBestScore = Integer.MIN_VALUE;

        for (CardRelatedPart part : source.getAllParts()) {
            if (part == null || !"token".equalsIgnoreCase(part.getComponent())) {
                continue;
            }

            CardInfo candidate = knownRelatedCards.get(part.getId());
            int score = candidate == null
                    ? scoreRelatedName(token, part)
                    : scoreToken(token, candidate);

            if (score > bestScore) {
                secondBestScore = bestScore;
                bestScore = score;
                best = candidate != null ? candidate : syntheticRelated(part);
            } else if (score > secondBestScore) {
                secondBestScore = score;
            }
        }

        boolean hasNoRunnerUp = secondBestScore == Integer.MIN_VALUE;
        boolean isClearlyBetter = hasNoRunnerUp || bestScore - secondBestScore >= 5;
        if (best != null && bestScore >= 20 && isClearlyBetter) {
            return best;
        }
        return null;
    }

    String descriptiveName(GameObjectState token) {
        StringBuilder text = new StringBuilder();
        if (token.getPower() != null && token.getToughness() != null) {
            text.append(token.getPower()).append('/').append(token.getToughness()).append(' ');
        }
        if (!token.getColors().isEmpty()) {
            text.append(String.join("/", token.getColors()).toLowerCase()).append(' ');
        }
        if (!token.getSubtypes().isEmpty()) {
            text.append(String.join(" ", token.getSubtypes())).append(' ');
        }
        return text.append("token").toString();
    }

    private int scoreRelatedName(GameObjectState token, CardRelatedPart part) {
        String haystack = ((part.getName() == null ? "" : part.getName()) + " "
                + (part.getTypeLine() == null ? "" : part.getTypeLine())).toLowerCase();

        int score = 5;
        for (String subtype : token.getSubtypes()) {
            if (haystack.contains(subtype.toLowerCase())) {
                score += 12;
            }
        }
        if (token.getPower() != null
                && token.getToughness() != null
                && haystack.contains(token.getPower() + "/" + token.getToughness())) {
            score += 20;
        }
        return score;
    }

    private int scoreToken(GameObjectState token, CardInfo candidate) {
        int score = 10;
        String typeLine = candidate.effectiveTypeLine() == null
                ? ""
                : candidate.effectiveTypeLine().toLowerCase();

        for (String subtype : token.getSubtypes()) {
            if (typeLine.contains(subtype.toLowerCase())) {
                score += 15;
            }
        }
        if (token.getPower() != null
                && String.valueOf(token.getPower()).equals(candidate.getPower())) {
            score += 12;
        }
        if (token.getToughness() != null
                && String.valueOf(token.getToughness()).equals(candidate.getToughness())) {
            score += 12;
        }
        if (candidate.getColors() != null
                && !candidate.getColors().isEmpty()
                && token.getColors().containsAll(candidate.getColors())) {
            score += 8;
        }
        return score;
    }

    private CardInfo syntheticRelated(CardRelatedPart part) {
        CardInfo card = new CardInfo();
        card.setId(part.getId());
        card.setName(part.getName());
        card.setTypeLine(part.getTypeLine());
        return card;
    }
}
