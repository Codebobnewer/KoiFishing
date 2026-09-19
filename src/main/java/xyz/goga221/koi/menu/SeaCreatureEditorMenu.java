package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.fishing.SeaCreature;
import xyz.goga221.koi.fishing.SeaCreaturePool;
import xyz.goga221.koi.fishing.TycheIntegration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.invui.window.Window;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static xyz.goga221.koi.menu.MenuText.mmLore;
import static xyz.goga221.koi.menu.MenuText.mmName;
import static xyz.goga221.koi.menu.MenuText.pagedListGui;

/**
 * Admin GUI opened by {@code /koi config}: a paged list of every {@link SeaCreature} with
 * click-to-edit Tyche-mob/rarity/max-health/spawn-weight/remove, plus a guided chat wizard to
 * add new ones. Every edit is saved straight back to {@code sea-creatures.yml} via
 * {@link xyz.goga221.koi.config.SeaCreatureConfig}. A sea creature has no Vulcan item of its own
 * - its appearance and loot come entirely from its Tyche mob (see
 * {@link xyz.goga221.koi.fishing.TycheIntegration}); this menu only edits the encounter's
 * gameplay data. {@link SeaCreature#getRarity()} drives the reel-in stage's difficulty exactly
 * like a fish species' rarity does; landing that reel spawns the actual mob, which must then be
 * killed within despawn-seconds - see {@link xyz.goga221.koi.fishing.FishManager}.
 */
public class SeaCreatureEditorMenu {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final double HEALTH_STEP = 5.0;

    private final ChatInputPrompt prompt = KoiPlugin.getChatInputPrompt();
    private int currentPage = 0;

    public void open(Player player) {
        open(player, currentPage);
    }

    private void open(Player player, int page) {
        currentPage = page;
        Window.single()
                .setTitle("Koi Sea Creature Editor")
                .setGui(buildListGui(player, page))
                .open(player);
    }

    private Gui buildListGui(Player player, int page) {
        List<Item> items = new ArrayList<>();
        for (SeaCreature creature : KoiPlugin.getSeaCreaturePool().getCreatures()) {
            items.add(creatureItem(creature));
        }

        return pagedListGui(items, addCreatureButton(), page, targetPage -> open(player, targetPage));
    }

    private Item addCreatureButton() {
        return new SimpleItem(new ItemBuilder(Material.EMERALD)
                .setDisplayName(mmName("<green>Add New Sea Creature"))
                .addLoreLines(mmLore("<gray>Click, then follow the prompts", "<gray>for an id and a Tyche mob")), MenuText.debounced(click -> {
            Player player = click.getPlayer();
            player.closeInventory();
            startAddWizard(player);
        }));
    }

    private Item creatureItem(SeaCreature creature) {
        ItemBuilder builder = MenuText.seaCreatureIcon(creature);

        boolean spawnable = TycheIntegration.mobExists(creature.getTycheMobId());
        builder.addLoreLines(mmLore(
                "<gray>Id: <white>" + creature.getId(),
                "<gray>Tyche mob id: <white>" + creature.getTycheMobId() + (spawnable ? "" : " <red>(missing!)"),
                "<gray>Fallback entity type: <white>" + creature.getEntityType().name(),
                "<gray>Rarity: <white>" + creature.getRarity().name(),
                "<gray>Max health: <white>" + creature.getMaxHealth(),
                "<gray>Spawn weight: <white>" + creature.getSpawnWeight(),
                "<gray>Despawn seconds: <white>" + creature.getDespawnSeconds(),
                "",
                "<yellow>Left-click: <gray>+" + HEALTH_STEP + " max health",
                "<yellow>Drop (Q): <gray>-" + HEALTH_STEP + " max health",
                "<yellow>Right-click: <gray>+0.5 spawn weight",
                "<yellow>Shift-right-click: <gray>-0.5 spawn weight",
                "<yellow>Swap (F): <gray>cycle rarity",
                "<yellow>Middle-click: <gray>change Tyche mob id",
                "<red>Shift-left-click: <gray>remove"
        ));

        return new SimpleItem(builder, MenuText.debounced(click -> {
            Player player = click.getPlayer();
            SeaCreaturePool pool = KoiPlugin.getSeaCreaturePool();

            switch (click.getClickType()) {
                case LEFT -> {
                    creature.setMaxHealth(creature.getMaxHealth() + HEALTH_STEP);
                    persistAndRefresh(player);
                }
                case DROP -> {
                    creature.setMaxHealth(Math.max(1.0, creature.getMaxHealth() - HEALTH_STEP));
                    persistAndRefresh(player);
                }
                case RIGHT -> {
                    creature.setSpawnWeight(creature.getSpawnWeight() + 0.5);
                    persistAndRefresh(player);
                }
                case SHIFT_RIGHT -> {
                    creature.setSpawnWeight(Math.max(0.1, creature.getSpawnWeight() - 0.5));
                    persistAndRefresh(player);
                }
                case SHIFT_LEFT -> {
                    pool.removeCreature(creature.getId());
                    persistAndRefresh(player);
                }
                case SWAP_OFFHAND -> {
                    creature.setRarity(nextRarity(creature.getRarity()));
                    persistAndRefresh(player);
                }
                case MIDDLE -> {
                    player.closeInventory();
                    promptTycheMobId(player, creature);
                }
                default -> {
                }
            }
        }));
    }

