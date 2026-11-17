package cc.avie.osmosis.client.friend;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.Announcement;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.gui.OsmosisToast;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The client's view of its friends, requests, blocked players and groups, mirrored from the server.
 *
 * <p>Only touched on the render thread. The connection hands every update over to it.
 */
public final class FriendList {
    private final Map<UUID, FriendProfile> friends = new LinkedHashMap<>();
    private final Map<UUID, FriendProfile> incoming = new LinkedHashMap<>();
    private final Map<UUID, FriendProfile> outgoing = new LinkedHashMap<>();
    private final Map<UUID, FriendProfile> blocked = new LinkedHashMap<>();
    private final Map<Long, GroupInfo> groups = new LinkedHashMap<>();
    private final Map<UUID, HostedWorld> hostedWorlds = new HashMap<>();
    private final Map<UUID, List<FriendProfile>> worldMembers = new HashMap<>();
    private @Nullable UUID lastInviteHost;
    private final Set<UUID> online = new HashSet<>();
    private final Map<UUID, Long> lastSeen = new HashMap<>();
    private final Map<ChatTarget, ChatMessage> lastMessages = new HashMap<>();
    private final List<Announcement> announcements = new ArrayList<>();
    private boolean announcementsLoaded;
    private final Map<ChatTarget, Integer> unread = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private ConnectionStatus status = ConnectionStatus.CONNECTING;
    private @Nullable FriendProfile self;
    private boolean onlineToasts = true;
    private int version;

    /**
     * @return a number that changes whenever anything in this list changes
     */
    public int version() {
        return this.version;
    }

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

    public Collection<FriendProfile> blocked() {
        return Collections.unmodifiableCollection(this.blocked.values());
    }

    public Collection<GroupInfo> groups() {
        return Collections.unmodifiableCollection(this.groups.values());
    }

    public @Nullable FriendProfile friend(UUID id) {
        return this.friends.get(id);
    }

    public @Nullable GroupInfo group(long id) {
        return this.groups.get(id);
    }

    public boolean isOnline(UUID id) {
        return this.online.contains(id);
    }

    /**
     * @return when this friend was last online, in milliseconds since the epoch, 0 if unknown
     */
    public long lastSeen(UUID id) {
        return this.lastSeen.getOrDefault(id, 0L);
    }

    /**
     * @return the newest message of a chat, for previews, or {@code null} if there is none
     */
    public @Nullable ChatMessage lastMessage(ChatTarget target) {
        return this.lastMessages.get(target);
    }

    public void setLastMessage(ChatTarget target, @Nullable ChatMessage message) {
        if (message == null) {
            this.lastMessages.remove(target);
        } else {
            this.lastMessages.put(target, message);
        }
        this.changed();
    }

    public List<Announcement> announcements() {
        return Collections.unmodifiableList(this.announcements);
    }

    /** The first batch after login is the stored history; later ones are new and get a notification. */
    public void addAnnouncements(List<Announcement> announcements) {
        if (this.announcementsLoaded) {
            for (Announcement announcement : announcements) {
                OsmosisToast.show(Component.translatable("osmosis.announcements"), Component.literal(
                        announcement.text().length() > 40 ? announcement.text().substring(0, 40) + "..."
                                : announcement.text()), null);
            }
        }
        this.announcements.addAll(announcements);
        this.announcementsLoaded = true;
        this.changed();
    }

    /**
     * @return the world this player is hosting, or {@code null} if they are not hosting or we do not know about it
     */
    public @Nullable HostedWorld hostedWorld(UUID host) {
        return this.hostedWorlds.get(host);
    }

    /**
     * @return the players invited to one of our worlds, or {@code null} until the server answered
     */
    public @Nullable List<FriendProfile> worldMembers(UUID world) {
        return this.worldMembers.get(world);
    }

    public void setWorldMembers(UUID world, List<FriendProfile> members) {
        this.worldMembers.put(world, List.copyOf(members));
        this.changed();
    }

    public Collection<HostedWorld> hostedWorlds() {
        return Collections.unmodifiableCollection(this.hostedWorlds.values());
    }

    /**
     * @return the world we were most recently told we can join, if it is still hosted
     */
    public @Nullable HostedWorld lastInvite() {
        HostedWorld world = this.lastInviteHost != null ? this.hostedWorlds.get(this.lastInviteHost) : null;
        return world != null && world.invited() ? world : null;
    }

