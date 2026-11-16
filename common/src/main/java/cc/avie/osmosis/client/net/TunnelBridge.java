package cc.avie.osmosis.client.net;

import cc.avie.osmosis.Osmosis;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Connects a Minecraft connection to the relay's tunnel port and copies bytes both ways.
 *
 * <p>The host side links the tunnel to its own LAN server. The guest side opens a local port that its Minecraft
 * connects to as if it were a normal server. Minecraft encrypts and authenticates the connection itself, so this
 * only moves bytes.
 */
final class TunnelBridge {
    private static final byte ROLE_HOST = 0;
    private static final byte ROLE_GUEST = 1;
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    /** How long the guest's local port waits for Minecraft to connect. */
    private static final int ACCEPT_TIMEOUT_SECONDS = 30;
    /**
     * Always IPv4: {@link InetAddress#getLoopbackAddress()} returns ::1 when the JVM prefers IPv6, as on NeoForge,
     * while the game connects to the "127.0.0.1" it is given.
     */
    private static final InetAddress LOCALHOST = InetAddress.ofLiteral("127.0.0.1");

    private TunnelBridge() {
    }

    /**
     * Links a tunnel to the host's LAN server.
     */
    static void host(EventLoopGroup group, InetAddress relay, int tunnelPort, byte[] ticket, int lanPort) {
        connect(group, new InetSocketAddress(relay, tunnelPort), tunnel -> {
            tunnel.writeAndFlush(header(ROLE_HOST, ticket));
            connect(group, new InetSocketAddress(LOCALHOST, lanPort),
                    server -> link(tunnel, server), tunnel::close);
        }, () -> Osmosis.LOGGER.warn("Could not reach the Osmosis tunnel for a joining guest"));
    }

    /**
     * Opens a local port for the guest's Minecraft and links the first connection on it to a tunnel.
     *
     * @return the local port to connect to
     */
    static CompletableFuture<Integer> guest(EventLoopGroup group, InetAddress relay, int tunnelPort, byte[] ticket) {
        CompletableFuture<Integer> port = new CompletableFuture<>();
        new ServerBootstrap()
                .group(group)
                .channel(NioServerSocketChannel.class)
                .childOption(ChannelOption.AUTO_READ, false)
                .childOption(ChannelOption.TCP_NODELAY, true)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel game) {
                        // Only the one connection from this player's game is relayed.
                        game.parent().close();
                        connect(group, new InetSocketAddress(relay, tunnelPort), tunnel -> {
                            tunnel.writeAndFlush(header(ROLE_GUEST, ticket));
                            link(game, tunnel);
                        }, game::close);
                    }
                })
                .bind(LOCALHOST, 0)
                .addListener((ChannelFuture future) -> {
                    if (!future.isSuccess()) {
                        port.completeExceptionally(future.cause());
                        return;
                    }
                    Channel listener = future.channel();
                    listener.eventLoop().schedule(() -> listener.close(), ACCEPT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
                    port.complete(((InetSocketAddress) listener.localAddress()).getPort());
                });
        return port;
    }

    private static ByteBuf header(byte role, byte[] ticket) {
        return Unpooled.buffer(1 + ticket.length).writeByte(role).writeBytes(ticket);
    }

    private static void connect(EventLoopGroup group, InetSocketAddress address, Consumer<Channel> onConnected,
                                Runnable onFailed) {
        new Bootstrap()
                .group(group)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, CONNECT_TIMEOUT_MILLIS)
                .option(ChannelOption.TCP_NODELAY, true)
                .option(ChannelOption.AUTO_READ, false)
                .handler(new ChannelInboundHandlerAdapter())
                .connect(address)
                .addListener((ChannelFuture future) -> {
                    if (future.isSuccess()) {
                        onConnected.accept(future.channel());
                    } else {
                        onFailed.run();
                    }
                });
    }

    /** Copies everything one channel reads to the other, and closes both when either closes. */
    private static void link(Channel a, Channel b) {
        a.pipeline().addLast(new Pipe(b));
        b.pipeline().addLast(new Pipe(a));
        a.config().setAutoRead(true);
        b.config().setAutoRead(true);
    }

    private static final class Pipe extends ChannelInboundHandlerAdapter {
        private final Channel other;

        private Pipe(Channel other) {
            this.other = other;
        }

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) {
            this.other.writeAndFlush(msg);
        }

        @Override
        public void channelInactive(ChannelHandlerContext ctx) {
            if (this.other.isActive()) {
                this.other.writeAndFlush(Unpooled.EMPTY_BUFFER).addListener(ChannelFutureListener.CLOSE);
            }
        }

        @Override
        public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
            ctx.close();
        }
    }
}
