package xyz.goga221.koi.tournament;

import me.goga221.chronos.api.CalendarEvent;
import me.goga221.chronos.api.ChronosAPI;
import me.goga221.chronos.api.ChronosProvider;
import me.goga221.chronos.api.GameDate;
import me.goga221.chronos.api.GameDateTime;
import me.goga221.chronos.event.TimeMinuteChangeEvent;
import xyz.goga221.koi.KoiPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.Optional;
import java.util.UUID;

/**
 * The only class in Koi allowed to reference a Chronos type. Confining every {@code ChronosAPI}/
 * {@code CalendarEvent}/event-class reference here means {@link TournamentManager} (and the rest
 * of Koi) never touches Chronos at all - so as long as this class is only ever constructed and
 * registered when Chronos is confirmed present ({@link #tryEnable}), Koi stays loadable with
 * Chronos absent regardless of JVM class-verification timing. {@code softdepend: [Chronos]} in
 * plugin.yml only guarantees load order when Chronos *is* installed; it doesn't require it.
 */
public class ChronosIntegration implements Listener {

    private final TournamentManager manager;
    private final ChronosAPI chronos;

    private ChronosIntegration(TournamentManager manager, ChronosAPI chronos) {
        this.manager = manager;
        this.chronos = chronos;
    }

    public static Optional<ChronosIntegration> tryEnable() {
        KoiPlugin plugin = KoiPlugin.getInstance();
        if (plugin.getServer().getPluginManager().getPlugin("Chronos") == null) {
            return Optional.empty();
        }
        try {
            ChronosAPI api = ChronosProvider.get();
            ChronosIntegration integration = new ChronosIntegration(KoiPlugin.getTournamentManager(), api);
            plugin.getServer().getPluginManager().registerEvents(integration, plugin);
            return Optional.of(integration);
        } catch (IllegalStateException e) {
            return Optional.empty();
        } catch (LinkageError e) {
            // Chronos is registered with the server but its classes aren't actually resolvable
            // from Koi's classloader - e.g. Koi was built/deployed before plugin.yml declared
            // softdepend: [Chronos], so Paper never wired classloader visibility between them.
            // Degrade gracefully rather than taking down the rest of Koi over it.
            plugin.getLogger().warning("Chronos was detected but its classes could not be loaded "
                    + "(" + e + ") - tournament scheduling will be unavailable. Try rebuilding/redeploying Koi.");
            return Optional.empty();
        }
    }

    /**
     * Registers a one-time calendar entry for an admin-scheduled future tournament.
     *
     * @throws IllegalArgumentException if the date/time is invalid for Chronos's configured calendar
     */
    public UUID registerOneTime(String name, String description, int year, int month, int day, int hour, int minute) {
        return chronos.registerCalendarEvent(new CalendarEvent(name, description, year, month, day, hour, minute, 0));
    }

    /**
     * Registers a calendar entry for a tournament that's starting immediately, so it's still
     * visible in Chronos's calendar for the base "advertise the running tournament" case.
     */
    public UUID registerNow(String name, String description) {
        return registerAt(name, description, currentTotalGameSeconds());
    }

    /**
     * Registers a one-time calendar entry for an absolute game-time instant, e.g. a computed
     * future trigger for a recurring schedule ({@code currentTotalGameSeconds() + intervalSeconds}).
     * Backed by {@link ChronosAPI#getDateTimeAt(long)}, so unlike {@link #registerNow}, this can
     * advertise an occurrence before it actually happens.
     *
     * @throws IllegalArgumentException if the resulting date/time is invalid for Chronos's configured calendar
     */
    public UUID registerAt(String name, String description, long totalGameSeconds) {
        GameDateTime at = chronos.getDateTimeAt(totalGameSeconds);
        GameDate date = at.date();
        return registerOneTime(name, description, date.year(), date.month(), date.day(),
                at.time().hour(), at.time().minute());
    }

    public void unregister(UUID id) {
        chronos.unregisterCalendarEvent(id);
    }

    /**
     * The absolute, monotonic game-time clock - used for recurring schedules instead of calendar
     * fields, since "next hour" can roll into a different day/month/year and computing that by
     * hand would duplicate Chronos's own calendar math (see {@link #registerAt}, which does it
     * properly via {@link ChronosAPI#getDateTimeAt(long)}).
     */
    public long currentTotalGameSeconds() {
        return chronos.getCurrentDateTime().totalGameSeconds();
    }

    public long secondsPerHour() {
        return (long) chronos.getSecondsPerMinute() * chronos.getMinutesPerHour();
    }

    public long secondsPerDay() {
        return secondsPerHour() * chronos.getHoursPerDay();
    }

    public long secondsPerWeek() {
        return secondsPerDay() * chronos.getDaysPerWeek();
    }

    @EventHandler
    public void onMinuteChange(TimeMinuteChangeEvent event) {
        GameDateTime current = event.getCurrent();
        GameDate date = current.date();
        manager.checkSchedule(date.year(), date.month(), date.day(), current.time().hour(), current.time().minute());
        manager.checkRecurring(current.totalGameSeconds());
    }
}