    /**
     * Records that a player started hosting, was invited, or stopped hosting when {@code world} is empty.
     */
    public void setHostedWorld(FriendProfile host, UUID worldId, String world, boolean invited) {
        HostedWorld previous = this.hostedWorlds.get(host.id());
        if (world.isEmpty()) {
            this.hostedWorlds.remove(host.id());
        } else {
            this.hostedWorlds.put(host.id(), new HostedWorld(host, worldId, world, invited));
            if (invited && (previous == null || !previous.invited())) {
                // Without a previous entry the host just started a world we were invited to before.
                String title = previous == null ? "osmosis.toast.hosting.title" : "osmosis.toast.invite.title";
                this.lastInviteHost = host.id();
                OsmosisToast.show(Component.translatable(title, host.name()), Component.translatable(
                        "osmosis.toast.invite", world, OsmosisClient.JOIN_KEY.getTranslatedKeyMessage()), host.id());
            }
        }
        this.changed();
    }

    public int unread(ChatTarget target) {
        return this.unread.getOrDefault(target, 0);
    }

    public int totalUnread() {
        int total = 0;
        for (int count : this.unread.values()) {
            total += count;
        }
        return total;
    }

    /**
     * @return why a request to this name makes no sense, or {@code null} if it can be sent
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
        if (containsName(this.blocked, name)) {
            return Component.translatable("osmosis.error.player_blocked");
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

    public void reset(FriendProfile self, List<FriendState> friends, List<FriendProfile> incoming,
                      List<FriendProfile> outgoing, List<FriendProfile> blocked, List<GroupState> groups) {
        this.self = self;
        this.hostedWorlds.clear();
        this.lastInviteHost = null;
        this.lastSeen.clear();
        this.lastMessages.clear();
        this.announcements.clear();
        this.announcementsLoaded = false;
        this.friends.clear();
        this.online.clear();
        this.unread.clear();
        for (FriendState friend : friends) {
            UUID id = friend.profile().id();
            this.friends.put(id, friend.profile());
            if (friend.online()) {
                this.online.add(id);
            }
            this.lastSeen.put(id, friend.lastSeen());
            this.setUnread(ChatTarget.direct(id), friend.unread());
            if (friend.lastMessage() != null) {
                this.lastMessages.put(ChatTarget.direct(id), friend.lastMessage());
            }
        }
        replace(this.incoming, incoming);
        replace(this.outgoing, outgoing);
        replace(this.blocked, blocked);
        this.groups.clear();
        for (GroupState state : groups) {
            this.groups.put(state.group().id(), state.group());
            this.setUnread(ChatTarget.group(state.group().id()), state.unread());
            if (state.lastMessage() != null) {
                this.lastMessages.put(ChatTarget.group(state.group().id()), state.lastMessage());
            }
        }
        this.status = ConnectionStatus.CONNECTED;
        this.changed();
    }

    private static void replace(Map<UUID, FriendProfile> target, List<FriendProfile> profiles) {
        target.clear();
        for (FriendProfile profile : profiles) {
            target.put(profile.id(), profile);
        }
    }

    private void setUnread(ChatTarget target, int count) {
        if (count > 0) {
            this.unread.put(target, count);
        } else {
            this.unread.remove(target);
        }
    }

    public void addUnread(ChatTarget target) {
        this.unread.merge(target, 1, Integer::sum);
        this.changed();
    }

    public void clearUnread(ChatTarget target) {
        if (this.unread.remove(target) != null) {
            this.changed();
        }
    }

    public void addFriend(FriendState friend) {
        FriendProfile profile = friend.profile();
        boolean isOnline = friend.online();
        this.lastSeen.put(profile.id(), friend.lastSeen());
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
        this.lastMessages.remove(ChatTarget.direct(id));
        this.unread.remove(ChatTarget.direct(id));
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

    public void addBlocked(FriendProfile profile) {
        this.blocked.put(profile.id(), profile);
        this.changed();
    }

    public void removeBlocked(UUID id) {
        if (this.blocked.remove(id) != null) {
            this.changed();
        }
    }

    public void putGroup(GroupState state) {
        GroupInfo group = state.group();
        this.groups.put(group.id(), group);
        this.setUnread(ChatTarget.group(group.id()), state.unread());
        if (state.lastMessage() != null) {
            this.lastMessages.put(ChatTarget.group(group.id()), state.lastMessage());
        }
        this.changed();
    }

    public void removeGroup(long id) {
        this.unread.remove(ChatTarget.group(id));
        this.lastMessages.remove(ChatTarget.group(id));
        if (this.groups.remove(id) != null) {
            this.changed();
        }
    }

    public void setOnline(UUID id, boolean isOnline, long lastSeen) {
        FriendProfile friend = this.friends.get(id);
        if (friend == null) {
            return;
        }
        this.lastSeen.put(id, lastSeen);
        boolean changed = isOnline ? this.online.add(id) : this.online.remove(id);
        if (changed && isOnline && this.onlineToasts) {
            OsmosisToast.show(Component.literal(friend.name()), Component.translatable("osmosis.toast.online"), id);
        }
        if (changed) {
            this.changed();
        }
    }

    private void changed() {
        this.version++;
        for (int i = 0; i < this.listeners.size(); i++) {
            this.listeners.get(i).run();
        }
    }
}
