package xyz.goga221.koi.item;

import xyz.goga221.koi.KoiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Resolves admin-defined {@link BaitType}s as Vulcan items sharing their id - see
 * {@link RodItems} for the same pattern applied to rods.
 */
public final class BaitItems {

    private BaitItems() {
    }

    /**
     * @return the rolled Vulcan item, or {@code null} if the type hasn't been authored in Vulcan yet
     */
    public static ItemStack create(BaitType type) {
        return KoiPlugin.getVulcanApi().getItem(type.getId());
    }

    public static BaitType typeOf(ItemStack item) {
        if (item == null) {
            return null;
        }

        String vulcanId = KoiPlugin.getVulcanApi().getItemId(item);
        if (vulcanId == null) {
            return null;
        }
        return KoiPlugin.getBaitTypePool().findType(vulcanId).orElse(null);
    }

    /**
     * Registers a crafting recipe for every type that declares a {@code craft-material} in
     * {@code bait-types.yml}.
     */
    public static void registerRecipes(JavaPlugin plugin) {
        for (BaitType type : KoiPlugin.getBaitTypePool().getTypes()) {
            if (type.getCraftMaterial() == null) {
                continue;
            }

            ItemStack result = create(type);
            if (result == null) {
                plugin.getLogger().warning("Skipping " + type.getId() + " bait recipe - author "
                        + type.getId() + " in Vulcan first (/v item create).");
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, "bait_" + type.getId().toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("MMM", "MFM", "MMM");
            recipe.setIngredient('M', type.getCraftMaterial());
            recipe.setIngredient('F', Material.NETHER_WART);
            Bukkit.addRecipe(recipe);
        }
    }
}
