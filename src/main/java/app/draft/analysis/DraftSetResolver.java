package app.draft.analysis;

import app.model.card.CardInfo;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DraftSetResolver {
    public Optional<String> infer(Collection<CardInfo> offeredCards) {
        if (offeredCards == null) return Optional.empty();
        return offeredCards.stream()
                .filter(Objects::nonNull)
                .map(CardInfo::getSet)
                .filter(code -> code != null && !code.isBlank())
                .map(String::toLowerCase)
                .collect(Collectors.groupingBy(
                        Function.identity(), Collectors.counting()))
                .entrySet().stream()
                .max(Comparator
                        .<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue)
                        .thenComparing(Map.Entry::getKey))
                .map(Map.Entry::getKey);
    }
}
