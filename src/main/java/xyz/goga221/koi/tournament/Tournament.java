package xyz.goga221.koi.tournament;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import xyz.goga221.koi.fishing.FishRarity;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * State for the single active server-wide tournament: a prize item, an end time, and each
 * player's best catch so far. The first player to reach a new best rarity becomes (and stays)
 * the leader - no tie-breaking needed.
 */
@Getter
public class Tournament {

    private final ItemStack prize;
    private final long endTimeMillis;
    private final Map<UUID, FishRarity> bestByPlayer = new ConcurrentHashMap<>();

    // volatile: recordCatch() is synchronized for its check-then-set, but these are also read
    // unsynchronized from other threads (TournamentManager#endActiveTournament, /koi tournament
    // status) - without volatile there's no happens-before edge guaranteeing those reads see the
    // latest write.
    private volatile UUID leaderId;
    private volatile FishRarity leaderRarity;

    @Setter
    private MyScheduledTask endTask;

    @Setter
    private UUID chronosEventId;

    public Tournament(ItemStack prize, long endTimeMillis) {
        this.prize = prize;
        this.endTimeMillis = endTimeMillis;
    }

    /**
     * Records a catch and returns true if it made this player the new outright leader.
     * Synchronized: catches land on whichever region thread the catching player is on, and two
     * players on different Folia regions can call this at the same instant - the leader
     * check-then-set needs to be atomic.
     */
    public synchronized boolean recordCatch(UUID playerId, FishRarity rarity) {
        FishRarity previousBest = bestByPlayer.get(playerId);
        if (previousBest == null || rarity.ordinal() > previousBest.ordinal()) {
            bestByPlayer.put(playerId, rarity);
        }

        if (leaderRarity == null || rarity.ordinal() > leaderRarity.ordinal()) {
            leaderId = playerId;
            leaderRarity = rarity;
            return true;
        }
        return false;
    }

    public long secondsRemaining() {
        return Math.max(0, (endTimeMillis - System.currentTimeMillis()) / 1000);
    }
}
