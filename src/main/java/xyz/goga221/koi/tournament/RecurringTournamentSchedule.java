package xyz.goga221.koi.tournament;

import java.util.UUID;

/**
 * A repeating tournament cadence (hourly/daily/weekly, in Chronos's own configured game-time
 * units - see {@link ChronosIntegration#secondsPerHour()} etc.). {@code nextTriggerTotalSeconds}
 * is compared against {@link ChronosIntegration#currentTotalGameSeconds()} every in-game minute;
 * once it's reached, the tournament starts and this advances by {@code intervalSeconds} again,
 * repeating indefinitely until cancelled. {@code chronosEventId} is the handle for the calendar
 * entry advertising the *next* occurrence - re-registered against the new
 * {@code nextTriggerTotalSeconds} each time this advances (via {@link ChronosIntegration#registerAt}),
 * so Chronos's calendar always shows the upcoming occurrence in advance, not just a same-day marker.
 */
public record RecurringTournamentSchedule(long intervalSeconds, int durationMinutes, long nextTriggerTotalSeconds,
                                           UUID chronosEventId) {
}
