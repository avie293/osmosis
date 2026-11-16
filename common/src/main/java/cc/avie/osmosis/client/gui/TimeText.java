package cc.avie.osmosis.client.gui;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * Turns timestamps into short texts for the friends screen.
 */
final class TimeText {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd", Locale.ROOT);
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;

    private TimeText() {
    }

    /** "Last online 3 days ago", or "Offline" when the time is unknown. */
    static Component lastOnline(long time) {
        if (time <= 0) {
            return Component.translatable("osmosis.friends.offline");
        }
        return Component.translatable("osmosis.friends.last_online", ago(time));
    }

    static Component ago(long time) {
        long elapsed = Math.max(0, System.currentTimeMillis() - time);
        if (elapsed < MINUTE) {
            return Component.translatable("osmosis.time.just_now");
        }
        if (elapsed < HOUR) {
            return plural("minutes", elapsed / MINUTE);
        }
        if (elapsed < DAY) {
            return plural("hours", elapsed / HOUR);
        }
        if (elapsed < 7 * DAY) {
            return plural("days", elapsed / DAY);
        }
        if (elapsed < 30 * DAY) {
            return plural("weeks", elapsed / (7 * DAY));
        }
        return plural("months", elapsed / (30 * DAY));
    }

    private static Component plural(String unit, long amount) {
        return Component.translatable("osmosis.time." + unit + (amount == 1 ? ".one" : ""), amount);
    }

    /** Time for today, "Yesterday", the weekday within a week, else the date. */
    static String chatListDate(long time) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        LocalDate date = dateTime.toLocalDate();
        LocalDate today = LocalDate.now();
        if (date.equals(today)) {
            return TIME.format(dateTime);
        }
        if (date.equals(today.minusDays(1))) {
            return Component.translatable("osmosis.time.yesterday").getString();
        }
        if (date.isAfter(today.minusDays(7))) {
            return date.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ROOT);
        }
        return DATE.format(dateTime);
    }
}
