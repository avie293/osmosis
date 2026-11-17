package cc.avie.osmosis.client.host;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.HostedWorld;
import cc.avie.osmosis.client.gui.OsmosisToast;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.HttpUtil;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * Hosting the player's singleplayer world for friends, and joining the worlds friends host.
 *
 * <p>Hosting opens the world to LAN like the vanilla button does, then lets the Osmosis relay bring invited players
 * in through a tunnel. Only touched on the render thread.
 */
public final class WorldHost {
    /**
     * Set by the development run configurations only. Development accounts have no Mojang session, so a hosted world
     * that verifies players would turn every one of them away.
     */
    private static final boolean OFFLINE_HOSTING = Boolean.getBoolean("osmosis.offlineHosting");

    private static final String WORLD_ID_FILE = "osmosis.id";

    private @Nullable String world;
    private @Nullable UUID worldId;
    private int lanPort;
    /** Settings of a world being loaded to be hosted once it is ready. */
    private @Nullable WorldSettings pendingSettings;

    public boolean isHosting() {
        return this.world != null;
    }

    public @Nullable String world() {
        return this.world;
    }

    public boolean canHost() {
        return Minecraft.getInstance().getSingleplayerServer() != null;
    }

    /**
     * @return the id of the world being hosted, or {@code null} when not hosting
     */
    public @Nullable UUID worldId() {
        return this.worldId;
    }

    /**
     * Opens a singleplayer world and hosts it once it has loaded.
     */
    public void hostAfterLoading(String levelId, WorldSettings settings, Runnable onCancel) {
        this.pendingSettings = settings;
        Minecraft.getInstance().createWorldOpenFlows().openWorld(levelId, () -> {
            this.pendingSettings = null;
            onCancel.run();
        });
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
        if (OFFLINE_HOSTING) {
            server.setUsesAuthentication(false);
        }
        this.worldId = worldId(server.getWorldPath(LevelResource.ROOT));
        this.world = server.getWorldData().getLevelName();
        OsmosisClient.connection().startHosting(this.worldId, this.world);
    }

    /**
     * Reads the id stored in the world folder, or creates it. Invites are remembered per id, so they survive renaming
     * the world, and copies of a world share them.
     */
    public static UUID worldId(Path worldFolder) {
        Path file = worldFolder.resolve(WORLD_ID_FILE);
        try {
            if (Files.exists(file)) {
                return UUID.fromString(Files.readString(file, StandardCharsets.UTF_8).strip());
            }
        } catch (IOException | IllegalArgumentException e) {
            Osmosis.LOGGER.warn("Could not read {}, creating a new world id", file, e);
        }
        UUID id = UUID.randomUUID();
        try {
            Files.writeString(file, id.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Osmosis.LOGGER.warn("Could not save the world id to {}", file, e);
        }
        return id;
    }

    /**
     * Invites players and posts an invite message in the chat it was sent from.
     */
    public void invite(ChatTarget chat, Collection<FriendProfile> players) {
        if (this.world == null || this.worldId == null) {
            return;
        }
        FriendProfile self = OsmosisClient.friends().self();
        for (FriendProfile player : players) {
            if (self == null || !player.id().equals(self.id())) {
                OsmosisClient.connection().invite(player.id());
            }
        }
        OsmosisClient.connection().sendMessage(chat, ChatMessage.INVITE, ChatMessage.invite(this.worldId, this.world));
    }

    public void stop() {
        if (this.world != null) {
            this.world = null;
            this.worldId = null;
            OsmosisClient.connection().stopHosting();
        }
    }

    /** Starts hosting a world once it finished loading, and ends hosting once the player left it. */
    public void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        IntegratedServer server = minecraft.getSingleplayerServer();
        if (this.pendingSettings != null && server != null && minecraft.player != null) {
            this.pendingSettings.applyTo(server);
            this.pendingSettings = null;
            this.start();
        }
        if (this.world != null && server == null) {
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

    /**
     * Joins a friend's world, leaving the current one first like the pause menu's quit button does.
     */
    public void join(HostedWorld world) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            minecraft.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE);
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
