package xyz.goga221.koi.item;

import xyz.goga221.koi.KoiPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds every admin-defined {@link RodTier}. Backed by a {@link CopyOnWriteArrayList} for the
 * same reason as {@link xyz.goga221.koi.fishing.FishPool} - on Folia, an admin editing tiers runs
 * on their own region thread while other players are concurrently casting on theirs, and reads
 * (every cast) vastly outnumber writes (occasional admin edits).
 */
public class RodTierPool {

    private final List<RodTier> tiers = new CopyOnWriteArrayList<>();

    public List<RodTier> getTiers() {
        return tiers;
    }

    public void addTier(RodTier tier) {
        tiers.add(tier);
    }

    public boolean removeTier(String id) {
        return tiers.removeIf(tier -> tier.getId().equalsIgnoreCase(id));
    }

    public Optional<RodTier> findTier(String id) {
        return tiers.stream().filter(tier -> tier.getId().equalsIgnoreCase(id)).findFirst();
    }

    public void clear() {
        tiers.clear();
    }

    /**
     * Discards every tier and reloads them from {@code rod-tiers.yml}.
     */
    public void reload() {
        clear();
        KoiPlugin.getRodTierConfig().load(this);
    }
}
