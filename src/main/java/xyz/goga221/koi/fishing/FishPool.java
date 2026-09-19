package xyz.goga221.koi.fishing;

import xyz.goga221.koi.item.BaitType;
import xyz.goga221.koi.item.RodTier;
import xyz.goga221.koi.util.WeightedRoll;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

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
        return WeightedRoll.roll(matches, FishSpecies::getDropWeight);
    }

    public Optional<FishSpecies> roll(BaitType bait, String biomeKey, RodTier rodTier) {
        return WeightedRoll.roll(species, fish -> weightOf(fish, bait, biomeKey, rodTier));
    }

    private double weightOf(FishSpecies fish, BaitType bait, String biomeKey, RodTier rodTier) {
        Set<String> whitelistedBiomes = fish.getWhitelistedBiomes();
        if (!whitelistedBiomes.isEmpty() && (biomeKey == null || !whitelistedBiomes.contains(biomeKey))) {
            return 0.0;
        }

        FishRarity rarity = fish.getRarity();
        double weight = rarity.getPoolWeight() * fish.getDropWeight();

        if (bait != null && bait.getFavoredRarities().contains(rarity)) {
            weight *= bait.getPotency();
        }
        if (biomeKey != null && fish.getFavoredBiomes().contains(biomeKey)) {
            weight *= 1.5;
        }
        if (rodTier != null) {
            weight *= 1.0 + rodTier.getRarityBoost() * rarity.ordinal();
        }

        return weight;
    }
}
