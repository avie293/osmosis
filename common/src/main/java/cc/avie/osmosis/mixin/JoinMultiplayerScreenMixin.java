package cc.avie.osmosis.mixin;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.mixin.accessor.ServerSelectionListAccessor;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(JoinMultiplayerScreen.class)
abstract class JoinMultiplayerScreenMixin {
    @Shadow
    protected ServerSelectionList serverSelectionList;

    @Unique
    private int osmosis$seenVersion = -1;

    // Vanilla only rebuilds the list when servers or LAN games change, so hosted worlds need their own trigger.
    @Inject(method = "tick", at = @At("TAIL"))
    private void osmosis$refreshFriendWorlds(CallbackInfo ci) {
        int version = OsmosisClient.friends().version();
        if (version != this.osmosis$seenVersion && this.serverSelectionList != null) {
            this.osmosis$seenVersion = version;
            ((ServerSelectionListAccessor) this.serverSelectionList).osmosis$refreshEntries();
        }
    }
}
