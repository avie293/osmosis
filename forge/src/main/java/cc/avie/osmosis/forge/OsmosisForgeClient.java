package cc.avie.osmosis.forge;

import cc.avie.osmosis.client.OsmosisClient;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.event.TickEvent;

/**
 * Forge client setup of Osmosis. Only called on the physical client, so client classes are never loaded on dedicated
 * servers.
 */
public final class OsmosisForgeClient {
    private OsmosisForgeClient() {
    }

    static void init() {
        OsmosisClient.init();
        RegisterKeyMappingsEvent.BUS.addListener(event -> event.register(OsmosisClient.OPEN_KEY));
        TickEvent.ClientTickEvent.Post.BUS.addListener(event -> OsmosisClient.tick());
    }
}
