package cc.avie.osmosis.client.host;

import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;

/**
 * The world options the host can change before or while hosting.
 */
public record WorldSettings(GameType gameType, Difficulty difficulty, boolean cheats) {
    public static WorldSettings of(IntegratedServer server) {
        return new WorldSettings(server.getWorldData().getGameType(), server.getWorldData().getDifficulty(),
                server.getWorldData().isAllowCommands());
    }

    public void applyTo(IntegratedServer server) {
        server.setWorldGameType(this.gameType);
        server.setDifficulty(this.difficulty, true);
        server.setWorldAllowCommands(this.cheats);
    }
}
