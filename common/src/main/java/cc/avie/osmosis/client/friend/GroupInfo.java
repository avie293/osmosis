package cc.avie.osmosis.client.friend;

import java.util.List;
import java.util.UUID;

/**
 * A group chat as sent by the server. Members are in the order they joined, the owner first.
 */
public record GroupInfo(long id, String name, UUID owner, List<FriendProfile> members) {
    public boolean isOwner(UUID player) {
        return this.owner.equals(player);
    }
}
