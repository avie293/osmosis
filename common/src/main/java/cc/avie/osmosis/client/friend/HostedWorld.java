package cc.avie.osmosis.client.friend;

import java.util.UUID;

/**
 * A world another player is hosting right now.
 *
 * @param id stays the same for a world, even when it is renamed
 * @param invited whether the host invited this player now or before, which is needed to join
 */
public record HostedWorld(FriendProfile host, UUID id, String world, boolean invited) {
}
