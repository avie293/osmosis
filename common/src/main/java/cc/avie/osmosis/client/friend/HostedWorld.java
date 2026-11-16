package cc.avie.osmosis.client.friend;

/**
 * A world another player is hosting right now.
 *
 * @param invited whether the host invited this player, which is needed to join
 */
public record HostedWorld(FriendProfile host, String world, boolean invited) {
}
