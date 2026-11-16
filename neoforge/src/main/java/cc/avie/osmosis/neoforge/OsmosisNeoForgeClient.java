package cc.avie.osmosis.neoforge;

import cc.avie.osmosis.Osmosis;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

/**
 * NeoForge client entrypoint of Osmosis. Not loaded on dedicated servers.
 */
@Mod(value = Osmosis.MOD_ID, dist = Dist.CLIENT)
public final class OsmosisNeoForgeClient {
    public OsmosisNeoForgeClient(IEventBus modBus, ModContainer container) {
    }
}
