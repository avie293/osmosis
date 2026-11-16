package cc.avie.osmosis.client.friend;

import cc.avie.osmosis.client.gui.OsmosisToast;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The client's view of its friends and requests, mirrored from the server.
 *
 * <p>Only touched on the render thread. The connection hands every update over to it.
 */
public final class FriendList {
    private final Map<UUID, FriendProfile> friends = new LinkedHashMap<>();
    private final Map<UUID, FriendProfile> incoming = new LinkedHashMap<>();
    private final Map<UUID, FriendProfile> outgoing = new LinkedHashMap<>();
    private final Set<UUID> online = new HashSet<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private ConnectionStatus status = ConnectionStatus.CONNECTING;
    private @Nullable FriendProfile self;
    private boolean onlineToasts = true;

    public ConnectionStatus status() {
        return this.status;
    }

    public @Nullable FriendProfile self() {
        return this.self;
    }

    public Collection<FriendProfile> friends() {
        return Collections.unmodifiableCollection(this.friends.values());
    }

    public Collection<FriendProfile> incoming() {
        return Collections.unmodifiableCollection(this.incoming.values());
    }

    public Collection<FriendProfile> outgoing() {
        return Collections.unmodifiableCollection(this.outgoing.values());
    }

    public boolean isOnline(UUID id) {
        return this.online.contains(id);
    }

    /**
     * @return the relation to the player with this name, or {@code null} if there is none
     */
    public @Nullable Component describeRelation(String name) {
        if (this.self != null && this.self.name().equalsIgnoreCase(name)) {
            return Component.translatable("osmosis.error.cannot_add_self");
        }
        if (containsName(this.friends, name)) {
            return Component.translatable("osmosis.error.already_friends");
        }
        if (containsName(this.outgoing, name)) {
            return Component.translatable("osmosis.error.already_requested");
        }
        return null;
    }

    private static boolean containsName(Map<UUID, FriendProfile> profiles, String name) {
        for (FriendProfile profile : profiles.values()) {
            if (profile.name().equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public void addListener(Runnable listener) {
        this.listeners.add(listener);
    }

    public void removeListener(Runnable listener) {
        this.listeners.remove(listener);
    }

    public void setOnlineToasts(boolean onlineToasts) {
        this.onlineToasts = onlineToasts;
    }

    public void setStatus(ConnectionStatus status) {
        if (this.status == status) {
            return;
        }
        this.status = status;
        if (status != ConnectionStatus.CONNECTED) {
            // Presence is unknown while disconnected; the lists stay visible until the next login replaces them.
            this.online.clear();
        }
        this.changed();
    }

    public void reset(FriendProfile self, Map<FriendProfile, Boolean> friends, List<FriendProfile> incoming,
                      List<FriendProfile> outgoing) {
        this.self = self;
        this.friends.clear();
        this.online.clear();
        friends.forEach((profile, isOnline) -> {
            this.friends.put(profile.id(), profile);
            if (isOnline) {
                this.online.add(profile.id());
            }
        });
        replace(this.incoming, incoming);
        replace(this.outgoing, outgoing);
        this.status = ConnectionStatus.CONNECTED;
        this.changed();
    }

    private static void replace(Map<UUID, FriendProfile> target, List<FriendProfile> profiles) {
        target.clear();
        for (FriendProfile profile : profiles) {
            target.put(profile.id(), profile);
        }
    }

    public void addFriend(FriendProfile profile, boolean isOnline) {
        if (this.outgoing.remove(profile.id()) != null) {
            OsmosisToast.show(Component.translatable("osmosis.toast.accepted.title"),
                    Component.translatable("osmosis.toast.accepted", profile.name()), profile.id());
        }
        this.incoming.remove(profile.id());
        this.friends.put(profile.id(), profile);
        if (isOnline) {
            this.online.add(profile.id());
        }
        this.changed();
    }

    public void removeFriend(UUID id) {
        this.online.remove(id);
        if (this.friends.remove(id) != null) {
            this.changed();
        }
    }

    public void addIncoming(FriendProfile profile) {
        this.incoming.put(profile.id(), profile);
        OsmosisToast.show(Component.translatable("osmosis.toast.request.title"),
                Component.translatable("osmosis.toast.request", profile.name()), profile.id());
        this.changed();
    }

    public void addOutgoing(FriendProfile profile) {
        this.outgoing.put(profile.id(), profile);
        this.changed();
    }

    public void removeRequest(UUID id) {
        boolean removed = this.incoming.remove(id) != null;
        removed |= this.outgoing.remove(id) != null;
        if (removed) {
            this.changed();
        }
    }

    public void setOnline(UUID id, boolean isOnline) {
        FriendProfile friend = this.friends.get(id);
        if (friend == null) {
            return;
        }
        boolean changed = isOnline ? this.online.add(id) : this.online.remove(id);
        if (changed && isOnline && this.onlineToasts) {
            OsmosisToast.show(Component.literal(friend.name()), Component.translatable("osmosis.toast.online"), id);
        }
        if (changed) {
            this.changed();
        }
    }

    private void changed() {
        for (int i = 0; i < this.listeners.size(); i++) {
            this.listeners.get(i).run();
        }
    }
}
