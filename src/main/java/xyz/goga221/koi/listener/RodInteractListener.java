package xyz.goga221.koi.listener;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.item.RodItems;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

/**
 * Vanilla right-clicks while a hook is out always retract it, so a real repeated-click
 * skill-check can't run on top of vanilla fishing. While a player has an active reel session,
 * every right-click is instead treated as a "reel pulse" and cancelled here before vanilla
 * gets a chance to end the cast. Switching the held item away from the rod (hotbar scroll or
 * dropping it) mid-reel cancels the session instead - you can't keep reeling in a fish you're
 * no longer holding a rod for.
 */
public class RodInteractListener implements Listener {

    private final KoiPlugin plugin;

    public RodInteractListener(KoiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onRodClick(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!plugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        plugin.getFishManager().pulseReel(player);
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (RodItems.tierOf(newItem) == null) {
            plugin.getFishManager().cancelSession(player);
        }
    }

    @EventHandler
    public void onDropRod(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!plugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }

        if (RodItems.tierOf(event.getItemDrop().getItemStack()) != null) {
            plugin.getFishManager().cancelSession(player);
        }
    }
}
