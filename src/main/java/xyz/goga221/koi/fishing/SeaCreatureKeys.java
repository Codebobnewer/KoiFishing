package xyz.goga221.koi.fishing;

import xyz.goga221.koi.item.RodTier;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Tags a spawned {@link SeaCreature} encounter onto its {@link LivingEntity} via PDC, so
 * {@link xyz.goga221.koi.listener.SeaCreatureListener} can recognize it - and recover the rod
 * tier/bait that was in play when it bit - on death. See {@link FishManager#spawnSeaCreature}.
 */
public final class SeaCreatureKeys {

    private static final NamespacedKey ID = new NamespacedKey("koi", "sea_creature_id");
    private static final NamespacedKey ROD_TIER_ID = new NamespacedKey("koi", "sea_creature_rod_tier_id");
    private static final NamespacedKey BAIT_ID = new NamespacedKey("koi", "sea_creature_bait_id");

    private SeaCreatureKeys() {
    }

    public static void tag(LivingEntity entity, SeaCreature creature, RodTier rodTier, String baitId) {
        PersistentDataContainer container = entity.getPersistentDataContainer();
        container.set(ID, PersistentDataType.STRING, creature.getId());
        if (rodTier != null) {
            container.set(ROD_TIER_ID, PersistentDataType.STRING, rodTier.getId());
        }
        if (baitId != null) {
            container.set(BAIT_ID, PersistentDataType.STRING, baitId);
        }
    }

    public static String idOf(Entity entity) {
        return entity.getPersistentDataContainer().get(ID, PersistentDataType.STRING);
    }

    public static String rodTierIdOf(Entity entity) {
        return entity.getPersistentDataContainer().get(ROD_TIER_ID, PersistentDataType.STRING);
    }

    public static String baitIdOf(Entity entity) {
        return entity.getPersistentDataContainer().get(BAIT_ID, PersistentDataType.STRING);
    }
}
