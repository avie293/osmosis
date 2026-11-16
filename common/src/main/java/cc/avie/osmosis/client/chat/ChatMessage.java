package cc.avie.osmosis.client.chat;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A decrypted chat message.
 *
 * @param kind {@link #TEXT}, or {@link #INVITE} for an invite to a hosted world
 * @param time milliseconds since the epoch, assigned by the server
 * @param text {@code null} if the message could not be decrypted; for invites the world id and name, see
 *             {@link #invite(UUID, String)}
 */
public record ChatMessage(UUID sender, boolean outgoing, int kind, long time, @Nullable String text) {
    public static final int TEXT = 0;
    public static final int INVITE = 1;

    /** The text of an invite message: the world id, a line break and the world name. */
    public static String invite(UUID worldId, String worldName) {
        return worldId + "\n" + worldName;
    }

    /**
     * @return the world id of an invite, or {@code null} if this is no readable invite
     */
    public @Nullable UUID inviteWorldId() {
        int separator = this.text != null ? this.text.indexOf('\n') : -1;
        if (this.kind != INVITE || separator < 0) {
            return null;
        }
        try {
            return UUID.fromString(this.text.substring(0, separator));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String inviteWorldName() {
        return this.text == null ? "" : this.text.substring(this.text.indexOf('\n') + 1);
    }
}
