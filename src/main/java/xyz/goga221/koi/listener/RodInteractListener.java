package xyz.goga221.koi.listener;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.item.RodItems;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Vanilla right-clicks while a hook is out always retract it, so a real repeated-click
 * skill-check can't run on top of vanilla fishing. While a player has an active reel session,
 * every right-click is instead treated as a "reel pulse" and cancelled here before vanilla
 * gets a chance to end the cast. Everything else that should end a reel session early - dying,
 * the hook getting invalidated (out of range, damage retracting the line, etc.), or the rod
 * leaving the held slot by any means (hotbar scroll, dropping it, or an inventory click/drag
 * that swaps the held slot's contents without ever firing {@link PlayerItemHeldEvent}) - is
 * caught below and routed through {@link xyz.goga221.koi.fishing.FishManager#cancelSession}.
 */
public class RodInteractListener implements Listener {

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
        if (!KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        KoiPlugin.getFishManager().pulseReel(player);
    }

    @EventHandler
    public void onHeldItemChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (!KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }

        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (RodItems.tierOf(newItem) == null) {
            KoiPlugin.getFishManager().cancelSession(player);
        }
    }

    @EventHandler
    public void onDropRod(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        if (!KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }

        if (RodItems.tierOf(event.getItemDrop().getItemStack()) != null) {
            KoiPlugin.getFishManager().cancelSession(player);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            KoiPlugin.getFishManager().cancelSession(player);
        }
    }

    /**
     * Vanilla removes the {@link FishHook} itself (out of range, the owner taking damage, etc.)
     * without necessarily routing back through a cancellable {@code PlayerFishEvent} - catching
     * its removal directly is the only reliable way to notice a snapped line. Our own resolve/
     * cancel paths already remove the session from {@link xyz.goga221.koi.fishing.FishManager}
     * before removing the hook themselves, so {@code cancelSession} below is a safe no-op for
     * those - this only ever does real work for a removal Koi didn't initiate.
     */
    @EventHandler
    public void onHookRemoved(EntityRemoveEvent event) {
        if (!(event.getEntity() instanceof FishHook hook)) {
            return;
        }
        ProjectileSource shooter = hook.getShooter();
        if (!(shooter instanceof Player player)) {
            return;
        }
        if (KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            KoiPlugin.getFishManager().cancelSession(player);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            checkRodNextTick(player);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            checkRodNextTick(player);
        }
    }

    /**
     * {@link InventoryClickEvent}/{@link InventoryDragEvent} fire before complex click types
     * (shift-click, double-click, hotbar-swap) finish moving items, so the held slot's final
     * contents aren't reliable until a tick later.
     */
    private void checkRodNextTick(Player player) {
        if (!KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())) {
            return;
        }
        KoiPlugin.getScheduler().runTaskLater(player, () -> {
            if (KoiPlugin.getFishManager().hasActiveSession(player.getUniqueId())
                    && RodItems.tierOf(player.getInventory().getItemInMainHand()) == null) {
                KoiPlugin.getFishManager().cancelSession(player);
            }
        }, 1L);
    }
}
