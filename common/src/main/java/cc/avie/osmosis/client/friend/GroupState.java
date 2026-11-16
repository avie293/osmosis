package cc.avie.osmosis.client.friend;

import cc.avie.osmosis.client.chat.ChatMessage;
import org.jspecify.annotations.Nullable;

/**
 * A group as sent by the server, with what only this player sees of it.
 */
public record GroupState(GroupInfo group, int unread, @Nullable ChatMessage lastMessage) {
}
