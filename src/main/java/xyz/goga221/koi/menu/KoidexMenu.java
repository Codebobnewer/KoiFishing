package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishSpecies;
import xyz.goga221.koi.fishing.SeaCreature;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static xyz.goga221.koi.menu.MenuText.mmLore;
import static xyz.goga221.koi.menu.MenuText.mmName;

/**
 * Personal collection-book GUI opened by {@code /koidex}: a Fish tab and a Sea Creatures tab,
 * each showing every entry in its pool as the real item for ones the player has caught before
 * and as a "???" placeholder otherwise. "Discovered" is derived from the {@code catches} table
 * (fish and sea creature catches share it, keyed by id), not tracked separately - both tabs read
 * the same discovered-id set fetched once in {@link #open}, so switching tabs never re-queries.
 */
public class KoidexMenu {

    private static final int GUI_WIDTH = 9;
    private static final int GUI_HEIGHT = 5;
    private static final int[] CONTENT_SLOTS = buildContentSlots();

    private static int[] buildContentSlots() {
        // rows 1-3, columns 1-7 - 21 slots/page, matching the old "x x x x x x x" x3 PagedGui structure.
        int[] slots = new int[21];
        int i = 0;
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * GUI_WIDTH + col;
            }
        }
        return slots;
    }

    private enum Tab {
        FISH, SEA_CREATURE
    }

    public void open(Player player) {
        // SQLite read off the region thread, then hop back to the player's own thread to build
        // and open the GUI (required on Folia - InvUI windows are owned by the viewing player).
        KoiPlugin.getScheduler().runTaskAsynchronously(() -> {
            Set<String> discovered = KoiPlugin.getDatabaseManager().getCatchRepository().findDiscoveredFishIds(player.getUniqueId());
            KoiPlugin.getScheduler().runTask(player, () -> openTab(player, Tab.FISH, discovered, 0));
        });
    }

    /**
     * Built as a plain {@link Gui#empty} grid with items placed directly via {@code setItem},
     * rather than InvUI's {@code PagedGui} - see {@link MenuText#pagedListGui} for why (the same
     * NUMBER_KEY/hotbar-swap item duplication bug applied here too, since this used the same
     * PagedGui content-slot mechanism).
     */
    private void openTab(Player player, Tab tab, Set<String> discovered, int page) {
        List<Item> items = new ArrayList<>();
        if (tab == Tab.FISH) {
            for (FishSpecies species : KoiPlugin.getFishManager().getPool().getSpecies()) {
                items.add(discovered.contains(species.getId()) ? discoveredFishItem(species) : undiscoveredItem());
            }
        } else {
            for (SeaCreature creature : KoiPlugin.getSeaCreaturePool().getCreatures()) {
                items.add(discovered.contains(creature.getId()) ? discoveredSeaCreatureItem(creature) : undiscoveredItem());
            }
        }

        int pageSize = CONTENT_SLOTS.length;
        int pageAmount = Math.max(1, (items.size() + pageSize - 1) / pageSize);
        int clampedPage = Math.max(0, Math.min(page, pageAmount - 1));

        Item filler = filler();
        Gui gui = Gui.empty(GUI_WIDTH, GUI_HEIGHT);
        for (int slot = 0; slot < GUI_WIDTH * GUI_HEIGHT; slot++) {
            gui.setItem(slot, filler);
        }

        int start = clampedPage * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int index = start + i;
            if (index < items.size()) {
                gui.setItem(CONTENT_SLOTS[i], items.get(index));
            }
        }

        boolean hasPrevious = clampedPage > 0;
        boolean hasNext = clampedPage < pageAmount - 1;
        gui.setItem(37, hasPrevious ? navButton("<yellow>Previous Page", clampedPage - 1, player, tab, discovered) : filler);
        gui.setItem(39, tabButton(tab, discovered));
        gui.setItem(43, hasNext ? navButton("<yellow>Next Page", clampedPage + 1, player, tab, discovered) : filler);

        Window.single()
                .setTitle(tab == Tab.FISH ? "Koi-dex - Fish" : "Koi-dex - Sea Creatures")
                .setGui(gui)
                .open(player);
    }

    private Item filler() {
        return new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" "));
    }

    private Item navButton(String name, int targetPage, Player player, Tab tab, Set<String> discovered) {
        return new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName(mmName(name)),
                MenuText.debounced(click -> openTab(player, tab, discovered, targetPage)));
    }

    private Item tabButton(Tab current, Set<String> discovered) {
        Tab other = current == Tab.FISH ? Tab.SEA_CREATURE : Tab.FISH;
        Material icon = other == Tab.FISH ? Material.TROPICAL_FISH : Material.TRIDENT;
        String label = other == Tab.FISH ? "<aqua>View Fish" : "<aqua>View Sea Creatures";
        return new SimpleItem(new ItemBuilder(icon).setDisplayName(mmName(label)),
                MenuText.debounced(click -> openTab(click.getPlayer(), other, discovered, 0)));
    }

    private Item discoveredFishItem(FishSpecies species) {
        ItemStack vulcanItem = KoiPlugin.getVulcanApi().getItem(species.getId());
        if (vulcanItem == null) {
            return undiscoveredItem();
        }

        ItemBuilder builder = new ItemBuilder(vulcanItem.clone())
                .addLoreLines(mmLore(
                        "<gray>Rarity: <white>" + species.getRarity().name(),
                        "<gray>Category: <white>" + species.getCategory().name()
                ));
        return new SimpleItem(builder);
    }

    private Item discoveredSeaCreatureItem(SeaCreature creature) {
        ItemBuilder builder = MenuText.seaCreatureIcon(creature)
                .setDisplayName(mmName("<aqua>" + creature.getId()))
                .addLoreLines(mmLore("<gray>Rarity: <white>" + creature.getRarity().name()));
        return new SimpleItem(builder);
    }

    private Item undiscoveredItem() {
        ItemBuilder builder = new ItemBuilder(Material.GRAY_DYE)
                .setDisplayName(mmName("<gray>???"))
                .addLoreLines(mmLore("<dark_gray>Not yet discovered"));
        return new SimpleItem(builder);
    }
}
