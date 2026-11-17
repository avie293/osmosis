package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Small icon button for hosting, styled like the vanilla icon buttons next to it.
 */
public final class HostButton extends SpriteIconButton.CenteredIcon {
    private static final Component MESSAGE = Component.translatable("osmosis.host.button");
    private static final WidgetSprites SPRITES =
            new WidgetSprites(Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, "icon/host"));

    /**
     * @param screen the screen to open, created when the button is pressed
     */
    public HostButton(Supplier<Screen> screen) {
        super(OsmosisButton.SIZE, OsmosisButton.SIZE, MESSAGE, 15, 15, 0, 0, SPRITES,
                button -> Minecraft.getInstance().gui.setScreen(screen.get()), MESSAGE, null, false);
    }
}
