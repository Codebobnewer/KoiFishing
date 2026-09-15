package xyz.goga221.koi.fishing;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Material;
import org.bukkit.Sound;

import java.util.HashSet;
import java.util.Set;

/**
 * A catchable entry - a fish, a piece of treasure, or junk (see {@link #category}). Mutable and
 * admin-editable at runtime via the fish editor menu, persisted to {@code fish-pool.yml}.
 */
@Getter
@Setter
public class FishSpecies {

    private String id;
    private String displayName;
    private FishRarity rarity;
    private LootCategory category = LootCategory.FISH;
    private Material material;
    private int customModelData;
    private double dropWeight;
    private Sound catchSound;

    /**
     * True for a vanilla treasure/junk item dropped as-is (a saddle, a name tag...). Entries
     * flagged this way are only rolled while {@code fishing.vanilla-loot-enabled} is on in
     * {@code config.yml} - filtered out at pool-load time otherwise.
     */
    private boolean vanillaLoot;

    private final Set<String> favoredBaitIds = new HashSet<>();
    private final Set<String> favoredBiomes = new HashSet<>();

    public FishSpecies(String id, String displayName, FishRarity rarity, Material material,
                        int customModelData, double dropWeight) {
        this.id = id;
        this.displayName = displayName;
        this.rarity = rarity;
        this.material = material;
        this.customModelData = customModelData;
        this.dropWeight = dropWeight;
    }
}
