package cc.avie.osmosis.mixin.accessor;

import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ServerSelectionList.class)
public interface ServerSelectionListAccessor {
    @Invoker("refreshEntries")
    void osmosis$refreshEntries();
}
