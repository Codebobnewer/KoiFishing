package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishSpecies;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.gui.PagedGui;
import xyz.xenondevs.invui.gui.structure.Markers;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.ItemProvider;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.item.impl.controlitem.PageItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static xyz.goga221.koi.menu.MenuText.mmLore;
import static xyz.goga221.koi.menu.MenuText.mmName;

/**
 * Personal collection-book GUI opened by {@code /koidex}: every species in the pool, shown as
 * the real item for ones the player has caught before and as a "???" placeholder otherwise.
 * "Discovered" is derived from the {@code catches} table, not tracked separately.
 */
public class KoidexMenu {

    private final KoiPlugin plugin;

    public KoidexMenu(KoiPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player) {
        // SQLite read off the region thread, then hop back to the player's own thread to build
        // and open the GUI (required on Folia - InvUI windows are owned by the viewing player).
        plugin.getScheduler().runTaskAsynchronously(() -> {
            Set<String> discovered = plugin.getDatabaseManager().getCatchRepository().findDiscoveredFishIds(player.getUniqueId());
            plugin.getScheduler().runTask(player, () -> openWithDiscovered(player, discovered));
        });
    }

    private void openWithDiscovered(Player player, Set<String> discovered) {
        List<Item> items = new ArrayList<>();
        for (FishSpecies species : plugin.getFishManager().getPool().getSpecies()) {
            items.add(discovered.contains(species.getId()) ? discoveredItem(species) : undiscoveredItem());
        }

        Gui gui = PagedGui.items(builder -> builder
                .setStructure(
                        "# # # # # # # # #",
                        "# x x x x x x x #",
                        "# x x x x x x x #",
                        "# x x x x x x x #",
                        "# < # # # # # > #")
                .addIngredient('#', new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" ")))
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', new PageItem(false) {
                    @Override
                    public ItemProvider getItemProvider(PagedGui<?> gui) {
                        return new ItemBuilder(Material.ARROW).setDisplayName(mmName("<yellow>Previous Page"));
                    }
                })
                .addIngredient('>', new PageItem(true) {
                    @Override
                    public ItemProvider getItemProvider(PagedGui<?> gui) {
                        return new ItemBuilder(Material.ARROW).setDisplayName(mmName("<yellow>Next Page"));
                    }
                })
                .setContent(items));

        Window.single()
                .setTitle("Koi-dex")
                .setGui(gui)
                .open(player);
    }

    private Item discoveredItem(FishSpecies species) {
        // species.getDisplayName() is admin-set free text - placeholder, not straight into the
        // template (same reasoning as everywhere else it's rendered).
        ItemBuilder builder = new ItemBuilder(species.getMaterial())
                .setDisplayName(mmName("<white><name>", Placeholder.unparsed("name", species.getDisplayName())))
                .addLoreLines(mmLore(
                        "<gray>Rarity: <white>" + species.getRarity().name(),
                        "<gray>Category: <white>" + species.getCategory().name()
                ));
        if (species.getCustomModelData() > 0) {
            builder.setCustomModelData(species.getCustomModelData());
        }
        return new SimpleItem(builder);
    }

    private Item undiscoveredItem() {
        ItemBuilder builder = new ItemBuilder(Material.GRAY_DYE)
                .setDisplayName(mmName("<gray>???"))
                .addLoreLines(mmLore("<dark_gray>Not yet discovered"));
        return new SimpleItem(builder);
    }
}
