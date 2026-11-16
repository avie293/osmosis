package cc.avie.osmosis.forge;

import cc.avie.osmosis.platform.PlatformHelper;
import java.nio.file.Path;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;

/**
 * Forge implementation of {@link PlatformHelper}.
 */
public final class ForgePlatformHelper implements PlatformHelper {
    @Override
    public String getPlatformName() {
        return "Forge";
    }

    @Override
    public boolean isModLoaded(String modId) {
        return ModList.isLoaded(modId);
    }

    @Override
    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
