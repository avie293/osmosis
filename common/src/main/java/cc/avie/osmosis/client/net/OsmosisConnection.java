package cc.avie.osmosis.client.net;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisConfig;
import cc.avie.osmosis.client.chat.ChatCrypto;
import cc.avie.osmosis.client.chat.ChatLog;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.FriendState;
import cc.avie.osmosis.client.gui.ChatScreen;
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
import java.util.List;
import java.util.UUID;
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

    /**
     * @return {@code false} if the friend's key is unknown, so the message could not be encrypted
     */
    public boolean sendMessage(UUID friend, String text) {
        byte[] sealed = this.crypto().encrypt(friend, text);
        if (sealed == null) {
            return false;
        }
        int ciphertextLength = sealed.length - ChatCrypto.NONCE_LENGTH;
        FriendlyByteBuf buf = Packets.create(Packets.SEND_MESSAGE);
        buf.writeUUID(friend);
        buf.writeBytes(sealed, 0, ChatCrypto.NONCE_LENGTH);
        buf.writeVarInt(ciphertextLength);
        buf.writeBytes(sealed, ChatCrypto.NONCE_LENGTH, ciphertextLength);
        this.send(buf);
        return true;
    }

    public void requestHistory(UUID friend, long before) {
        FriendlyByteBuf buf = Packets.create(Packets.REQUEST_HISTORY);
        buf.writeUUID(friend);
        buf.writeLong(before);
        buf.writeByte(Packets.HISTORY_PAGE_SIZE);
        this.send(buf);
    }

    public void markRead(UUID friend) {
        this.sendWithId(Packets.MARK_READ, friend);
    }

    private void sendWithId(int packet, UUID id) {
        FriendlyByteBuf buf = Packets.create(packet);
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

    private void onMessage(UUID partner, ChatMessage message) {
        this.chats.add(partner, message);
        if (message.outgoing()) {
            return;
        }
        if (ChatScreen.isViewing(partner)) {
            this.markRead(partner);
            return;
        }
        this.friends.addUnread(partner);
        FriendProfile friend = this.friends.friend(partner);
        if (friend != null) {
            OsmosisToast.show(Component.literal(friend.name()), ChatScreen.preview(message), partner);
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
            FriendList friends = OsmosisConnection.this.friends;
            ChatLog chats = OsmosisConnection.this.chats;
            switch (buf.readUnsignedByte()) {
                case Packets.CHALLENGE -> {
                    String serverId = buf.readUtf(Packets.MAX_SERVER_ID_LENGTH);
                    Util.ioPool().execute(() -> OsmosisConnection.this.authenticate(serverId));
                }
                case Packets.WELCOME -> {
                    FriendProfile self = Packets.readProfile(buf);
                    int count = buf.readVarInt();
                    List<FriendState> friendList = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        friendList.add(this.readFriend(buf));
                    }
                    List<FriendProfile> incoming = readProfiles(buf);
                    List<FriendProfile> outgoing = readProfiles(buf);
                    OsmosisConnection.this.failedAttempts = 0;
                    OsmosisConnection.this.onMainThread(() -> {
                        chats.clear();
                        friends.reset(self, friendList, incoming, outgoing);
                    });
                }
                case Packets.DISCONNECT -> {
                    this.closeStatus = Packets.disconnectStatus(buf.readUnsignedByte());
                    ctx.close();
                }
                case Packets.FRIEND_ADDED -> {
                    FriendState friend = this.readFriend(buf);
                    OsmosisConnection.this.onMainThread(() -> friends.addFriend(friend.profile(), friend.online()));
                }
                case Packets.FRIEND_REMOVED -> {
                    UUID id = buf.readUUID();
                    OsmosisConnection.this.onMainThread(() -> {
                        friends.removeFriend(id);
                        chats.remove(id);
                    });
                }
                case Packets.REQUEST_RECEIVED -> {
                    FriendProfile profile = Packets.readProfile(buf);
                    OsmosisConnection.this.onMainThread(() -> friends.addIncoming(profile));
                }
                case Packets.REQUEST_SENT -> {
                    FriendProfile profile = Packets.readProfile(buf);
                    OsmosisConnection.this.onMainThread(() -> friends.addOutgoing(profile));
                }
                case Packets.REQUEST_REMOVED -> {
                    UUID id = buf.readUUID();
                    OsmosisConnection.this.onMainThread(() -> friends.removeRequest(id));
                }
                case Packets.PRESENCE -> {
                    UUID id = buf.readUUID();
                    boolean online = buf.readBoolean();
                    OsmosisConnection.this.onMainThread(() -> friends.setOnline(id, online));
                }
                case Packets.ERROR -> {
                    Component message = Component.translatable(Packets.errorKey(buf.readUnsignedByte()));
                    OsmosisConnection.this.onMainThread(() -> OsmosisToast.show(OsmosisToast.TITLE, message, null));
                }
                case Packets.PONG -> {
                }
                case Packets.MESSAGE -> {
                    UUID partner = buf.readUUID();
                    ChatMessage message = this.readMessage(buf, partner);
                    OsmosisConnection.this.onMainThread(() -> OsmosisConnection.this.onMessage(partner, message));
                }
                case Packets.HISTORY -> {
                    UUID partner = buf.readUUID();
                    boolean hasMore = buf.readBoolean();
                    int count = buf.readVarInt();
                    List<ChatMessage> messages = new ArrayList<>(count);
                    for (int i = 0; i < count; i++) {
                        messages.add(this.readMessage(buf, partner));
                    }
                    OsmosisConnection.this.onMainThread(() -> chats.addHistory(partner, messages, hasMore));
                }
                case Packets.FRIEND_KEY -> {
                    UUID id = buf.readUUID();
                    OsmosisConnection.this.crypto().setFriendKey(id, buf.readByteArray(Packets.MAX_KEY_LENGTH));
                }
                default -> throw new DecoderException("Unknown Osmosis packet");
            }
        }

        private FriendState readFriend(FriendlyByteBuf buf) {
            FriendProfile profile = Packets.readProfile(buf);
            boolean online = buf.readBoolean();
            OsmosisConnection.this.crypto().setFriendKey(profile.id(), buf.readByteArray(Packets.MAX_KEY_LENGTH));
            return new FriendState(profile, online, buf.readVarInt());
        }

        /** Decrypts right here on the network thread, so the render thread never waits for it. */
        private ChatMessage readMessage(FriendlyByteBuf buf, UUID partner) {
            boolean outgoing = buf.readBoolean();
            long time = buf.readLong();
            byte[] nonce = new byte[ChatCrypto.NONCE_LENGTH];
            buf.readBytes(nonce);
            byte[] ciphertext = buf.readByteArray(Packets.MAX_CIPHERTEXT_LENGTH);
            String text = OsmosisConnection.this.crypto().decrypt(partner, outgoing, nonce, ciphertext);
            return new ChatMessage(outgoing, time, text);
        }

        private static List<FriendProfile> readProfiles(FriendlyByteBuf buf) {
            int count = buf.readVarInt();
            List<FriendProfile> profiles = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                profiles.add(Packets.readProfile(buf));
            }
            return profiles;
        }
    }
}
