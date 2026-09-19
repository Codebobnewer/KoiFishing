package xyz.goga221.koi.fishing;

import xyz.goga221.koi.KoiPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the {@link ItemStack} a player receives for any Koi catch (a fish, a sea creature
 * kill, ...) as a Vulcan item sharing the catch's id - an admin must author it in Vulcan with
 * {@code /v item create <id>} before it's actually obtainable. Koi's own catch difficulty
 * rarity is appended as an extra lore line on top of whatever Vulcan already built.
 */
public final class FishItems {

    private FishItems() {
    }

    /**
     * Every catch of the same species produces an identical ItemStack (no per-catch variable
     * text in the lore), so they stack normally in inventories.
     *
     * @return the rolled Vulcan item with Koi's rarity lore appended, or {@code null} if no
     * Vulcan item with this species' id has been authored yet
     */
    public static ItemStack create(FishSpecies species) {
        return create(species.getId(), species.getRarity());
    }

    /**
     * @return the rolled Vulcan item for {@code vulcanId} with Koi's rarity lore appended, or
     * {@code null} if no Vulcan item with that id has been authored yet
     */
    public static ItemStack create(String vulcanId, FishRarity rarity) {
        ItemStack base = KoiPlugin.getVulcanApi().getItem(vulcanId);
        if (base == null) {
            return null;
        }

        ItemStack item = base.clone();
        ItemMeta meta = item.getItemMeta();
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("Rarity: " + rarity.name(), rarity.getColor())
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }
}
