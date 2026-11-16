package cc.avie.osmosis.client.chat;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A conversation: either with one friend or in a group.
 */
public record ChatTarget(@Nullable UUID friend, long group) {
    public static ChatTarget direct(UUID friend) {
        return new ChatTarget(friend, 0);
    }

    public static ChatTarget group(long group) {
        return new ChatTarget(null, group);
    }

    public boolean isGroup() {
        return this.friend == null;
    }
}
