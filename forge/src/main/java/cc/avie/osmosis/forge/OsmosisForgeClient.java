package cc.avie.osmosis.forge;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

/**
 * Forge client setup of Osmosis. Only called on the physical client, so client classes are never loaded on dedicated
 * servers.
 */
public final class OsmosisForgeClient {
    private OsmosisForgeClient() {
    }

    static void init(FMLJavaModLoadingContext context) {
    }
}
