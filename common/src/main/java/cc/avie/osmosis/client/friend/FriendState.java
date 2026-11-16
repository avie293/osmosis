package cc.avie.osmosis.client.friend;

import cc.avie.osmosis.client.chat.ChatMessage;
import org.jspecify.annotations.Nullable;

/**
 * A friend as sent by the server on login.
 *
 * @param lastSeen when the friend was last online, in milliseconds since the epoch, 0 if unknown
 * @param lastMessage the newest message with this friend, for the chat list preview
 */
public record FriendState(FriendProfile profile, boolean online, int unread, long lastSeen,
                          @Nullable ChatMessage lastMessage) {
}
