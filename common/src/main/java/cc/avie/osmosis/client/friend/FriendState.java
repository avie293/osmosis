package cc.avie.osmosis.client.friend;

/**
 * A friend as sent by the server on login.
 */
public record FriendState(FriendProfile profile, boolean online, int unread) {
}
