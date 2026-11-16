package cc.avie.osmosis.client;

import cc.avie.osmosis.client.chat.ChatCrypto;
import cc.avie.osmosis.client.chat.ChatLog;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.HostedWorld;
import cc.avie.osmosis.client.gui.SocialScreen;
import cc.avie.osmosis.client.host.WorldHost;
import cc.avie.osmosis.client.net.OsmosisConnection;
import cc.avie.osmosis.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Client side entry point. Each loader calls {@link #init()} once, registers {@link #OPEN_KEY} and {@link #JOIN_KEY}
 * and calls {@link #tick()} at the end of every client tick.
 */
public final class OsmosisClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.osmosis.open", InputConstants.KEY_O,
            KeyMapping.Category.MULTIPLAYER);
    public static final KeyMapping JOIN_KEY = new KeyMapping("key.osmosis.join", InputConstants.KEY_J,
            KeyMapping.Category.MULTIPLAYER);

    private static final FriendList FRIENDS = new FriendList();
    private static final ChatLog CHATS = new ChatLog();
    private static final WorldHost HOST = new WorldHost();
    private static @Nullable OsmosisConnection connection;
    private static boolean started;

    private OsmosisClient() {
    }

    public static void init() {
        OsmosisConfig config = OsmosisConfig.load(Services.PLATFORM.getConfigDir());
        FRIENDS.setOnlineToasts(config.onlineToasts());
        connection = new OsmosisConnection(config, FRIENDS, CHATS);
    }

    public static void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        // Waiting for the first tick means the game has finished starting and the session is available.
        if (!started) {
            started = true;
            UUID account = minecraft.getUser().getProfileId();
            // One key per account, outside the config folder so it is never shipped with a modpack.
            Path keyFile = minecraft.gameDirectory.toPath().resolve("osmosis/keys/" + account + ".key");
            connection().start(ChatCrypto.loadOrCreate(keyFile, account));
        }
        HOST.tick();
        while (OPEN_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new SocialScreen(null));
            }
        }
        while (JOIN_KEY.consumeClick()) {
            HostedWorld invite = FRIENDS.lastInvite();
            if (invite != null) {
                HOST.join(invite);
            }
        }
    }

    public static FriendList friends() {
        return FRIENDS;
    }

    public static ChatLog chats() {
        return CHATS;
    }

    public static WorldHost host() {
        return HOST;
    }

    public static OsmosisConnection connection() {
        if (connection == null) {
            throw new IllegalStateException("Osmosis client is not initialized");
        }
        return connection;
    }
}
