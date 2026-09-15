package xyz.goga221.koi.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Builds and identifies tiered fishing rod {@link ItemStack}s. Every tier reuses
 * {@link Material#FISHING_ROD} (vanilla only has one rod item) and is distinguished by
 * CustomModelData plus a {@code koi:rod_tier} PDC tag.
 */
public final class RodItems {

    private RodItems() {
    }

    public static ItemStack create(RodTier tier) {
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(tier.getCustomModelData());
        meta.displayName(Component.text(tier.getDisplayName(), NamedTextColor.AQUA)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Koi rod tier: " + tier.name(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(KoiKeys.ROD_TIER, PersistentDataType.STRING, tier.name());
        item.setItemMeta(meta);
        return item;
    }

    /**
     * Resolves the tier of a held item. A plain vanilla fishing rod (no PDC tag) is treated
     * as {@link RodTier#WOOD} so untagged rods still work at baseline difficulty.
     */
    public static RodTier tierOf(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD) {
            return null;
        }
        if (!item.hasItemMeta()) {
            return RodTier.WOOD;
        }

        String tag = item.getItemMeta().getPersistentDataContainer().get(KoiKeys.ROD_TIER, PersistentDataType.STRING);
        if (tag == null) {
            return RodTier.WOOD;
        }
        try {
            return RodTier.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return RodTier.WOOD;
        }
    }

    public static void registerRecipes(JavaPlugin plugin) {
        registerUpgrade(plugin, RodTier.WOOD, RodTier.IRON, Material.IRON_INGOT);
        registerUpgrade(plugin, RodTier.IRON, RodTier.DIAMOND, Material.DIAMOND);
        registerUpgrade(plugin, RodTier.DIAMOND, RodTier.KOI, Material.NAUTILUS_SHELL);
    }

    private static void registerUpgrade(JavaPlugin plugin, RodTier from, RodTier to, Material upgradeMaterial) {
        NamespacedKey key = new NamespacedKey(plugin, "rod_upgrade_" + to.name().toLowerCase(Locale.ROOT));
        ShapedRecipe recipe = new ShapedRecipe(key, create(to));
        recipe.shape(" R ", " U ", "   ");
        recipe.setIngredient('R', new RecipeChoice.ExactChoice(create(from)));
        recipe.setIngredient('U', upgradeMaterial);
        Bukkit.addRecipe(recipe);
    }
}
