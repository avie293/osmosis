package cc.avie.osmosis.client.chat;

/**
 * News from the server operator, shown to every player.
 *
 * @param time milliseconds since the epoch
 */
public record Announcement(long time, String text) {
}
