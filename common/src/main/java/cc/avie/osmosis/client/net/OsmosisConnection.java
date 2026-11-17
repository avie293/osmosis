package cc.avie.osmosis.client.net;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.OsmosisConfig;
import cc.avie.osmosis.client.chat.Announcement;
import cc.avie.osmosis.client.chat.ChatCrypto;
import cc.avie.osmosis.client.chat.ChatLog;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.FriendState;
import cc.avie.osmosis.client.friend.GroupInfo;
import cc.avie.osmosis.client.friend.GroupState;
import cc.avie.osmosis.client.gui.SocialScreen;
import cc.avie.osmosis.client.gui.OsmosisToast;
import com.mojang.authlib.exceptions.AuthenticationException;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.concurrent.DefaultThreadFactory;
import io.netty.util.concurrent.ScheduledFuture;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Varint21FrameDecoder;
import net.minecraft.network.Varint21LengthFieldPrepender;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * The connection to the Osmosis server. Reconnects on its own with an increasing delay until the server tells it to
 * stop, for example because the mod is outdated.
 */
public final class OsmosisConnection {
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    private static final int PING_INTERVAL_SECONDS = 30;
    private static final int READ_TIMEOUT_SECONDS = 90;
    private static final int MAX_RECONNECT_DELAY_SECONDS = 60;

