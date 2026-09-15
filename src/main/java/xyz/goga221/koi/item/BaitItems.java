package xyz.goga221.koi.item;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Locale;

/**
 * Builds and identifies {@link BaitType} {@link ItemStack}s via a {@code koi:bait_id} PDC tag.
 */
public final class BaitItems {

    private BaitItems() {
    }

    public static ItemStack create(BaitType type) {
        ItemStack item = new ItemStack(type.getMaterial());
        ItemMeta meta = item.getItemMeta();
        meta.setCustomModelData(type.getCustomModelData());
        meta.displayName(Component.text(type.getDisplayName(), NamedTextColor.GREEN)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(Component.text("Koi bait", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(KoiKeys.BAIT_ID, PersistentDataType.STRING, type.name());
        item.setItemMeta(meta);
        return item;
    }

    public static BaitType typeOf(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        String tag = item.getItemMeta().getPersistentDataContainer().get(KoiKeys.BAIT_ID, PersistentDataType.STRING);
        if (tag == null) {
            return null;
        }
        try {
            return BaitType.valueOf(tag);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void registerRecipes(JavaPlugin plugin) {
        for (BaitType type : BaitType.values()) {
            NamespacedKey key = new NamespacedKey(plugin, "bait_" + type.name().toLowerCase(Locale.ROOT));
            ShapedRecipe recipe = new ShapedRecipe(key, create(type));
            recipe.shape("MMM", "MFM", "MMM");
            recipe.setIngredient('M', type.getMaterial());
            recipe.setIngredient('F', org.bukkit.Material.NETHER_WART);
            Bukkit.addRecipe(recipe);
        }
    }
}
