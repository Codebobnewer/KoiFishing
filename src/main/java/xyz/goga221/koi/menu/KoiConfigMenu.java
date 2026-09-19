package xyz.goga221.koi.menu;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import static xyz.goga221.koi.menu.MenuText.mmLore;
import static xyz.goga221.koi.menu.MenuText.mmName;

/**
 * Admin hub GUI opened by {@code /koi config} - links to the fish species editor
 * ({@link FishEditorMenu}), the rod tier editor ({@link RodTierEditorMenu}), and the sea
 * creature editor ({@link SeaCreatureEditorMenu}), the three Koi-side catalogs that pair with
 * Vulcan-authored items.
 */
public class KoiConfigMenu {

    public void open(Player player) {
        Gui gui = Gui.normal(builder -> builder
                .setStructure(
                        "# # # # # # # # #",
                        "# # f # r # s # #",
                        "# # # # # # # # #")
                .addIngredient('#', new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" ")))
                .addIngredient('f', fishButton())
                .addIngredient('r', rodTierButton())
                .addIngredient('s', seaCreatureButton()));

        Window.single()
                .setTitle("Koi Config")
                .setGui(gui)
                .open(player);
    }

    private Item fishButton() {
        return new SimpleItem(new ItemBuilder(Material.TROPICAL_FISH)
                .setDisplayName(mmName("<aqua>Fish Species"))
                .addLoreLines(mmLore("<gray>Edit the catchable fish pool")),
                MenuText.debounced(click -> new FishEditorMenu().open(click.getPlayer())));
    }

    private Item rodTierButton() {
        return new SimpleItem(new ItemBuilder(Material.FISHING_ROD)
                .setDisplayName(mmName("<aqua>Rod Tiers"))
                .addLoreLines(mmLore("<gray>Edit fishing rod tiers")),
                MenuText.debounced(click -> new RodTierEditorMenu().open(click.getPlayer())));
    }

    private Item seaCreatureButton() {
        return new SimpleItem(new ItemBuilder(Material.TRIDENT)
                .setDisplayName(mmName("<aqua>Sea Creatures"))
                .addLoreLines(mmLore("<gray>Edit rare-bite sea creature encounters")),
                MenuText.debounced(click -> new SeaCreatureEditorMenu().open(click.getPlayer())));
    }
}
