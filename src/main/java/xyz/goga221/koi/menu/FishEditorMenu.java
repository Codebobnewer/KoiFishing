package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishPool;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.fishing.FishSpecies;
import xyz.goga221.koi.fishing.LootCategory;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
import java.util.Locale;

/**
 * Admin GUI opened by {@code /koi config}: a paged list of every {@link FishSpecies} (fish,
 * treasure, and junk alike) with click-to-edit rarity/category/drop-weight/remove, plus a
 * guided chat wizard to add new species. Every edit is saved straight back to
 * {@code fish-pool.yml} via {@link xyz.goga221.koi.config.FishPoolConfig}.
 */
public class FishEditorMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final KoiPlugin plugin;
    private final ChatInputPrompt prompt;

    public FishEditorMenu(KoiPlugin plugin) {
        this.plugin = plugin;
        this.prompt = plugin.getChatInputPrompt();
    }

    public void open(Player player) {
        Window.single()
                .setTitle("Koi Fish Editor")
                .setGui(buildListGui(player))
                .open(player);
    }

    private Gui buildListGui(Player player) {
        List<Item> items = new ArrayList<>();
        for (FishSpecies species : plugin.getFishManager().getPool().getSpecies()) {
            items.add(speciesItem(species));
        }

        return PagedGui.items(builder -> builder
                .setStructure(
                        "# # # # # # # # #",
                        "# x x x x x x x #",
                        "# x x x x x x x #",
                        "# x x x x x x x #",
                        "# < # a # # # > #")
                .addIngredient('#', new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" ")))
                .addIngredient('x', Markers.CONTENT_LIST_SLOT_HORIZONTAL)
                .addIngredient('<', new PageItem(false) {
                    @Override
                    public ItemProvider getItemProvider(PagedGui<?> gui) {
                        return new ItemBuilder(Material.ARROW).setDisplayName("§ePrevious Page");
                    }
                })
                .addIngredient('>', new PageItem(true) {
                    @Override
                    public ItemProvider getItemProvider(PagedGui<?> gui) {
                        return new ItemBuilder(Material.ARROW).setDisplayName("§eNext Page");
                    }
                })
                .addIngredient('a', addFishButton(player))
                .setContent(items));
    }

    private Item addFishButton(Player owner) {
        return new SimpleItem(new ItemBuilder(Material.EMERALD)
                .setDisplayName("§aAdd New Fish")
                .addLoreLines("§7Hold the item to use, then click"), click -> {
            Player player = click.getPlayer();
            ItemStack held = player.getInventory().getItemInMainHand();
            if (held.getType().isAir()) {
                player.sendMessage(MM.deserialize("<red>Hold the item you want to use for the new fish first.</red>"));
                return;
            }

            Material material = held.getType();
            int customModelData = held.hasItemMeta() && held.getItemMeta().hasCustomModelData()
                    ? held.getItemMeta().getCustomModelData() : 0;

            player.closeInventory();
            startAddWizard(player, material, customModelData);
        });
    }

    private Item speciesItem(FishSpecies species) {
        ItemBuilder builder = new ItemBuilder(species.getMaterial())
                .setDisplayName("§f" + species.getDisplayName())
                .addLoreLines(
                        "§7Rarity: §f" + species.getRarity().name(),
                        "§7Category: §f" + species.getCategory().name(),
                        "§7Drop weight: §f" + species.getDropWeight(),
                        "",
                        "§eLeft-click: §7cycle rarity",
                        "§eDrop (Q): §7cycle category",
                        "§eRight-click: §7+0.5 drop weight",
                        "§eShift-right-click: §7-0.5 drop weight",
                        "§cShift-left-click: §7remove"
                );

        return new SimpleItem(builder, click -> {
            Player player = click.getPlayer();
            FishPool pool = plugin.getFishManager().getPool();

            switch (click.getClickType()) {
                case LEFT -> {
                    species.setRarity(nextRarity(species.getRarity()));
                    persistAndRefresh(player);
                }
                case DROP -> {
                    species.setCategory(nextCategory(species.getCategory()));
                    persistAndRefresh(player);
                }
                case RIGHT -> {
                    species.setDropWeight(species.getDropWeight() + 0.5);
                    persistAndRefresh(player);
                }
                case SHIFT_RIGHT -> {
                    species.setDropWeight(Math.max(0.1, species.getDropWeight() - 0.5));
                    persistAndRefresh(player);
                }
                case SHIFT_LEFT -> {
                    pool.removeSpecies(species.getId());
                    persistAndRefresh(player);
                }
                default -> {
                }
            }
        });
    }

    private FishRarity nextRarity(FishRarity current) {
        FishRarity[] values = FishRarity.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private LootCategory nextCategory(LootCategory current) {
        LootCategory[] values = LootCategory.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void persistAndRefresh(Player player) {
        // The in-memory pool is already mutated, so refresh the GUI immediately - the disk
        // write doesn't need to block that, and file I/O shouldn't run on the region thread.
        open(player);
        plugin.getScheduler().runTaskAsynchronously(() -> plugin.getFishPoolConfig().save(plugin.getFishManager().getPool()));
    }

    private void startAddWizard(Player player, Material material, int customModelData) {
        prompt.await(player, "Enter a unique fish id (letters/numbers/underscore):", id -> {
            if (plugin.getFishManager().getPool().findSpecies(id).isPresent()) {
                player.sendMessage(MM.deserialize("<red>A fish with id '" + id + "' already exists.</red>"));
                return;
            }
            prompt.await(player, "Enter a display name:", displayName ->
                    promptRarity(player, id, displayName, material, customModelData));
        });
    }

    private void promptRarity(Player player, String id, String displayName, Material material, int customModelData) {
        prompt.await(player, "Enter a rarity (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC):", rarityName -> {
            FishRarity rarity;
            try {
                rarity = FishRarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                player.sendMessage(MM.deserialize("<red>Unknown rarity: " + rarityName + "</red>"));
                return;
            }

            FishSpecies species = new FishSpecies(id, displayName, rarity, material, customModelData, 1.0);
            plugin.getFishManager().getPool().addSpecies(species);
            player.sendMessage(MM.deserialize("<green>Added fish '" + displayName + "'.</green>"));
            open(player);
            plugin.getScheduler().runTaskAsynchronously(() -> plugin.getFishPoolConfig().save(plugin.getFishManager().getPool()));
        });
    }
}
