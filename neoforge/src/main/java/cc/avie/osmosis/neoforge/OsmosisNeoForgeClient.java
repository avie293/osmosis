package cc.avie.osmosis.neoforge;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * NeoForge client entrypoint of Osmosis. Not loaded on dedicated servers.
 */
@Mod(value = Osmosis.MOD_ID, dist = Dist.CLIENT)
public final class OsmosisNeoForgeClient {
    public OsmosisNeoForgeClient(IEventBus modBus) {
        OsmosisClient.init();
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(OsmosisClient.OPEN_KEY));
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> OsmosisClient.tick());
    }
}
