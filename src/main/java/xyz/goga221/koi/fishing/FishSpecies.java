package xyz.goga221.koi.fishing;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Sound;

import java.util.HashSet;
import java.util.Set;

/**
 * A catchable entry - a fish, a piece of treasure, or junk (see {@link #category}). The actual
 * ItemStack is a Vulcan item sharing this {@link #id} (see {@link FishItems}) - an admin must
 * author it in Vulcan with {@code /v item create <id>} before it's eligible to be caught. Vulcan
 * owns both the item's appearance AND its rarity ({@link #getRarity()} is derived from the
 * Vulcan item definition, not stored here) - this class only owns the data that drives the catch
 * roll and the reel minigame. Mutable and admin-editable at runtime via the fish editor menu,
 * persisted to {@code fish-pool.yml}.
 */
@Getter
@Setter
public class FishSpecies implements Catchable {

    private String id;
    private LootCategory category = LootCategory.FISH;
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

    /**
     * If non-empty, this species can ONLY be caught while standing in one of these whitelisted
     * biomes - excluded from the roll entirely everywhere else. Empty means no whitelist
     * (catchable anywhere, still subject to {@link #favoredBiomes}' soft weight boost). Biome
     * keys match {@link org.bukkit.block.Biome#getKey()}, e.g. {@code minecraft:river} for a
     * vanilla biome or {@code jeracraft:mist_garden} for a BiomeMaker custom biome - BiomeMaker
     * registers its biomes into the server's real biome registry and applies them to actual
     * world chunks via WorldEdit, so they resolve through the same vanilla Biome API and need no
     * special handling here.
     */
    private final Set<String> whitelistedBiomes = new HashSet<>();

    public FishSpecies(String id, double dropWeight) {
        this.id = id;
        this.dropWeight = dropWeight;
    }

    public FishRarity getRarity() {
        return FishRarity.fromVulcanItem(id);
    }
}
