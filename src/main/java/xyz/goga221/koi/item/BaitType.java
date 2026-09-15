package xyz.goga221.koi.item;

import xyz.goga221.koi.fishing.FishRarity;
import lombok.Getter;
import org.bukkit.Material;

import java.util.EnumSet;
import java.util.Set;

/**
 * Craftable bait that shifts a cast's rarity odds. Held in the off-hand, consumed one per cast.
 */
@Getter
public enum BaitType {

    WORM("Worm Bait", Material.STRING, 2001, EnumSet.of(FishRarity.COMMON, FishRarity.UNCOMMON), 1.4),
    SHRIMP("Shrimp Bait", Material.PRISMARINE_SHARD, 2002, EnumSet.of(FishRarity.RARE, FishRarity.EPIC), 1.7),
    GLOWING_LURE("Glowing Lure", Material.GLOW_INK_SAC, 2003, EnumSet.of(FishRarity.LEGENDARY, FishRarity.MYTHIC), 2.2);

    private final String displayName;
    private final Material material;
    private final int customModelData;
    private final Set<FishRarity> favoredRarities;
    private final double potency;

    BaitType(String displayName, Material material, int customModelData, Set<FishRarity> favoredRarities, double potency) {
        this.displayName = displayName;
        this.material = material;
        this.customModelData = customModelData;
        this.favoredRarities = favoredRarities;
        this.potency = potency;
    }
}
