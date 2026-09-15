package xyz.goga221.koi.listener;

import xyz.goga221.koi.KoiPlugin;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;

/**
 * Hooks {@link PlayerFishEvent} to drive the custom cast/bite flow via
 * {@link xyz.goga221.koi.fishing.FishManager}. Every event for a player with an active reel
 * session is cancelled so vanilla can't resolve or end the cast out from under the minigame.
 */
public class FishingListener implements Listener {

    private final KoiPlugin plugin;

    public FishingListener(KoiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getFishManager().isWorldEnabled(player.getWorld())) {
            return;
        }

        if (plugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> plugin.getFishManager().handleCast(player, hook);
            case BITE -> plugin.getFishManager().handleBite(player, hook);
            default -> {
            }
        }
    }
}
