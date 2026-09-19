package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.SeaCreature;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import xyz.xenondevs.invui.gui.Gui;
import xyz.xenondevs.invui.item.Click;
import xyz.xenondevs.invui.item.Item;
import xyz.xenondevs.invui.item.builder.ItemBuilder;
import xyz.xenondevs.invui.item.impl.SimpleItem;
import xyz.xenondevs.inventoryaccess.component.AdventureComponentWrapper;
import xyz.xenondevs.inventoryaccess.component.ComponentWrapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Bridges MiniMessage text into InvUI's {@link ComponentWrapper}-based item name/lore API, so
 * GUI items are built the same way as every other player-facing string in the plugin instead
 * of legacy {@code §} codes. Also holds the paged-catalog-editor scaffold shared by
 * {@link FishEditorMenu}, {@link RodTierEditorMenu}, and {@link SeaCreatureEditorMenu}.
 */
final class MenuText {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private MenuText() {
    }

    static ComponentWrapper mmName(String miniMessageTemplate, TagResolver... resolvers) {
        return new AdventureComponentWrapper(MM.deserialize(miniMessageTemplate, resolvers));
    }

    static ComponentWrapper[] mmLore(String... miniMessageLines) {
        ComponentWrapper[] wrapped = new ComponentWrapper[miniMessageLines.length];
        for (int i = 0; i < miniMessageLines.length; i++) {
            wrapped[i] = new AdventureComponentWrapper(MM.deserialize(miniMessageLines[i]));
        }
        return wrapped;
    }

    private static final int GUI_WIDTH = 9;
    private static final int GUI_HEIGHT = 5;
    private static final int[] CONTENT_SLOTS = buildContentSlots();

    private static int[] buildContentSlots() {
        // rows 1-3, columns 1-7 of the 9-wide grid - the interior of the border/nav-row layout
        // below, 21 slots/page, matching the old "x x x x x x x" x3 PagedGui structure.
        int[] slots = new int[21];
        int i = 0;
        for (int row = 1; row <= 3; row++) {
            for (int col = 1; col <= 7; col++) {
                slots[i++] = row * GUI_WIDTH + col;
            }
        }
        return slots;
    }

    /**
     * The paged list scaffold shared by every catalog editor menu - built as a plain
     * {@link Gui#empty} grid with items placed directly via {@code setItem}, rather than
     * InvUI's {@code PagedGui}. PagedGui's content slots were the common thread behind a
     * NUMBER_KEY/hotbar-swap item duplication bug: pressing 1-9 while hovering a page's item
     * actually copies it into that hotbar slot server-side, and InvUI's {@code setCancelled}
     * doesn't undo it. This mirrors the plain-Gui + direct setItem pattern already proven not to
     * exhibit that elsewhere in the network (Vulcan's/Tyche's main menus). {@code onNavigate} is
     * called with the target page whenever the caller should reopen itself there (prev/next
     * click) - callers remember it (e.g. an instance field) so a refresh after an edit reopens on
     * the same page instead of resetting to the first.
     */
    static Gui pagedListGui(List<Item> content, Item addButton, int page, IntConsumer onNavigate) {
        int pageSize = CONTENT_SLOTS.length;
        int pageAmount = Math.max(1, (content.size() + pageSize - 1) / pageSize);
        int clampedPage = Math.max(0, Math.min(page, pageAmount - 1));

        Item filler = filler();
        Gui gui = Gui.empty(GUI_WIDTH, GUI_HEIGHT);
        for (int slot = 0; slot < GUI_WIDTH * GUI_HEIGHT; slot++) {
            gui.setItem(slot, filler);
        }

        int start = clampedPage * pageSize;
        for (int i = 0; i < pageSize; i++) {
            int index = start + i;
            if (index < content.size()) {
                gui.setItem(CONTENT_SLOTS[i], content.get(index));
            }
        }

        boolean hasPrevious = clampedPage > 0;
        boolean hasNext = clampedPage < pageAmount - 1;
        gui.setItem(37, hasPrevious ? navButton("<yellow>Previous Page", () -> onNavigate.accept(clampedPage - 1)) : filler);
        gui.setItem(38, backButton());
        gui.setItem(39, addButton);
        gui.setItem(43, hasNext ? navButton("<yellow>Next Page", () -> onNavigate.accept(clampedPage + 1)) : filler);

        return gui;
    }

