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

    @EventHandler(ignoreCancelled = false)
    public void onFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        if (!KoiPlugin.getFishManager().isWorldEnabled(player.getWorld())) {
            return;
        }

        if (KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        FishHook hook = event.getHook();
        switch (event.getState()) {
            case FISHING -> KoiPlugin.getFishManager().handleCast(player, hook);
            case BITE -> KoiPlugin.getFishManager().handleBite(player, hook);
            default -> {
            }
        }
    }
}