    private FishRarity nextRarity(FishRarity current) {
        FishRarity[] values = FishRarity.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private void persistAndRefresh(Player player) {
        MenuText.persistAndRefresh(player, () -> open(player, currentPage), () -> KoiPlugin.getSeaCreatureConfig().save(KoiPlugin.getSeaCreaturePool()));
    }

    private void promptTycheMobId(Player player, SeaCreature creature) {
        prompt.await(player, "Enter the Tyche mob id this sea creature should spawn as:", mobId -> {
            if (!TycheIntegration.mobExists(mobId)) {
                player.sendMessage(MM.deserialize("<red>No Tyche mob '<id>' exists (or Tyche isn't installed).</red>",
                        Placeholder.unparsed("id", mobId)));
                return;
            }
            creature.setTycheMobId(mobId);
            player.sendMessage(MM.deserialize("<green>Sea creature '<id>' will now spawn as Tyche mob '<mob>'.</green>",
                    Placeholder.unparsed("id", creature.getId()), Placeholder.unparsed("mob", mobId)));
            persistAndRefresh(player);
        });
    }

    private void startAddWizard(Player player) {
        prompt.await(player, "Enter an id for this sea creature (your own label, e.g. 'abyssal_kraken'):", id -> {
            if (id.isBlank() || KoiPlugin.getSeaCreaturePool().findCreature(id).isPresent()) {
                player.sendMessage(MM.deserialize("<red>A sea creature with id '<id>' already exists.</red>",
                        Placeholder.unparsed("id", id)));
                return;
            }
            promptTycheMobIdForNew(player, id);
        });
    }

    private void promptTycheMobIdForNew(Player player, String id) {
        prompt.await(player, "Enter the Tyche mob id this sea creature should spawn as (create it first in Tyche if needed):", mobId -> {
            if (!TycheIntegration.mobExists(mobId)) {
                player.sendMessage(MM.deserialize("<red>No Tyche mob '<id>' exists (or Tyche isn't installed).</red>",
                        Placeholder.unparsed("id", mobId)));
                return;
            }
            promptEntityType(player, id, mobId);
        });
    }

    private void promptEntityType(Player player, String id, String tycheMobId) {
        prompt.await(player, "Enter a vanilla entity type as a fallback for if Tyche is ever unavailable (e.g. DROWNED, GUARDIAN):", typeName -> {
            EntityType entityType;
            try {
                entityType = EntityType.valueOf(typeName.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                player.sendMessage(MM.deserialize("<red>Unknown entity type: <type></red>",
                        Placeholder.unparsed("type", typeName)));
                return;
            }

            SeaCreature creature = new SeaCreature(id, tycheMobId, entityType);
            KoiPlugin.getSeaCreaturePool().addCreature(creature);
            player.sendMessage(MM.deserialize("<green>Added sea creature '<id>'.</green>", Placeholder.unparsed("id", id)));
            persistAndRefresh(player);
        });
    }
}
