package xyz.goga221.koi.fishing;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.util.WeightedRoll;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds every admin-defined {@link SeaCreature} and resolves a weighted spawn roll - see
 * {@link xyz.goga221.koi.fishing.FishPool} for the same weighted-selection pattern applied to
 * fish species. Backed by a {@link CopyOnWriteArrayList} for the same Folia-safety reason.
 */
public class SeaCreaturePool {

    private final List<SeaCreature> creatures = new CopyOnWriteArrayList<>();

    public List<SeaCreature> getCreatures() {
        return creatures;
    }

    public void addCreature(SeaCreature creature) {
        creatures.add(creature);
    }

    public boolean removeCreature(String id) {
        return creatures.removeIf(creature -> creature.getId().equalsIgnoreCase(id));
    }

    public Optional<SeaCreature> findCreature(String id) {
        return creatures.stream().filter(creature -> creature.getId().equalsIgnoreCase(id)).findFirst();
    }

    public void clear() {
        creatures.clear();
    }

    /**
     * Discards every creature and reloads them from {@code sea-creatures.yml}.
     */
    public void reload() {
        clear();
        KoiPlugin.getSeaCreatureConfig().load(this);
    }

    /**
     * Rolls one creature weighted by {@link SeaCreature#getSpawnWeight()}. Only considers
     * creatures whose {@link SeaCreature#getTycheMobId()} actually resolves to a real Tyche mob
     * right now - a roll can never come back unspawnable, so callers don't need their own
     * "does this actually exist" fallback.
     */
    public Optional<SeaCreature> roll() {
        List<SeaCreature> spawnable = creatures.stream()
                .filter(creature -> TycheIntegration.mobExists(creature.getTycheMobId()))
                .toList();
        return WeightedRoll.roll(spawnable, SeaCreature::getSpawnWeight);
    }
}
