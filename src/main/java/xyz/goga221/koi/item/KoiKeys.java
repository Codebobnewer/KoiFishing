package xyz.goga221.koi.item;

import org.bukkit.NamespacedKey;

/**
 * Shared {@link NamespacedKey} constants for tagging Koi items via PersistentDataContainer.
 */
public final class KoiKeys {

    public static final NamespacedKey ROD_TIER = new NamespacedKey("koi", "rod_tier");
    public static final NamespacedKey BAIT_ID = new NamespacedKey("koi", "bait_id");
    public static final NamespacedKey FISH_ID = new NamespacedKey("koi", "fish_id");

    private KoiKeys() {
    }
}
