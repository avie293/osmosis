package cc.avie.osmosis.client;

import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.gui.FriendsScreen;
import cc.avie.osmosis.client.net.OsmosisConnection;
import cc.avie.osmosis.platform.Services;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Client side entry point. Each loader calls {@link #init()} once, registers {@link #OPEN_KEY} and calls
 * {@link #tick()} at the end of every client tick.
 */
public final class OsmosisClient {
    public static final KeyMapping OPEN_KEY = new KeyMapping("key.osmosis.open", InputConstants.KEY_O,
            KeyMapping.Category.MULTIPLAYER);

    private static final FriendList FRIENDS = new FriendList();
    private static @Nullable OsmosisConnection connection;
    private static boolean started;

    private OsmosisClient() {
    }

    public static void init() {
        OsmosisConfig config = OsmosisConfig.load(Services.PLATFORM.getConfigDir());
        FRIENDS.setOnlineToasts(config.onlineToasts());
        connection = new OsmosisConnection(config, FRIENDS);
    }

    public static void tick() {
        // Waiting for the first tick means the game has finished starting and the session is available.
        if (!started) {
            started = true;
            connection().start();
        }
        Minecraft minecraft = Minecraft.getInstance();
        while (OPEN_KEY.consumeClick()) {
            if (minecraft.gui.screen() == null) {
                minecraft.gui.setScreen(new FriendsScreen(null));
            }
        }
    }

    public static FriendList friends() {
        return FRIENDS;
    }

    public static OsmosisConnection connection() {
        if (connection == null) {
            throw new IllegalStateException("Osmosis client is not initialized");
        }
        return connection;
    }
}
