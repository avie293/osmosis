package cc.avie.osmosis.client.host;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.HostedWorld;
import cc.avie.osmosis.client.gui.OsmosisToast;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import org.jspecify.annotations.Nullable;

/**
 * Hosting the player's singleplayer world for friends, and joining the worlds friends host.
 *
 * <p>Hosting opens the world to LAN like the vanilla button does, then lets the Osmosis relay bring invited players
 * in through a tunnel. Only touched on the render thread.
 */
public final class WorldHost {
    private @Nullable String world;
    private int lanPort;

    public boolean isHosting() {
        return this.world != null;
    }

    public @Nullable String world() {
        return this.world;
    }

    public boolean canHost() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    public void start() {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null || this.world != null) {
            return;
        }
        if (server.isPublished()) {
            this.lanPort = server.getPort();
        } else {
            int port = HttpUtil.getAvailablePort();
            if (!server.publishServer(MinecraftServer.MultiplayerScope.LAN, false, port)) {
                OsmosisToast.show(OsmosisToast.TITLE, Component.translatable("osmosis.host.failed"), null);
                return;
            }
            this.lanPort = port;
        }
        this.world = server.getWorldData().getLevelName();
        OsmosisClient.connection().startHosting(this.world);
    }

    public void stop() {
        if (this.world != null) {
            this.world = null;
            OsmosisClient.connection().stopHosting();
        }
    }

    /** Ends hosting once the player left the world. */
    public void tick() {
        if (this.world != null && Minecraft.getInstance().getSingleplayerServer() == null) {
            this.stop();
        }
    }

    public void onGuestJoining(FriendProfile guest, byte[] ticket, int tunnelPort) {
        if (this.world == null) {
            return;
        }
        OsmosisClient.connection().openHostTunnel(ticket, tunnelPort, this.lanPort);
        OsmosisToast.show(Component.translatable("osmosis.host.joining", guest.name()), null, guest.id());
    }

    public void join(HostedWorld world) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            OsmosisToast.show(OsmosisToast.TITLE, Component.translatable("osmosis.host.leave_first"), null);
            return;
        }
        OsmosisClient.connection().joinWorld(world.host().id());
    }

    public void onJoinTicket(UUID host, byte[] ticket, int tunnelPort) {
        HostedWorld world = OsmosisClient.friends().hostedWorld(host);
        String name = world != null ? world.world() : "Osmosis";
        OsmosisClient.connection().openGuestTunnel(ticket, tunnelPort).whenCompleteAsync((port, error) -> {
            if (error != null) {
                Osmosis.LOGGER.warn("Could not open the Osmosis tunnel", error);
                OsmosisToast.show(OsmosisToast.TITLE, Component.translatable("osmosis.host.join_failed"), null);
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            Screen parent = minecraft.gui.screen() != null ? minecraft.gui.screen() : new TitleScreen();
            String address = "127.0.0.1:" + port;
            ConnectScreen.startConnecting(parent, minecraft, ServerAddress.parseString(address),
                    new ServerData(name, address, ServerData.Type.OTHER), false, null);
        }, Minecraft.getInstance());
    }
}
