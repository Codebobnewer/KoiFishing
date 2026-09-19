package xyz.goga221.koi.fishing.minigame;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import xyz.goga221.koi.fishing.Catchable;
import xyz.goga221.koi.item.RodTier;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.entity.FishHook;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Per-player state for an active reel-in minigame: bars light up one at a time as the player
 * clicks, in order through every rarity tier's own color starting at Common. The catch lands
 * somewhere inside the true rarity's own segment (rolled once when the session starts, so the
 * player never knows in advance exactly how many clicks it'll take) - reaching a rarer catch
 * means clicking through every lower tier's bars first, which is where the extra clicks for
 * rarer catches come from. There's no timer - once hooked, the catch waits for the player to
 * finish clicking.
 */
@Getter
public class ReelSession {

    private final UUID playerId;
    private final Catchable catchable;
    private final RodTier rodTier;
    private final String baitId;
    private final FishHook hook;
    private final int requiredClicks;

    private int revealedBars = 0;

    @Setter
    private MyScheduledTask task;

    public ReelSession(UUID playerId, Catchable catchable, RodTier rodTier, String baitId, FishHook hook) {
        this.playerId = playerId;
        this.catchable = catchable;
        this.rodTier = rodTier;
        this.baitId = baitId;
        this.hook = hook;
        int barsInOwnTier = ThreadLocalRandom.current().nextInt(1, catchable.getRarity().getBarCount() + 1);
        this.requiredClicks = catchable.getRarity().cumulativeBarsBefore() + barsInOwnTier;
    }

    public void pulse() {
        revealedBars = Math.min(requiredClicks, revealedBars + 1);
    }

    public boolean isComplete() {
        return revealedBars >= requiredClicks;
    }
}
