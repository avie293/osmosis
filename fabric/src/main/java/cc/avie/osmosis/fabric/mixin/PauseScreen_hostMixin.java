package cc.avie.osmosis.fabric.mixin;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.gui.HostButton;
import net.minecraft.client.gui.layouts.LayoutElement;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Adds the hosting button to the pause menu's icon row, only on Fabric for now.
 */
@Mixin(PauseScreen.class)
abstract class PauseScreen_hostMixin extends Screen {
    protected PauseScreen_hostMixin(Component title) {
        super(title);
    }

    @ModifyArg(method = "createPauseMenu", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/layouts/GridLayout$RowHelper;addChild(Lnet/minecraft/client/gui/layouts/LayoutElement;ILnet/minecraft/client/gui/layouts/LayoutSettings;)Lnet/minecraft/client/gui/layouts/LayoutElement;"),
            index = 0)
    private LayoutElement osmosis$addHostButton(LayoutElement element) {
        // Only a singleplayer world can be hosted.
        if (element instanceof LinearLayout iconRow && OsmosisClient.host().canHost()) {
            iconRow.addChild(new HostButton(this));
        }
        return element;
    }
}
