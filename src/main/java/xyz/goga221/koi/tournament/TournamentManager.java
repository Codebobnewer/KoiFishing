package xyz.goga221.koi.tournament;

import xyz.goga221.koi.KoiPlugin;
import xyz.goga221.koi.fishing.FishRarity;
import lombok.Getter;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.UUID;

/**
 * Owns the single active server-wide {@link Tournament} (if any), at most one admin-scheduled
 * one-off future tournament, at most one repeating cadence, and the admin-configured prize
 * waiting to be used by the next one. Not persisted across restarts - an in-flight, scheduled, or
 * repeating tournament setup is simply lost on a restart. Never references a Chronos type
 * directly - see {@link ChronosIntegration}.
 */
public class TournamentManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private static final String TOURNAMENT_NAME = "Koi Fishing Tournament";
    private static final String NEXT_OCCURRENCE_DESCRIPTION = "The next occurrence of the recurring fishing tournament.";

    private volatile ItemStack pendingPrize;
    @Getter
    private volatile Tournament active;
    @Getter
    private volatile ScheduledTournament scheduled;
    @Getter
    private volatile RecurringTournamentSchedule recurring;
    private volatile ChronosIntegration chronos;

    public void attachChronosIntegration(ChronosIntegration integration) {
        this.chronos = integration;
    }

    public boolean isActive() {
        return active != null;
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

        if (chronos != null) {
            try {
                active.setChronosEventId(chronos.registerNow(TOURNAMENT_NAME, "Happening now - reel in the biggest catch to win!"));
            } catch (IllegalArgumentException e) {
                // Non-fatal - the tournament still runs even if Chronos couldn't take the entry.
            }
        }

        long ticks = minutes * 60L * 20L;
        active.setEndTask(KoiPlugin.getScheduler().runTaskLater(this::endActiveTournament, ticks));

        KoiPlugin.getInstance().getServer().broadcast(MM.deserialize("<gold>A Koi fishing tournament has begun! <yellow>"
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

    /**
     * Schedules a future tournament for Chronos's calendar to reach {@code year/month/day
     * hour:minute}; {@link ChronosIntegration} calls {@link #checkSchedule} every in-game minute
     * to notice when that arrives and auto-{@link #start} it.
     *
     * @throws IllegalStateException if Chronos is unavailable, a prize isn't set, or a
     *                                tournament is already running/scheduled
     * @throws IllegalArgumentException if the date/time is invalid for Chronos's configured calendar
     */
    public synchronized void scheduleAt(int year, int month, int day, int hour, int minute, int durationMinutes) {
        if (chronos == null) {
            throw new IllegalStateException("Chronos isn't installed.");
        }
        if (active != null) {
            throw new IllegalStateException("A tournament is already running.");
        }
        if (scheduled != null) {
            throw new IllegalStateException("A tournament is already scheduled.");
        }
        if (pendingPrize == null) {
            throw new IllegalStateException("Set a prize first with /koi tournament setprize.");
        }

        UUID id = chronos.registerOneTime(TOURNAMENT_NAME, "Reel in the biggest catch to win!", year, month, day, hour, minute);
        scheduled = new ScheduledTournament(year, month, day, hour, minute, durationMinutes, id);
    }

    public synchronized boolean cancelScheduled() {
        ScheduledTournament pending = scheduled;
        if (pending == null) {
            return false;
        }
        scheduled = null;
        if (chronos != null) {
            chronos.unregister(pending.chronosEventId());
        }
        return true;
    }

    /**
     * Starts a repeating cadence ("hourly"/"daily"/"weekly", in Chronos's own configured
     * game-time units) - the tournament auto-starts every interval, indefinitely, until
     * {@link #stopRepeating()}. Unlike {@link #scheduleAt}, this isn't a one-shot slot: it keeps
     * re-arming itself in {@link #checkRecurring}.
     *
     * @throws IllegalStateException if Chronos is unavailable or a prize isn't set
     * @throws IllegalArgumentException if {@code intervalName} isn't hourly/daily/weekly
     */
    public synchronized void startRepeating(String intervalName, int durationMinutes) {
        if (chronos == null) {
            throw new IllegalStateException("Chronos isn't installed.");
        }
        if (pendingPrize == null) {
            throw new IllegalStateException("Set a prize first with /koi tournament setprize.");
        }

        long intervalSeconds = switch (intervalName) {
            case "hourly" -> chronos.secondsPerHour();
            case "daily" -> chronos.secondsPerDay();
            case "weekly" -> chronos.secondsPerWeek();
            default -> throw new IllegalArgumentException("Unknown interval: " + intervalName);
        };

        long nextTrigger = chronos.currentTotalGameSeconds() + intervalSeconds;
        recurring = new RecurringTournamentSchedule(intervalSeconds, durationMinutes,
                nextTrigger, registerNextOccurrence(nextTrigger));
    }

    /**
     * Registers a calendar entry advertising the recurring tournament's next occurrence.
     * Non-fatal on failure - the cadence still runs even if Chronos couldn't take the entry.
     */
    private UUID registerNextOccurrence(long triggerTotalSeconds) {
        if (chronos == null) {
            return null;
        }
        try {
            return chronos.registerAt(TOURNAMENT_NAME, NEXT_OCCURRENCE_DESCRIPTION, triggerTotalSeconds);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public synchronized boolean stopRepeating() {
        if (recurring == null) {
            return false;
        }
        if (chronos != null && recurring.chronosEventId() != null) {
            chronos.unregister(recurring.chronosEventId());
        }
        recurring = null;
        return true;
    }

    /**
     * Called by {@link ChronosIntegration} every in-game minute - plain ints only, so this
     * class never needs to know a Chronos type exists.
     */
    synchronized void checkSchedule(int year, int month, int day, int hour, int minute) {
        ScheduledTournament pending = scheduled;
        if (pending == null || !pending.matches(year, month, day, hour, minute)) {
            return;
        }

        scheduled = null;
        if (chronos != null) {
            chronos.unregister(pending.chronosEventId());
        }
        start(pending.durationMinutes());
    }

    /**
     * Called by {@link ChronosIntegration} every in-game minute with the absolute game clock -
     * if a repeating schedule's next trigger has been reached, starts the tournament (a no-op if
     * one's already running - see {@link #start}) and re-arms the next occurrence.
     */
    synchronized void checkRecurring(long totalGameSeconds) {
        RecurringTournamentSchedule schedule = recurring;
        if (schedule == null || totalGameSeconds < schedule.nextTriggerTotalSeconds()) {
            return;
        }

        if (chronos != null && schedule.chronosEventId() != null) {
            chronos.unregister(schedule.chronosEventId());
        }

        long nextTrigger = totalGameSeconds + schedule.intervalSeconds();
        recurring = new RecurringTournamentSchedule(schedule.intervalSeconds(), schedule.durationMinutes(),
                nextTrigger, registerNextOccurrence(nextTrigger));
        start(schedule.durationMinutes());
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
            KoiPlugin.getInstance().getServer().broadcast(MM.deserialize("<gold><player> takes the tournament lead with a <yellow>"
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

        if (chronos != null && finished.getChronosEventId() != null) {
            chronos.unregister(finished.getChronosEventId());
        }

        if (finished.getLeaderId() == null) {
            KoiPlugin.getInstance().getServer().broadcast(MM.deserialize("<gray>The Koi fishing tournament has ended with no catches.</gray>"));
            return;
        }

        Player winner = KoiPlugin.getInstance().getServer().getPlayer(finished.getLeaderId());
        String winnerName = winner != null ? winner.getName() : finished.getLeaderId().toString();

        KoiPlugin.getInstance().getServer().broadcast(MM.deserialize("<gold>The Koi fishing tournament is over! <yellow><winner></yellow> wins with a <yellow>"
                        + finished.getLeaderRarity().name() + "</yellow> catch!</gold>",
                Placeholder.unparsed("winner", winnerName)));

        if (winner != null) {
            HashMap<Integer, ItemStack> leftover = winner.getInventory().addItem(finished.getPrize());
            leftover.values().forEach(item -> winner.getWorld().dropItemNaturally(winner.getLocation(), item));
        }
    }
}
