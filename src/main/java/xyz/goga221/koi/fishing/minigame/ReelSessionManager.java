package xyz.goga221.koi.fishing.minigame;

import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishRarity;
import xyz.goga221.koi.fishing.FishSpecies;
import xyz.goga221.koi.item.RodTier;
import org.bukkit.Sound;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Owns pending casts and active {@link ReelSession}s, one per player. Session ticking runs on
 * UniversalScheduler's per-player entity scheduler, so it stays Folia-region-safe.
 */
public class ReelSessionManager {

    private final KoiPlugin plugin;
    private final Map<UUID, CastContext> pendingCasts = new ConcurrentHashMap<>();
    private final Map<UUID, ReelSession> activeSessions = new ConcurrentHashMap<>();

    public ReelSessionManager(KoiPlugin plugin) {
        this.plugin = plugin;
    }

    public void prepareCast(UUID playerId, CastContext context) {
        pendingCasts.put(playerId, context);
    }

    public CastContext consumeCast(UUID playerId) {
        return pendingCasts.remove(playerId);
    }

    public boolean hasActiveSession(UUID playerId) {
        return activeSessions.containsKey(playerId);
    }

    /**
     * Ends a player's session without resolving it as a catch (e.g. they switched off their
     * rod). Cancels the ticking task and clears the action bar; the caller is responsible for
     * removing the hook and messaging the player.
     */
    public Optional<ReelSession> cancel(Player player) {
        ReelSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return Optional.empty();
        }
        if (session.getTask() != null) {
            session.getTask().cancel();
        }
        ClickBarRenderer.clear(player);
        return Optional.of(session);
    }

    public void startSession(Player player, FishSpecies species, RodTier rodTier, String baitId, FishHook hook,
                              BiConsumer<Player, ReelSession> onResolve) {
        ReelSession session = new ReelSession(player.getUniqueId(), species, rodTier, baitId, hook);
        activeSessions.put(player.getUniqueId(), session);
        ClickBarRenderer.render(player, session);

        long period = Math.max(1L, plugin.getConfigManager().getTickPeriodTicks());
        MyScheduledTask task = plugin.getScheduler().runTaskTimer(player, () -> tick(player, onResolve), period, period);
        session.setTask(task);
    }

    public void pulse(Player player, BiConsumer<Player, ReelSession> onResolve) {
        ReelSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        session.pulse();
        playPulseSound(player, session);
        ClickBarRenderer.render(player, session);
        resolveIfFinished(player, session, onResolve);
    }

    private void playPulseSound(Player player, ReelSession session) {
        FishRarity tier = FishRarity.forCumulativeIndex(session.getRevealedBars() - 1);
        float pitch = 0.8f + tier.ordinal() * 0.1f;
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, pitch);
    }

    private void tick(Player player, BiConsumer<Player, ReelSession> onResolve) {
        ReelSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        ClickBarRenderer.render(player, session); // action bar text fades if not refreshed, even with no progress change
        resolveIfFinished(player, session, onResolve);
    }

    private void resolveIfFinished(Player player, ReelSession session, BiConsumer<Player, ReelSession> onResolve) {
        if (session.isComplete()) {
            endSession(player, onResolve);
        }
    }

    private void endSession(Player player, BiConsumer<Player, ReelSession> onResolve) {
        ReelSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.getTask() != null) {
            session.getTask().cancel();
        }
        ClickBarRenderer.clear(player);
        onResolve.accept(player, session);
    }
}
