package cc.avie.osmosis.fabric.mixin;

import cc.avie.osmosis.client.gui.HostButton;
import cc.avie.osmosis.client.gui.OsmosisButton;
import cc.avie.osmosis.client.gui.WorldSelectScreen;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds the hosting button to the title screen's icon row, after the friends button, only on Fabric for now.
 */
@Mixin(TitleScreen.class)
abstract class TitleScreen_hostMixin extends Screen {
    @Shadow
    private @Nullable FriendsButton friends;

    protected TitleScreen_hostMixin(Component title) {
        super(title);
    }

    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I"), index = 1)
    private int osmosis$countHostButton(int numberOfButtons) {
        return numberOfButtons + 1;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void osmosis$addHostButton(CallbackInfo ci) {
        if (this.friends != null) {
            HostButton button = new HostButton(() -> new WorldSelectScreen(this));
            // Fifth in the row: friends, language, accessibility and the friends list come first.
            button.setPosition(this.friends.getX() + 4 * (OsmosisButton.SIZE + 4), this.friends.getY());
            this.addRenderableWidget(button);
        }
    }
}
