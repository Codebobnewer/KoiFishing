package xyz.goga221.koi.fishing;

import xyz.goga221.koi.item.KoiKeys;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

/**
 * Builds the {@link ItemStack} a player receives for a successful catch. Custom fish beyond
 * vanilla's cod/salmon/pufferfish/tropical fish reuse those materials with distinct
 * CustomModelData values - real resource-pack textures are a separate art task.
 */
public final class FishItems {

    private FishItems() {
    }

    /**
     * Every catch of the same species produces an identical ItemStack (no per-catch variable
     * text in the lore), so they stack normally in inventories.
     */
    public static ItemStack create(FishSpecies species) {
        ItemStack item = new ItemStack(species.getMaterial());
        ItemMeta meta = item.getItemMeta();
        if (species.getCustomModelData() > 0) {
            meta.setCustomModelData(species.getCustomModelData());
        }
        meta.displayName(Component.text(species.getDisplayName(), species.getRarity().getColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
                Component.text("Rarity: " + species.getRarity().name(), species.getRarity().getColor())
                        .decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(KoiKeys.FISH_ID, PersistentDataType.STRING, species.getId());
        item.setItemMeta(meta);
        return item;
    }
}