    private static Item filler() {
        return new SimpleItem(new ItemBuilder(Material.GRAY_STAINED_GLASS_PANE).setDisplayName(" "));
    }

    private static Item navButton(String name, Runnable onClick) {
        return new SimpleItem(new ItemBuilder(Material.ARROW).setDisplayName(mmName(name)), debounced(click -> onClick.run()));
    }

    private static Item backButton() {
        return new SimpleItem(new ItemBuilder(Material.BARRIER)
                .setDisplayName(mmName("<yellow>Back")),
                debounced(click -> new KoiConfigMenu().open(click.getPlayer())));
    }

    /**
     * Single background thread every catalog save goes through - {@code save} lambdas read the
     * live pool lazily (at execution time, not when scheduled), so if two saves for the same
     * file were ever running concurrently on different threads, disk write completion order
     * wouldn't be guaranteed to match edit order: an older in-flight save finishing after a
     * newer one would silently overwrite it, quietly reverting a just-made edit on disk (still
     * correct in memory, but gone after the next reload/restart - a real risk with two admins
     * editing the same catalog, or several rapid edits queuing overlapping saves). A single
     * thread makes every save run strictly one at a time, in submission order, so whichever one
     * runs last always reflects every edit made before it. Daemon so it never blocks JVM exit.
     */
    private static final ExecutorService SAVE_EXECUTOR = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "Koi-catalog-save");
        thread.setDaemon(true);
        return thread;
    });

    /**
     * Reopens the calling menu for {@code player} immediately (the in-memory pool is already
     * mutated, so there's nothing to wait on) and saves asynchronously - file I/O shouldn't run
     * on the region thread. Shared by every catalog editor menu's click handlers and add-wizards.
     */
    static void persistAndRefresh(Player player, Runnable reopen, Runnable save) {
        reopen.run();
        SAVE_EXECUTOR.execute(save);
    }

    /**
     * A catalog entry's live Vulcan item as the GUI icon, or a red barrier flagging that the
     * admin hasn't authored it yet - the same fallback every catalog editor menu needs, since
     * an entry can exist in Koi's own YAML before its Vulcan item does.
     */
    static ItemBuilder vulcanIconOrBarrier(String id) {
        ItemStack vulcanItem = KoiPlugin.getVulcanApi().getItem(id);
        return vulcanItem != null
                ? new ItemBuilder(vulcanItem.clone())
                : new ItemBuilder(Material.BARRIER).setDisplayName(mmName("<red><id> <dark_red>(missing Vulcan item)",
                        Placeholder.unparsed("id", id)));
    }

    /**
     * A spawn egg matching a sea creature's vanilla fallback mob - there's no Vulcan item to show
     * an icon for since Tyche owns its appearance; purely cosmetic, unrelated to what actually
     * spawns. Shared by {@link SeaCreatureEditorMenu} and {@link KoidexMenu}.
     */
    static ItemBuilder seaCreatureIcon(SeaCreature creature) {
        Material spawnEgg = Material.matchMaterial(creature.getEntityType().name() + "_SPAWN_EGG");
        return new ItemBuilder(spawnEgg != null ? spawnEgg : Material.TRIDENT);
    }

    private static final Map<UUID, Long> lastClickMillis = new ConcurrentHashMap<>();
    private static final long CLICK_COOLDOWN_MILLIS = 150L;

    /**
     * Wraps a button's click handler with a per-player cooldown, dropping repeat clicks that
     * land within {@link #CLICK_COOLDOWN_MILLIS} of the last one anywhere in a Koi menu - an
     * autoclicker/held-down mouse button firing many clicks a second was re-running a handler's
     * side effects (reopening the window, starting a chat wizard, saving to disk) far faster than
     * a real click ever would, even though InvUI itself already blocks the item from being pulled
     * out of the slot. Every catalog editor's item/add-button click handlers go through this.
     */
    static Consumer<Click> debounced(Consumer<Click> handler) {
        return click -> {
            UUID playerId = click.getPlayer().getUniqueId();
            long now = System.currentTimeMillis();
            Long last = lastClickMillis.put(playerId, now);
            if (last != null && now - last < CLICK_COOLDOWN_MILLIS) {
                return;
            }
            handler.accept(click);
        };
    }
}
