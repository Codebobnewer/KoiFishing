package xyz.goga221.koi.tournament;

import java.util.UUID;

/**
 * An admin-scheduled future tournament, waiting for Chronos's in-game clock to reach
 * {@code year/month/day/hour/minute}. {@code chronosEventId} is the handle for the calendar
 * entry advertising it, unregistered once it fires or is cancelled.
 */
public record ScheduledTournament(int year, int month, int day, int hour, int minute, int durationMinutes, UUID chronosEventId) {

    boolean matches(int year, int month, int day, int hour, int minute) {
        return this.year == year && this.month == month && this.day == day
                && this.hour == hour && this.minute == minute;
    }
}
