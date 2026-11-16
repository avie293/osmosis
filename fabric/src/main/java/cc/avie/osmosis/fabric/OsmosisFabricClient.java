package cc.avie.osmosis.fabric;

import cc.avie.osmosis.client.OsmosisClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;

/**
 * Fabric client entrypoint of Osmosis.
 */
public final class OsmosisFabricClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        OsmosisClient.init();
        KeyMappingHelper.registerKeyMapping(OsmosisClient.OPEN_KEY);
        ClientTickEvents.END_CLIENT_TICK.register(minecraft -> OsmosisClient.tick());
    }
}
