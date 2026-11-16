package cc.avie.osmosis.mixin;

import cc.avie.osmosis.client.gui.OsmosisButton;
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

@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
    @Shadow
    private @Nullable FriendsButton friends;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    // The small icon buttons are centered for a fixed count, so it has to include ours.
    @ModifyArg(method = "init", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/TitleScreen;getHorizontalPosition(III)I"), index = 1)
    private int osmosis$countButton(int numberOfButtons) {
        return numberOfButtons + 1;
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void osmosis$addButton(CallbackInfo ci) {
        if (this.friends != null) {
            OsmosisButton button = new OsmosisButton(this);
            // Last in the row: friends, language and accessibility come first, 4 pixels apart.
            button.setPosition(this.friends.getX() + 3 * (OsmosisButton.SIZE + 4), this.friends.getY());
            this.addRenderableWidget(button);
        }
    }
}
