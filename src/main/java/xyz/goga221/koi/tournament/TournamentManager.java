package xyz.goga221.koi.tournament;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishRarity;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Owns the single active server-wide {@link Tournament} (if any) and the admin-configured prize
 * waiting to be used by the next one. Not persisted across restarts - an in-flight tournament is
 * simply lost on a restart.
 */
public class TournamentManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private final KoiPlugin plugin;
    private volatile ItemStack pendingPrize;
    private volatile Tournament active;

    public TournamentManager(KoiPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active != null;
    }

    public Tournament getActive() {
        return active;
    }

    public void setPendingPrize(ItemStack prize) {
        this.pendingPrize = prize.clone();
    }

    public boolean hasPendingPrize() {
        return pendingPrize != null;
    }

    /**
     * start/stop can race each other across threads (two admins, or an admin racing the
     * natural end-of-timer callback) - synchronized so only one wins the transition.
     */
    public synchronized boolean start(int minutes) {
        if (active != null || pendingPrize == null) {
            return false;
        }

        long endTimeMillis = System.currentTimeMillis() + minutes * 60_000L;
        active = new Tournament(pendingPrize.clone(), endTimeMillis);

        long ticks = minutes * 60L * 20L;
        active.setEndTask(plugin.getScheduler().runTaskLater(this::endActiveTournament, ticks));

        plugin.getServer().broadcast(MM.deserialize("<gold>A Koi fishing tournament has begun! <yellow>"
                + minutes + " minutes</yellow> - best catch wins.</gold>"));
        return true;
    }

    public synchronized boolean stop() {
        if (active == null) {
            return false;
        }
        if (active.getEndTask() != null) {
            active.getEndTask().cancel();
        }
        endActiveTournament();
        return true;
    }

    public void recordCatch(Player player, FishRarity rarity) {
        Tournament tournament = active;
        if (tournament == null) {
            return;
        }
        boolean tookLead = tournament.recordCatch(player.getUniqueId(), rarity);
        if (tookLead) {
            // Broadcast to every player - player.getName() must go through a placeholder, not
            // string concatenation, so a crafted name can't inject MiniMessage tags server-wide.
            plugin.getServer().broadcast(MM.deserialize("<gold><player> takes the tournament lead with a <yellow>"
                            + rarity.name() + "</yellow> catch!</gold>",
                    Placeholder.unparsed("player", player.getName())));
        }
    }

    private synchronized void endActiveTournament() {
        Tournament finished = active;
        active = null;
        if (finished == null) {
            return;
        }

        if (finished.getLeaderId() == null) {
            plugin.getServer().broadcast(MM.deserialize("<gray>The Koi fishing tournament has ended with no catches.</gray>"));
            return;
        }

        Player winner = plugin.getServer().getPlayer(finished.getLeaderId());
        String winnerName = winner != null ? winner.getName() : finished.getLeaderId().toString();

        plugin.getServer().broadcast(MM.deserialize("<gold>The Koi fishing tournament is over! <yellow><winner></yellow> wins with a <yellow>"
                        + finished.getLeaderRarity().name() + "</yellow> catch!</gold>",
                Placeholder.unparsed("winner", winnerName)));

        if (winner != null) {
            var leftover = winner.getInventory().addItem(finished.getPrize());
            leftover.values().forEach(item -> winner.getWorld().dropItemNaturally(winner.getLocation(), item));
        }
    }
}
