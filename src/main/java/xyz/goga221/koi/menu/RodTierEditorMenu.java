package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.item.RodTier;
import xyz.goga221.koi.item.RodTierPool;
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
 * Admin GUI opened by {@code /koi rodtiers}: a paged list of every {@link RodTier} with
 * click-to-edit rarity-boost/hook-speed/remove, plus a guided chat wizard to add new tiers.
 * Every edit is saved straight back to {@code rod-tiers.yml} via
 * {@link xyz.goga221.koi.config.RodTierConfig}. A tier's actual ItemStack is a Vulcan item
 * sharing its id (see {@link xyz.goga221.koi.item.RodItems}) - this menu only edits the
 * Koi-side gameplay data, never appearance, since Vulcan owns that. The optional upgrade recipe
 * ({@code upgrades-from}/{@code upgrade-material}) is shown read-only here - set it by hand in
 * {@code rod-tiers.yml}, same as fish biomes/bait in the fish editor.
 */
public class RodTierEditorMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final double RARITY_BOOST_STEP = 0.05;
    private static final int HOOK_TICK_STEP = 20;

    private final ChatInputPrompt prompt = KoiPlugin.getChatInputPrompt();
    private int currentPage = 0;

    public void open(Player player) {
        open(player, currentPage);
    }

    private void open(Player player, int page) {
        currentPage = page;
        Window.single()
                .setTitle("Koi Rod Tier Editor")
                .setGui(buildListGui(player, page))
                .open(player);
    }

    private Gui buildListGui(Player player, int page) {
        List<Item> items = new ArrayList<>();
        for (RodTier tier : KoiPlugin.getRodTierPool().getTiers()) {
            items.add(tierItem(tier));
        }

        return pagedListGui(items, addRodTierButton(), page, targetPage -> open(player, targetPage));
    }

    private Item addRodTierButton() {
        return new SimpleItem(new ItemBuilder(Material.EMERALD)
                .setDisplayName(mmName("<green>Add New Rod Tier"))
                .addLoreLines(mmLore("<gray>Click, then enter the id of an", "<gray>already-authored Vulcan item")), MenuText.debounced(click -> {
            Player player = click.getPlayer();
            player.closeInventory();
            startAddWizard(player);
        }));
    }

    private Item tierItem(RodTier tier) {
        ItemBuilder builder = vulcanIconOrBarrier(tier.getId());

        String upgrade = tier.getUpgradesFromId() == null
                ? "None"
                : tier.getUpgradesFromId() + " + " + (tier.getUpgradeMaterial() == null ? "?" : tier.getUpgradeMaterial().name());

        builder.addLoreLines(mmLore(
                "<gray>Vulcan id: <white>" + tier.getId(),
                "<gray>Rarity boost: <white>" + tier.getRarityBoost(),
                "<gray>Hook ticks: <white>" + tier.getMinHookTicks() + "-" + tier.getMaxHookTicks(),
                "<gray>Upgrades from: <white>" + upgrade,
                "",
                "<yellow>Left-click: <gray>-" + HOOK_TICK_STEP + " hook ticks (faster)",
                "<yellow>Drop (Q): <gray>+" + HOOK_TICK_STEP + " hook ticks (slower)",
                "<yellow>Right-click: <gray>+" + RARITY_BOOST_STEP + " rarity boost",
                "<yellow>Shift-right-click: <gray>-" + RARITY_BOOST_STEP + " rarity boost",
                "<red>Shift-left-click: <gray>remove"
        ));

        return new SimpleItem(builder, MenuText.debounced(click -> {
            Player player = click.getPlayer();
            RodTierPool pool = KoiPlugin.getRodTierPool();

            switch (click.getClickType()) {
                case LEFT -> {
                    tier.setMinHookTicks(Math.max(1, tier.getMinHookTicks() - HOOK_TICK_STEP));
                    tier.setMaxHookTicks(Math.max(tier.getMinHookTicks(), tier.getMaxHookTicks() - HOOK_TICK_STEP));
                    persistAndRefresh(player);
                }
                case DROP -> {
                    tier.setMinHookTicks(tier.getMinHookTicks() + HOOK_TICK_STEP);
                    tier.setMaxHookTicks(tier.getMaxHookTicks() + HOOK_TICK_STEP);
                    persistAndRefresh(player);
                }
                case RIGHT -> {
                    tier.setRarityBoost(tier.getRarityBoost() + RARITY_BOOST_STEP);
                    persistAndRefresh(player);
                }
                case SHIFT_RIGHT -> {
                    tier.setRarityBoost(Math.max(0.0, tier.getRarityBoost() - RARITY_BOOST_STEP));
                    persistAndRefresh(player);
                }
                case SHIFT_LEFT -> {
                    pool.removeTier(tier.getId());
                    persistAndRefresh(player);
                }
                default -> {
                }
            }
        }));
    }

    private void persistAndRefresh(Player player) {
        MenuText.persistAndRefresh(player, () -> open(player, currentPage), () -> KoiPlugin.getRodTierConfig().save(KoiPlugin.getRodTierPool()));
    }

    private void startAddWizard(Player player) {
        // See ChatInputPrompt.await for why every reply here goes through Placeholder.unparsed.
        prompt.await(player, "Enter the Vulcan item id for this rod tier (create it first with /v item create if needed):", id -> {
            if (KoiPlugin.getRodTierPool().findTier(id).isPresent()) {
                player.sendMessage(MM.deserialize("<red>A rod tier with id '<id>' already exists.</red>",
                        Placeholder.unparsed("id", id)));
                return;
            }
            if (!KoiPlugin.getVulcanApi().exists(id)) {
                player.sendMessage(MM.deserialize("<red>No Vulcan item '<id>' exists yet - run <white>/v item create <id></white> first.</red>",
                        Placeholder.unparsed("id", id)));
                return;
            }

            // Rarity-boost/hook-ticks default to no-bonus vanilla values, adjustable via
            // right/shift-right/left/drop-click afterward. Upgrade recipe stays unset here -
            // set upgrades-from/upgrade-material by hand in rod-tiers.yml if wanted.
            RodTier tier = new RodTier(id);
            KoiPlugin.getRodTierPool().addTier(tier);
            player.sendMessage(MM.deserialize("<green>Added rod tier '<id>'.</green>", Placeholder.unparsed("id", id)));
            open(player, currentPage);
            KoiPlugin.getScheduler().runTaskAsynchronously(() -> KoiPlugin.getRodTierConfig().save(KoiPlugin.getRodTierPool()));
        });
    }
}
