package xyz.goga221.koi.listener;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.SeaCreature;
import xyz.goga221.koi.fishing.SeaCreatureKeys;
import xyz.goga221.koi.item.RodTier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Rewards a player for killing a {@link SeaCreature} spawned by
 * {@link xyz.goga221.koi.fishing.FishManager#resolveSession} once its reel-in stage lands -
 * identified purely by the PDC tag {@link SeaCreatureKeys} sets at spawn time, so any other
 * plugin's own mob deaths are untouched. Vanilla drops/experience from the tagged entity are
 * cleared in favor of the Koi catch reward.
 */
public class SeaCreatureListener implements Listener {

    @EventHandler
    public void onSeaCreatureDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        String creatureId = SeaCreatureKeys.idOf(entity);
        if (creatureId == null) {
            return;
        }

        event.getDrops().clear();
        event.setDroppedExp(0);

        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }

        SeaCreature creature = KoiPlugin.getSeaCreaturePool().findCreature(creatureId).orElse(null);
        if (creature == null) {
            return;
        }

        String rodTierId = SeaCreatureKeys.rodTierIdOf(entity);
        RodTier rodTier = rodTierId == null ? null : KoiPlugin.getRodTierPool().findTier(rodTierId).orElse(null);
        String baitId = SeaCreatureKeys.baitIdOf(entity);

        KoiPlugin.getFishManager().grantSeaCreatureKillReward(killer, creature, rodTier, baitId);
    }
}
