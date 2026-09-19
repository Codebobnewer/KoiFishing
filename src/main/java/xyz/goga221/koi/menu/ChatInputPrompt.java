package xyz.goga221.koi.menu;

import xyz.goga221.koi.KoiPlugin;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Reusable "await the player's next chat line" helper, used by the catalog editor menus'
 * (fish/rod-tier/bait-type/sea-creature) add wizards for free-text fields InvUI has no
 * dedicated input widget for.
 */
public class ChatInputPrompt implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String CANCEL_KEYWORD = "cancel";

    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputPrompt() {
        KoiPlugin.getInstance().getServer().getPluginManager().registerEvents(this, KoiPlugin.getInstance());
    }

    /**
     * {@code onInput} receives raw, untrusted chat text - callers must pass it through
     * {@code Placeholder.unparsed} rather than splicing it into a MiniMessage template string,
     * or a crafted reply could inject MiniMessage tags into a message shown to other players.
     */
    public void await(Player player, String promptText, Consumer<String> onInput) {
        Consumer<String> previous = pending.put(player.getUniqueId(), onInput);
        if (previous != null) {
            // Silently swapping it out would leave whichever wizard set it up waiting forever
            // for a reply that's now bound to a different question instead.
            player.sendMessage(MM.deserialize("<red>Your previous prompt was cancelled.</red>"));
        }
        player.sendMessage(MM.deserialize("<yellow>" + promptText + "</yellow> <gray>(type 'cancel' to abort)</gray>"));
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Consumer<String> handler = pending.remove(event.getPlayer().getUniqueId());
        if (handler == null) {
            return;
        }
        event.setCancelled(true);

        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());

        KoiPlugin.getScheduler().runTask(player, () -> {
            if (message.trim().equalsIgnoreCase(CANCEL_KEYWORD)) {
                player.sendMessage(MM.deserialize("<red>Cancelled.</red>"));
                return;
            }
            handler.accept(message);
        });
    }
}
