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
     * {@code bait-types.yml}. Safe to call again after {@code /koi reload} (removes any stale
     * recipe under the same key first, since {@code Bukkit.addRecipe} won't overwrite an
     * already-registered key) - must run on the global region thread, not off-thread with the
     * rest of a reload's YAML reads.
     */
    public static void registerRecipes(JavaPlugin plugin) {
        for (BaitType type : KoiPlugin.getBaitTypePool().getTypes()) {
            NamespacedKey key = new NamespacedKey(plugin, "bait_" + type.getId().toLowerCase(Locale.ROOT));
            Bukkit.removeRecipe(key);

            if (type.getCraftMaterial() == null) {
                continue;
            }

            ItemStack result = create(type);
            if (result == null) {
                plugin.getLogger().warning("Skipping " + type.getId() + " bait recipe - author "
                        + type.getId() + " in Vulcan first (/v item create).");
                continue;
            }

            ShapedRecipe recipe = new ShapedRecipe(key, result);
            recipe.shape("MMM", "MFM", "MMM");
            recipe.setIngredient('M', type.getCraftMaterial());
            recipe.setIngredient('F', Material.NETHER_WART);
            Bukkit.addRecipe(recipe);
        }
    }
}
