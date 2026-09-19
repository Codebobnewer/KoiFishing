package xyz.goga221.koi.item;

import xyz.goga221.koi.KoiPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * Resolves admin-defined {@link RodTier}s as Vulcan items sharing their id - Vulcan owns the
 * actual ItemStack (appearance, PDC identity), Koi only tracks which tier a held rod represents
 * and the gameplay tuning in {@code rod-tiers.yml}.
 */
public final class RodItems {

    private RodItems() {
    }

    /**
     * @return the rolled Vulcan item, or {@code null} if the tier hasn't been authored in Vulcan yet
     */
    public static ItemStack create(RodTier tier) {
        return KoiPlugin.getVulcanApi().getItem(tier.getId());
    }

    /**
     * Resolves the tier of a held item via its Vulcan item id, or {@code null} if it isn't a
     * known Koi rod tier (a plain vanilla fishing rod, or an unrelated Vulcan item).
     */
    public static RodTier tierOf(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return null;
        }

        String vulcanId = KoiPlugin.getVulcanApi().getItemId(item);
        if (vulcanId == null) {
            return null;
        }
        return KoiPlugin.getRodTierPool().findTier(vulcanId).orElse(null);
    }

    /**
     * Registers a crafting recipe for every tier that declares an {@code upgrades-from} tier and
     * {@code upgrade-material} in {@code rod-tiers.yml}. Tiers with neither (e.g. a starting
     * tier handed out some other way) get no recipe.
     */
    public static void registerRecipes(JavaPlugin plugin) {
        for (RodTier tier : KoiPlugin.getRodTierPool().getTiers()) {
            if (tier.getUpgradesFromId() == null || tier.getUpgradeMaterial() == null) {
                continue;
            }

            RodTier from = KoiPlugin.getRodTierPool().findTier(tier.getUpgradesFromId()).orElse(null);
            ItemStack fromItem = from != null ? create(from) : null;
            ItemStack toItem = create(tier);
            if (fromItem == null || toItem == null) {
                plugin.getLogger().warning("Skipping " + tier.getId() + " rod upgrade recipe - author both "
                        + tier.getUpgradesFromId() + " and " + tier.getId() + " in Vulcan first (/v item create).");
                continue;
            }

            NamespacedKey key = new NamespacedKey(plugin, "rod_upgrade_" + tier.getId().toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, toItem);
            recipe.shape(" R ", " U ", "   ");
            recipe.setIngredient('R', new RecipeChoice.ExactChoice(fromItem));
            recipe.setIngredient('U', tier.getUpgradeMaterial());
            Bukkit.addRecipe(recipe);
        }
    }
}
