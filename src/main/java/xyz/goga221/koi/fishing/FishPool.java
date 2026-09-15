package xyz.goga221.koi.fishing;

import xyz.goga221.koi.item.BaitType;
import xyz.goga221.koi.item.RodTier;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Holds all known {@link FishSpecies} and resolves weighted catch rolls, factoring in rarity,
 * bait, and biome. Backed by a {@link CopyOnWriteArrayList} - on Folia, an admin editing the
 * pool (fish editor GUI, {@code /koi reload}) runs on their own region thread while other
 * players are concurrently rolling catches on theirs, so plain {@code ArrayList} isn't safe
 * here. Reads (every catch) vastly outnumber writes (occasional admin edits), which is exactly
 * what copy-on-write is for.
 */
public class FishPool {

    private final List<FishSpecies> species = new CopyOnWriteArrayList<>();

    public List<FishSpecies> getSpecies() {
        return species;
    }

    public void addSpecies(FishSpecies fish) {
        species.add(fish);
    }

    public boolean removeSpecies(String id) {
        return species.removeIf(fish -> fish.getId().equalsIgnoreCase(id));
    }

    public Optional<FishSpecies> findSpecies(String id) {
        return species.stream().filter(fish -> fish.getId().equalsIgnoreCase(id)).findFirst();
    }

    public void clear() {
        species.clear();
    }

    public boolean hasSpecies(FishRarity rarity) {
        return species.stream().anyMatch(fish -> fish.getRarity() == rarity);
    }

    /**
     * Rolls among only the species of a given rarity, weighted by drop weight. Used by the
     * {@code /koi test tier} debug command to force a specific rarity's next catch.
     */
    public Optional<FishSpecies> rollWithinRarity(FishRarity rarity) {
        List<FishSpecies> matches = species.stream().filter(fish -> fish.getRarity() == rarity).toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }

        double totalWeight = matches.stream().mapToDouble(FishSpecies::getDropWeight).sum();
        if (totalWeight <= 0.0) {
            return Optional.of(matches.get(ThreadLocalRandom.current().nextInt(matches.size())));
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0;
        for (FishSpecies fish : matches) {
            cumulative += fish.getDropWeight();
            if (roll < cumulative) {
                return Optional.of(fish);
            }
        }
        return Optional.of(matches.get(matches.size() - 1));
    }

    public Optional<FishSpecies> roll(BaitType bait, String biomeKey, RodTier rodTier) {
        if (species.isEmpty()) {
            return Optional.empty();
        }

        double[] weights = new double[species.size()];
        double totalWeight = 0.0;

        for (int i = 0; i < species.size(); i++) {
            FishSpecies fish = species.get(i);
            double weight = fish.getRarity().getPoolWeight() * fish.getDropWeight();

            if (bait != null && bait.getFavoredRarities().contains(fish.getRarity())) {
                weight *= bait.getPotency();
            }
            if (biomeKey != null && fish.getFavoredBiomes().contains(biomeKey)) {
                weight *= 1.5;
            }
            if (rodTier != null) {
                weight *= 1.0 + rodTier.getRarityBoost() * fish.getRarity().ordinal();
            }

            weights[i] = weight;
            totalWeight += weight;
        }

        if (totalWeight <= 0.0) {
            return Optional.empty();
        }

        double roll = ThreadLocalRandom.current().nextDouble(totalWeight);
        double cumulative = 0.0;
        for (int i = 0; i < species.size(); i++) {
            cumulative += weights[i];
            if (roll < cumulative) {
                return Optional.of(species.get(i));
            }
        }
        return Optional.of(species.get(species.size() - 1));
    }
}
