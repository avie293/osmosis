package cc.avie.osmosis.forge;

import cc.avie.osmosis.platform.PlatformHelper;
import net.minecraftforge.fml.ModList;

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
}
