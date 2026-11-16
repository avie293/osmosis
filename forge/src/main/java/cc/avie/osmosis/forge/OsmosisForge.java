package cc.avie.osmosis.forge;

import cc.avie.osmosis.Osmosis;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;

/**
 * Forge entrypoint of Osmosis for both sides.
 */
@Mod(Osmosis.MOD_ID)
public final class OsmosisForge {
    public OsmosisForge() {
        if (FMLEnvironment.dist.isClient()) {
            OsmosisForgeClient.init();
        }
    }
}
