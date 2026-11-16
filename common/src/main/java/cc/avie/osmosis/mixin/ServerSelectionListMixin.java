package cc.avie.osmosis.mixin;

import cc.avie.osmosis.client.gui.FriendWorldEntries;
import java.util.Collection;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(ServerSelectionList.class)
abstract class ServerSelectionListMixin {
    // Friends' hosted worlds go below the saved servers and the LAN section.
    @ModifyArg(method = "refreshEntries", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/multiplayer/ServerSelectionList;replaceEntries(Ljava/util/Collection;)V"))
    private Collection<ServerSelectionList.Entry> osmosis$addFriendWorlds(Collection<ServerSelectionList.Entry> entries) {
        return FriendWorldEntries.append(entries);
    }
}
