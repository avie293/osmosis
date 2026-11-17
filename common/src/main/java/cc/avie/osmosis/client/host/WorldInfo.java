package cc.avie.osmosis.client.host;

import java.nio.file.Path;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelSummary;

/**
 * A singleplayer world the player may host, either the one that is open right now or one from the saves folder.
 *
 * @param loaded whether this is the world that is open right now
 */
public record WorldInfo(String levelId, String name, String version, UUID worldId, boolean loaded,
                        WorldSettings settings) {
    public static WorldInfo of(IntegratedServer server) {
        Path folder = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        return new WorldInfo(folder.getFileName().toString(), server.getWorldData().getLevelName(),
                SharedConstants.getCurrentVersion().name(), WorldHost.worldId(folder), true, WorldSettings.of(server));
    }

    public static WorldInfo of(LevelSummary summary) {
        Path folder = Minecraft.getInstance().getLevelSource().getLevelPath(summary.getLevelId());
        WorldSettings settings = new WorldSettings(summary.getSettings().gameType(),
                summary.getSettings().difficultySettings().difficulty(), summary.getSettings().allowCommands());
        return new WorldInfo(summary.getLevelId(), summary.getLevelName(), summary.levelVersion().minecraftVersionName(),
                WorldHost.worldId(folder), false, settings);
    }
}
