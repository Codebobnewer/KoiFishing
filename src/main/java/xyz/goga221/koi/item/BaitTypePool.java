package xyz.goga221.koi.item;

import xyz.goga221.koi.KoiPlugin;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Holds every admin-defined {@link BaitType} - see {@link RodTierPool} for the same pattern
 * applied to rod tiers.
 */
public class BaitTypePool {

    private final List<BaitType> types = new CopyOnWriteArrayList<>();

    public List<BaitType> getTypes() {
        return types;
    }

    public void addType(BaitType type) {
        types.add(type);
    }

    public boolean removeType(String id) {
        return types.removeIf(type -> type.getId().equalsIgnoreCase(id));
    }

    public Optional<BaitType> findType(String id) {
        return types.stream().filter(type -> type.getId().equalsIgnoreCase(id)).findFirst();
    }

    public void clear() {
        types.clear();
    }

    /**
     * Discards every type and reloads them from {@code bait-types.yml}.
     */
    public void reload() {
        clear();
        KoiPlugin.getBaitTypeConfig().load(this);
    }
}
