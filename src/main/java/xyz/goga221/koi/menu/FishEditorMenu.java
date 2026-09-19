package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishPool;
import xyz.goga221.koi.fishing.FishSpecies;
import xyz.goga221.koi.fishing.LootCategory;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;

import static xyz.goga221.koi.menu.MenuText.mmLore;
import static xyz.goga221.koi.menu.MenuText.mmName;
import static xyz.goga221.koi.menu.MenuText.pagedListGui;
import static xyz.goga221.koi.menu.MenuText.vulcanIconOrBarrier;

/**
 * Admin GUI opened by {@code /koi config}: a paged list of every {@link FishSpecies} (fish,
 * treasure, and junk alike) with click-to-edit category/drop-weight/remove, plus a guided chat
 * wizard to add new species. Every edit is saved straight back to {@code fish-pool.yml} via
 * {@link xyz.goga221.koi.config.FishPoolConfig}. A species' actual ItemStack AND its rarity are
 * both owned by its Vulcan item (see {@link xyz.goga221.koi.fishing.FishItems}/
 * {@link FishSpecies#getRarity()}) - this menu only edits the Koi-side rolling data
 * (category/drop weight/biomes), so rarity is shown read-only; change it with
 * {@code /v item edit <id>} in Vulcan.
 */
public class FishEditorMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final ChatInputPrompt prompt = KoiPlugin.getChatInputPrompt();
    private int currentPage = 0;

    public void open(Player player) {
        open(player, currentPage);
    }

    private void open(Player player, int page) {
        currentPage = page;
        Window.single()
                .setTitle("Koi Fish Editor")
                .setGui(buildListGui(player, page))
                .open(player);
    }

    private Gui buildListGui(Player player, int page) {
        List<Item> items = new ArrayList<>();
        for (FishSpecies species : KoiPlugin.getFishManager().getPool().getSpecies()) {
            items.add(speciesItem(species));
        }

        return pagedListGui(items, addFishButton(), page, targetPage -> open(player, targetPage));
    }

    private Item addFishButton() {
        return new SimpleItem(new ItemBuilder(Material.EMERALD)
                .setDisplayName(mmName("<green>Add New Fish"))
                .addLoreLines(mmLore("<gray>Click, then enter the id of an", "<gray>already-authored Vulcan item")), MenuText.debounced(click -> {
            Player player = click.getPlayer();
            player.closeInventory();
            startAddWizard(player);
        }));
    }

    private Item speciesItem(FishSpecies species) {
        ItemBuilder builder = vulcanIconOrBarrier(species.getId());

        String whitelistedBiomes = species.getWhitelistedBiomes().isEmpty()
                ? "Any"
                : String.join(", ", species.getWhitelistedBiomes());

        builder.addLoreLines(mmLore(
                "<gray>Vulcan id: <white>" + species.getId(),
                "<gray>Rarity: <white>" + species.getRarity().name(),
                "<gray>Category: <white>" + species.getCategory().name(),
                "<gray>Drop weight: <white>" + species.getDropWeight(),
                "<gray>Biome whitelist: <white>" + whitelistedBiomes,
                "",
                "<yellow>Drop (Q): <gray>cycle category",
                "<yellow>Right-click: <gray>+0.5 drop weight",
                "<yellow>Shift-right-click: <gray>-0.5 drop weight",
                "<red>Shift-left-click: <gray>remove"
        ));

        return new SimpleItem(builder, MenuText.debounced(click -> {
            Player player = click.getPlayer();
            FishPool pool = KoiPlugin.getFishManager().getPool();

            switch (click.getClickType()) {
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
        }));
    }

    private LootCategory nextCategory(LootCategory current) {
        LootCategory[] values = LootCategory.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void persistAndRefresh(Player player) {
        MenuText.persistAndRefresh(player, () -> open(player, currentPage), () -> KoiPlugin.getFishPoolConfig().save(KoiPlugin.getFishManager().getPool()));
    }

    private void startAddWizard(Player player) {
        // See ChatInputPrompt.await for why every reply here goes through Placeholder.unparsed.
        prompt.await(player, "Enter the Vulcan item id for this fish (create it first with /v item create if needed):", id -> {
            if (KoiPlugin.getFishManager().getPool().findSpecies(id).isPresent()) {
                player.sendMessage(MM.deserialize("<red>A fish with id '<id>' already exists.</red>",
                        Placeholder.unparsed("id", id)));
                return;
            }
            if (!KoiPlugin.getVulcanApi().exists(id)) {
                player.sendMessage(MM.deserialize("<red>No Vulcan item '<id>' exists yet - run <white>/v item create <id></white> first.</red>",
                        Placeholder.unparsed("id", id)));
                return;
            }

            // Rarity comes from the Vulcan item itself (set it there with /v item edit <id>) -
            // Koi only needs the roll weight here, defaulted and adjustable via right/shift-right-click.
            FishSpecies species = new FishSpecies(id, 1.0);
            KoiPlugin.getFishManager().getPool().addSpecies(species);
            player.sendMessage(MM.deserialize("<green>Added fish '<id>'.</green>", Placeholder.unparsed("id", id)));
            persistAndRefresh(player);
        });
    }
}
