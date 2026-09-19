package xyz.goga221.koi.util;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.ToDoubleFunction;

/**
 * Shared cumulative-weight random selection: P(item) = weight(item) / sum(weight(all)). Used
 * everywhere Koi rolls one entry out of a weighted list - fish species, sea creature spawns,
 * rarity-scoped test rolls.
 */
public final class WeightedRoll {

    private WeightedRoll() {
    }

    public static <T> Optional<T> roll(List<T> items, ToDoubleFunction<T> weightFn) {
        if (items.isEmpty()) {
            return Optional.empty();
        }

        // weightFn is computed exactly once per item (not once for the sum and again for the
        // walk below) - it may do real work per call (e.g. an external item-registry lookup).
        double[] weights = new double[items.size()];
        double totalWeight = 0.0;
        for (int i = 0; i < items.size(); i++) {
            weights[i] = weightFn.applyAsDouble(items.get(i));
            totalWeight += weights[i];
        }
        if (totalWeight <= 0.0) {
            return Optional.empty();
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0;
        for (int i = 0; i < items.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return Optional.of(items.get(i));
            }
        }
        return Optional.of(items.get(items.size() - 1));
    }
}
