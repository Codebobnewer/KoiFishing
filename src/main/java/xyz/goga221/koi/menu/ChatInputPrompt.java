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
 * Reusable "await the player's next chat line" helper, used by {@link FishEditorMenu}'s
 * add-fish wizard for free-text fields InvUI has no dedicated input widget for.
 */
public class ChatInputPrompt implements Listener {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String CANCEL_KEYWORD = "cancel";

    private final KoiPlugin plugin;
    private final Map<UUID, Consumer<String>> pending = new ConcurrentHashMap<>();

    public ChatInputPrompt(KoiPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void await(Player player, String promptText, Consumer<String> onInput) {
        player.sendMessage(MM.deserialize("<yellow>" + promptText + "</yellow> <gray>(type 'cancel' to abort)</gray>"));
        pending.put(player.getUniqueId(), onInput);
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

        plugin.getScheduler().runTask(player, () -> {
            if (message.equalsIgnoreCase(CANCEL_KEYWORD)) {
                player.sendMessage(MM.deserialize("<red>Cancelled.</red>"));
                return;
            }
            handler.accept(message);
        });
    }
}