    private final OsmosisConfig config;
    private final FriendList friends;
    private final ChatLog chats;
    // One daemon thread is plenty for a single socket and never keeps the game from exiting.
    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1,
            new DefaultThreadFactory("Osmosis Network", true), NioIoHandler.newFactory());
    private volatile @Nullable Channel channel;
    private volatile @Nullable ChatCrypto crypto;
    // Only touched on the event loop.
    private int failedAttempts;

    public OsmosisConnection(OsmosisConfig config, FriendList friends, ChatLog chats) {
        this.config = config;
        this.friends = friends;
        this.chats = chats;
    }

    public void start(ChatCrypto crypto) {
        this.crypto = crypto;
        this.group.execute(this::connect);
    }

    private ChatCrypto crypto() {
        ChatCrypto crypto = this.crypto;
        if (crypto == null) {
            throw new IllegalStateException("Osmosis connection is not started");
        }
        return crypto;
    }

    public void sendRequest(String name) {
        FriendlyByteBuf buf = Packets.create(Packets.SEND_REQUEST);
        buf.writeUtf(name, Packets.MAX_NAME_LENGTH);
        this.send(buf);
    }

    public void acceptRequest(UUID id) {
        this.sendWithId(Packets.ACCEPT_REQUEST, id);
    }

    public void declineRequest(UUID id) {
        this.sendWithId(Packets.DECLINE_REQUEST, id);
    }

    public void cancelRequest(UUID id) {
        this.sendWithId(Packets.CANCEL_REQUEST, id);
    }

    public void removeFriend(UUID id) {
        this.sendWithId(Packets.REMOVE_FRIEND, id);
    }

    public void block(UUID id) {
        this.sendWithId(Packets.BLOCK, id);
    }

    public void unblock(UUID id) {
        this.sendWithId(Packets.UNBLOCK, id);
    }

    public void createGroup(String name, Collection<UUID> members) {
        FriendlyByteBuf buf = Packets.create(Packets.CREATE_GROUP);
        buf.writeUtf(name, Packets.MAX_GROUP_NAME_LENGTH);
        buf.writeVarInt(members.size());
        members.forEach(buf::writeUUID);
        this.send(buf);
    }

    public void renameGroup(long group, String name) {
        FriendlyByteBuf buf = Packets.create(Packets.RENAME_GROUP);
        buf.writeLong(group);
        buf.writeUtf(name, Packets.MAX_GROUP_NAME_LENGTH);
        this.send(buf);
    }

    public void addGroupMember(long group, UUID member) {
        this.sendWithGroupAndId(Packets.ADD_GROUP_MEMBER, group, member);
    }

    public void removeGroupMember(long group, UUID member) {
        this.sendWithGroupAndId(Packets.REMOVE_GROUP_MEMBER, group, member);
    }

    public void leaveGroup(long group) {
        this.sendWithGroup(Packets.LEAVE_GROUP, group);
    }

    public void startHosting(UUID worldId, String world) {
        FriendlyByteBuf buf = Packets.create(Packets.START_HOSTING);
        buf.writeUUID(worldId);
        buf.writeUtf(world.length() > Packets.MAX_WORLD_NAME_LENGTH
                ? world.substring(0, Packets.MAX_WORLD_NAME_LENGTH) : world, Packets.MAX_WORLD_NAME_LENGTH);
        this.send(buf);
    }

    public void stopHosting() {
        this.send(Packets.create(Packets.STOP_HOSTING));
    }

    public void invite(UUID player) {
        this.sendWithId(Packets.INVITE, player);
    }

    public void joinWorld(UUID host) {
        this.sendWithId(Packets.JOIN_WORLD, host);
    }

    public void requestWorldMembers(UUID world) {
        this.sendWithId(Packets.REQUEST_WORLD_MEMBERS, world);
    }

    public void addWorldMember(UUID world, UUID player) {
        FriendlyByteBuf buf = Packets.create(Packets.ADD_WORLD_MEMBER);
        buf.writeUUID(world);
        buf.writeUUID(player);
        this.send(buf);
    }

    public void removeWorldMember(UUID world, UUID player) {
        FriendlyByteBuf buf = Packets.create(Packets.REMOVE_WORLD_MEMBER);
        buf.writeUUID(world);
        buf.writeUUID(player);
        this.send(buf);
    }

    /**
     * The relay's tunnel lives on the same host as the control connection, so the address that worked for that one is
     * used again, which avoids trying IPv4 and IPv6 a second time.
     */
    private @Nullable InetAddress relayAddress() {
        Channel channel = this.channel;
        return channel != null && channel.remoteAddress() instanceof InetSocketAddress address
                ? address.getAddress() : null;
    }

    public void openHostTunnel(byte[] ticket, int tunnelPort, int lanPort) {
        InetAddress relay = this.relayAddress();
        if (relay != null) {
            TunnelBridge.host(this.group, relay, tunnelPort, ticket, lanPort);
        }
    }

    /**
     * @return the local port the game should connect to
     */
    public CompletableFuture<Integer> openGuestTunnel(byte[] ticket, int tunnelPort) {
        InetAddress relay = this.relayAddress();
        if (relay == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("Not connected to Osmosis"));
        }
        return TunnelBridge.guest(this.group, relay, tunnelPort, ticket);
    }

    /**
     * Encrypts and sends a message. Group messages are encrypted once for every member.
     *
     * @return {@code false} if a recipient's key is unknown, so the message could not be encrypted
     */
    public boolean sendMessage(ChatTarget target, String text) {
        return this.sendMessage(target, ChatMessage.TEXT, text);
    }

    /**
     * @param kind {@link ChatMessage#TEXT} or {@link ChatMessage#INVITE}
     * @return {@code false} if a recipient's key is unknown, so the message could not be encrypted
     */
    public boolean sendMessage(ChatTarget target, int kind, String text) {
        if (!target.isGroup()) {
            byte[] sealed = this.crypto().encrypt(target.friend(), 0, kind, text);
            if (sealed == null) {
                return false;
            }
            FriendlyByteBuf buf = Packets.create(Packets.SEND_MESSAGE);
            buf.writeUUID(target.friend());
            writeSealed(buf, sealed);
            this.send(buf);
            return true;
        }

        GroupInfo group = this.friends.group(target.group());
        if (group == null) {
            return false;
        }
        FriendlyByteBuf buf = Packets.create(Packets.SEND_GROUP_MESSAGE);
        buf.writeLong(group.id());
        buf.writeVarInt(group.members().size());
        for (FriendProfile member : group.members()) {
            byte[] sealed = this.crypto().encrypt(member.id(), group.id(), kind, text);
            if (sealed == null) {
                buf.release();
                return false;
            }
            buf.writeUUID(member.id());
            writeSealed(buf, sealed);
        }
        this.send(buf);
        return true;
    }

    private static void writeSealed(FriendlyByteBuf buf, byte[] sealed) {
        int ciphertextLength = sealed.length - ChatCrypto.NONCE_LENGTH;
        buf.writeBytes(sealed, 0, ChatCrypto.NONCE_LENGTH);
        buf.writeVarInt(ciphertextLength);
        buf.writeBytes(sealed, ChatCrypto.NONCE_LENGTH, ciphertextLength);
    }

    public void requestHistory(ChatTarget target, long before) {
        int packet = target.isGroup() ? Packets.REQUEST_GROUP_HISTORY : Packets.REQUEST_HISTORY;
        FriendlyByteBuf buf = Packets.create(packet);
        writeTarget(buf, target);
        buf.writeLong(before);
        buf.writeByte(Packets.HISTORY_PAGE_SIZE);
        this.send(buf);
    }

    public void markRead(ChatTarget target) {
        FriendlyByteBuf buf = Packets.create(target.isGroup() ? Packets.MARK_GROUP_READ : Packets.MARK_READ);
        writeTarget(buf, target);
        this.send(buf);
    }

    public void deleteChat(ChatTarget target) {
        FriendlyByteBuf buf = Packets.create(target.isGroup() ? Packets.DELETE_GROUP_CHAT : Packets.DELETE_CHAT);
        writeTarget(buf, target);
        this.send(buf);
    }

    private static void writeTarget(FriendlyByteBuf buf, ChatTarget target) {
        if (target.isGroup()) {
            buf.writeLong(target.group());
        } else {
            buf.writeUUID(target.friend());
        }
    }

    private void sendWithId(int packet, UUID id) {
        FriendlyByteBuf buf = Packets.create(packet);
        buf.writeUUID(id);
        this.send(buf);
    }

    private void sendWithGroup(int packet, long group) {
        FriendlyByteBuf buf = Packets.create(packet);
        buf.writeLong(group);
        this.send(buf);
    }

    private void sendWithGroupAndId(int packet, long group, UUID id) {
        FriendlyByteBuf buf = Packets.create(packet);
        buf.writeLong(group);
        buf.writeUUID(id);
        this.send(buf);
    }

    private void send(ByteBuf buf) {
        Channel channel = this.channel;
        if (channel != null && channel.isActive()) {
            channel.writeAndFlush(buf, channel.voidPromise());
        } else {
            buf.release();
        }
    }

    private void connect() {
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(this.config.serverHost());
        } catch (UnknownHostException e) {
            this.onClosed(ConnectionStatus.UNREACHABLE);
            return;
        }
        this.connectTo(addresses, 0);
    }

    /**
     * Tries the resolved addresses in order. "localhost" resolves to both ::1 and 127.0.0.1, and which comes first
     * depends on the loader's JVM flags, while the server usually listens on only one of them.
     */
    private void connectTo(InetAddress[] addresses, int index) {
        new Bootstrap()
                .group(this.group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .option(ChannelOption.TCP_NODELAY, true)
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel channel) {
                        channel.pipeline().addLast(
                                new ReadTimeoutHandler(READ_TIMEOUT_SECONDS),
                                new Varint21FrameDecoder(null),
                                new Varint21LengthFieldPrepender(),
                                new Handler());
                    }
                })
                .connect(new InetSocketAddress(addresses[index], this.config.serverPort()))
                .addListener((ChannelFuture future) -> {
                    if (future.isSuccess()) {
                        return;
                    }
                    if (index + 1 < addresses.length) {
                        this.connectTo(addresses, index + 1);
                    } else {
                        this.onClosed(ConnectionStatus.UNREACHABLE);
                    }
                });
    }

    private void onClosed(ConnectionStatus status) {
        this.channel = null;
        this.onMainThread(() -> this.friends.setStatus(status));
        if (status.reconnects()) {
            long delay = Math.min(MAX_RECONNECT_DELAY_SECONDS, 2L << Math.min(this.failedAttempts, 5));
            this.failedAttempts++;
            this.group.schedule(this::connect, delay, TimeUnit.SECONDS);
        }
    }

    private void onMessage(ChatTarget target, ChatMessage message) {
        this.chats.add(target, message);
        this.friends.setLastMessage(target, message);
        if (message.outgoing()) {
            return;
        }
        if (SocialScreen.isViewing(target)) {
            this.markRead(target);
            return;
        }
        if (message.kind() == ChatMessage.INVITE) {
            // The invite itself already showed a notification.
            this.friends.addUnread(target);
            return;
        }
        this.friends.addUnread(target);
        FriendProfile sender = this.friends.friend(message.sender());
        if (target.isGroup()) {
            GroupInfo group = this.friends.group(target.group());
            if (group != null) {
                OsmosisToast.show(Component.literal(group.name()), SocialScreen.preview(message, group), null);
            }
        } else if (sender != null) {
            OsmosisToast.show(Component.literal(sender.name()), SocialScreen.preview(message, null), sender.id());
        }
    }

    private void onMainThread(Runnable task) {
        Minecraft.getInstance().execute(task);
    }

    /**
     * Proves account ownership to the server the same way joining a vanilla server does. Blocking.
     */
    private void authenticate(String serverId) {
        Minecraft minecraft = Minecraft.getInstance();
        // Development accounts have no session; only a server in offline mode accepts them.
        if (!minecraft.isOfflineDeveloperMode()) {
            User user = minecraft.getUser();
            try {
                minecraft.services().sessionService().joinServer(user.getProfileId(), user.getAccessToken(), serverId);
            } catch (AuthenticationException e) {
                // Still answer the challenge, the server then tells us that the login failed.
                Osmosis.LOGGER.warn("Could not verify the Minecraft account with Mojang: {}", e.getMessage());
            }
        }
        this.send(Packets.create(Packets.AUTHENTICATE));
    }

    private final class Handler extends SimpleChannelInboundHandler<ByteBuf> {
        private @Nullable ScheduledFuture<?> pingTask;
        private @Nullable ConnectionStatus closeStatus;

        @Override
        public void channelActive(ChannelHandlerContext ctx) {
            OsmosisConnection.this.channel = ctx.channel();
            User user = Minecraft.getInstance().getUser();
            FriendlyByteBuf hello = Packets.create(Packets.HELLO);
            hello.writeVarInt(Packets.PROTOCOL_VERSION);
            hello.writeUUID(user.getProfileId());
            hello.writeUtf(user.getName(), Packets.MAX_NAME_LENGTH);
            hello.writeByteArray(OsmosisConnection.this.crypto().publicKey());
            ctx.writeAndFlush(hello, ctx.voidPromise());
            this.pingTask = ctx.executor().scheduleAtFixedRate(
                    () -> OsmosisConnection.this.send(Packets.create(Packets.PING)),
                    PING_INTERVAL_SECONDS, PING_INTERVAL_SECONDS, TimeUnit.SECONDS);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (this.pingTask != null) {
                this.pingTask.cancel(false);
            }
            OsmosisConnection.this.onClosed(this.closeStatus != null ? this.closeStatus : ConnectionStatus.UNREACHABLE);
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            Osmosis.LOGGER.debug("Osmosis connection error", cause);
            ctx.close();
        }

        @Override
        protected void channelRead0(ChannelHandlerContext ctx, ByteBuf msg) {
            FriendlyByteBuf buf = new FriendlyByteBuf(msg);
            OsmosisConnection connection = OsmosisConnection.this;
            FriendList friends = connection.friends;
            ChatLog chats = connection.chats;
            switch (buf.readUnsignedByte()) {
                case Packets.CHALLENGE -> {
                    String serverId = buf.readUtf(Packets.MAX_SERVER_ID_LENGTH);
                    Util.ioPool().execute(() -> connection.authenticate(serverId));
                }
                case Packets.WELCOME -> {
                    FriendProfile self = Packets.readProfile(buf);
                    int count = buf.readVarInt();
                    List<FriendState> friendList = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        friendList.add(this.readFriend(buf));
                    }
                    List<FriendProfile> incoming = Packets.readProfiles(buf);
                    List<FriendProfile> outgoing = Packets.readProfiles(buf);
                    List<FriendProfile> blocked = Packets.readProfiles(buf);
                    int groupCount = buf.readVarInt();
                    List<GroupState> groups = new ArrayList<>(groupCount);
                    for (int i = 0; i < groupCount; i++) {
                        groups.add(this.readGroup(buf));
                    }
                    connection.failedAttempts = 0;
                    connection.onMainThread(() -> {
                        chats.clear();
                        friends.reset(self, friendList, incoming, outgoing, blocked, groups);
                    });
                }
                case Packets.DISCONNECT -> {
                    this.closeStatus = Packets.disconnectStatus(buf.readUnsignedByte());
                    ctx.close();
                }
                case Packets.FRIEND_ADDED -> {
                    FriendState friend = this.readFriend(buf);
                    connection.onMainThread(() -> friends.addFriend(friend));
                }
                case Packets.FRIEND_REMOVED -> {
                    UUID id = buf.readUUID();
                    connection.onMainThread(() -> {
                        friends.removeFriend(id);
                        chats.remove(ChatTarget.direct(id));
                    });
                }
                case Packets.REQUEST_RECEIVED -> {
                    FriendProfile profile = Packets.readProfile(buf);
                    connection.onMainThread(() -> friends.addIncoming(profile));
                }
                case Packets.REQUEST_SENT -> {
                    FriendProfile profile = Packets.readProfile(buf);
                    connection.onMainThread(() -> friends.addOutgoing(profile));
                }
                case Packets.REQUEST_REMOVED -> {
                    UUID id = buf.readUUID();
                    connection.onMainThread(() -> friends.removeRequest(id));
                }
                case Packets.PRESENCE -> {
                    UUID id = buf.readUUID();
                    boolean online = buf.readBoolean();
                    long lastSeen = buf.readLong();
                    connection.onMainThread(() -> friends.setOnline(id, online, lastSeen));
                }
                case Packets.ERROR -> {
                    Component message = Component.translatable(Packets.errorKey(buf.readUnsignedByte()));
                    connection.onMainThread(() -> OsmosisToast.show(OsmosisToast.TITLE, message, null));
                }
                case Packets.PONG -> {
                }
                case Packets.MESSAGE -> {
                    UUID partner = buf.readUUID();
                    ChatMessage message = this.readDirectMessage(buf, partner);
                    connection.onMainThread(() -> connection.onMessage(ChatTarget.direct(partner), message));
                }
                case Packets.HISTORY -> {
                    UUID partner = buf.readUUID();
                    boolean hasMore = buf.readBoolean();
                    int count = buf.readVarInt();
                    List<ChatMessage> messages = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        messages.add(this.readDirectMessage(buf, partner));
                    }
                    connection.onMainThread(() -> chats.addHistory(ChatTarget.direct(partner), messages, hasMore));
                }
                case Packets.FRIEND_KEY -> {
                    UUID id = buf.readUUID();
                    connection.crypto().setFriendKey(id, buf.readByteArray(Packets.MAX_KEY_LENGTH));
                }
                case Packets.BLOCKED -> {
                    FriendProfile profile = Packets.readProfile(buf);
                    connection.onMainThread(() -> friends.addBlocked(profile));
                }
                case Packets.UNBLOCKED -> {
                    UUID id = buf.readUUID();
                    connection.onMainThread(() -> friends.removeBlocked(id));
                }
                case Packets.GROUP_UPDATED -> {
                    GroupState group = this.readGroup(buf);
                    connection.onMainThread(() -> friends.putGroup(group));
                }
                case Packets.GROUP_REMOVED -> {
                    long id = buf.readLong();
                    connection.onMainThread(() -> {
                        friends.removeGroup(id);
                        chats.remove(ChatTarget.group(id));
                    });
                }
                case Packets.GROUP_MESSAGE -> {
                    long id = buf.readLong();
                    ChatMessage message = this.readGroupMessage(buf, id);
                    connection.onMainThread(() -> connection.onMessage(ChatTarget.group(id), message));
                }
                case Packets.GROUP_HISTORY -> {
                    long id = buf.readLong();
                    boolean hasMore = buf.readBoolean();
                    int count = buf.readVarInt();
                    List<ChatMessage> messages = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        messages.add(this.readGroupMessage(buf, id));
                    }
                    connection.onMainThread(() -> chats.addHistory(ChatTarget.group(id), messages, hasMore));
                }
                case Packets.HOSTING -> {
                    FriendProfile host = Packets.readProfile(buf);
                    UUID worldId = buf.readUUID();
                    String world = buf.readUtf(Packets.MAX_WORLD_NAME_LENGTH);
                    boolean invited = buf.readBoolean();
                    connection.onMainThread(() -> friends.setHostedWorld(host, worldId, world, invited));
                }
                case Packets.JOIN_TICKET -> {
                    UUID host = buf.readUUID();
                    byte[] ticket = readTicket(buf);
                    int port = buf.readUnsignedShort();
                    connection.onMainThread(() -> OsmosisClient.host().onJoinTicket(host, ticket, port));
                }
                case Packets.GUEST_JOINING -> {
                    FriendProfile guest = Packets.readProfile(buf);
                    byte[] ticket = readTicket(buf);
                    int port = buf.readUnsignedShort();
                    connection.onMainThread(() -> OsmosisClient.host().onGuestJoining(guest, ticket, port));
                }
                case Packets.ANNOUNCEMENTS -> {
                    int count = buf.readVarInt();
                    List<Announcement> announcements = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        announcements.add(new Announcement(buf.readLong(), buf.readUtf(Packets.MAX_ANNOUNCEMENT_LENGTH)));
                    }
                    connection.onMainThread(() -> friends.addAnnouncements(announcements));
                }
                case Packets.WORLD_MEMBERS -> {
                    UUID world = buf.readUUID();
                    List<FriendProfile> members = Packets.readProfiles(buf);
                    connection.onMainThread(() -> friends.setWorldMembers(world, members));
                }
                default -> throw new DecoderException("Unknown Osmosis packet");
            }
        }

        private static byte[] readTicket(FriendlyByteBuf buf) {
            byte[] ticket = new byte[16];
            buf.readBytes(ticket);
            return ticket;
        }

        private FriendState readFriend(FriendlyByteBuf buf) {
            FriendProfile profile = Packets.readProfile(buf);
            boolean online = buf.readBoolean();
            OsmosisConnection.this.crypto().setFriendKey(profile.id(), buf.readByteArray(Packets.MAX_KEY_LENGTH));
            int unread = buf.readVarInt();
            long lastSeen = buf.readLong();
            ChatMessage lastMessage = buf.readBoolean() ? this.readDirectMessage(buf, profile.id()) : null;
            return new FriendState(profile, online, unread, lastSeen, lastMessage);
        }

        /** Reads a group, storing the member keys on the way. */
        private GroupState readGroup(FriendlyByteBuf buf) {
            long id = buf.readLong();
            String name = buf.readUtf(Packets.MAX_GROUP_NAME_LENGTH);
            UUID owner = buf.readUUID();
            int count = buf.readVarInt();
            List<FriendProfile> members = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                FriendProfile member = Packets.readProfile(buf);
                OsmosisConnection.this.crypto().setFriendKey(member.id(), buf.readByteArray(Packets.MAX_KEY_LENGTH));
                members.add(member);
            }
            int unread = buf.readVarInt();
            ChatMessage lastMessage = buf.readBoolean() ? this.readGroupMessage(buf, id) : null;
            return new GroupState(new GroupInfo(id, name, owner, List.copyOf(members)), unread, lastMessage);
        }

        /** Decrypts right here on the network thread, so the render thread never waits for it. */
        private ChatMessage readDirectMessage(FriendlyByteBuf buf, UUID partner) {
            boolean outgoing = buf.readBoolean();
            long time = buf.readLong();
            byte[] nonce = new byte[ChatCrypto.NONCE_LENGTH];
            buf.readBytes(nonce);
            byte[] ciphertext = buf.readByteArray(Packets.MAX_CIPHERTEXT_LENGTH);
            UUID self = Minecraft.getInstance().getUser().getProfileId();
            UUID sender = outgoing ? self : partner;
            UUID recipient = outgoing ? partner : self;
            return message(sender, outgoing, time, OsmosisConnection.this.crypto()
                    .decrypt(sender, recipient, 0, nonce, ciphertext));
        }

        private ChatMessage readGroupMessage(FriendlyByteBuf buf, long group) {
            UUID sender = buf.readUUID();
            long time = buf.readLong();
            byte[] nonce = new byte[ChatCrypto.NONCE_LENGTH];
            buf.readBytes(nonce);
            byte[] ciphertext = buf.readByteArray(Packets.MAX_CIPHERTEXT_LENGTH);
            UUID self = Minecraft.getInstance().getUser().getProfileId();
            return message(sender, sender.equals(self), time, OsmosisConnection.this.crypto()
                    .decrypt(sender, self, group, nonce, ciphertext));
        }

        private static ChatMessage message(UUID sender, boolean outgoing, long time,
                                           ChatCrypto.@Nullable Plaintext plaintext) {
            return plaintext == null ? new ChatMessage(sender, outgoing, ChatMessage.TEXT, time, null)
                    : new ChatMessage(sender, outgoing, plaintext.kind(), time, plaintext.text());
        }
    }
}
