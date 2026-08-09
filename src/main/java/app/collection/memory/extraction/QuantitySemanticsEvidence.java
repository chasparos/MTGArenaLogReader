package app.collection.memory.extraction;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Pure diagnostic summary used to test whether a structural card map behaves like ownership. */
public record QuantitySemanticsEvidence(
        int entries,
        long totalCopies,
        int oneCopy,
        int twoCopies,
        int threeCopies,
        int fourCopies,
        int fiveToTwentyCopies,
        int aboveTwentyCopies,
        int maximumCopies,
        List<Map.Entry<Long, Integer>> highestQuantities) {

    public QuantitySemanticsEvidence {
        highestQuantities = List.copyOf(highestQuantities);
    }

    public static QuantitySemanticsEvidence summarize(Map<Long, Integer> copies) {
        int one = 0, two = 0, three = 0, four = 0, fiveToTwenty = 0, aboveTwenty = 0, maximum = 0;
        long total = 0;
        for (int quantity : copies.values()) {
            total += quantity;
            maximum = Math.max(maximum, quantity);
            switch (quantity) {
                case 1 -> one++;
                case 2 -> two++;
                case 3 -> three++;
                case 4 -> four++;
                default -> {
                    if (quantity <= 20) fiveToTwenty++;
                    else aboveTwenty++;
                }
            }
        }
        List<Map.Entry<Long, Integer>> highest = copies.entrySet().stream()
                .sorted(Map.Entry.<Long, Integer>comparingByValue(Comparator.reverseOrder())
                        .thenComparing(Map.Entry.comparingByKey()))
                .limit(12).map(entry -> Map.entry(entry.getKey(), entry.getValue())).toList();
        return new QuantitySemanticsEvidence(copies.size(), total, one, two, three, four,
                fiveToTwenty, aboveTwenty, maximum, highest);
    }
}
