package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.gui.popup.HostPopup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Small icon button in the pause menu that opens the hosting popup, styled like the vanilla icon buttons next to it.
 */
public final class HostButton extends SpriteIconButton.CenteredIcon {
    private static final Component MESSAGE = Component.translatable("osmosis.host.button");
    private static final WidgetSprites SPRITES =
            new WidgetSprites(Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, "icon/host"));

    public HostButton(Screen parent) {
        super(OsmosisButton.SIZE, OsmosisButton.SIZE, MESSAGE, 15, 15, 0, 0, SPRITES,
                button -> Minecraft.getInstance().gui.setScreen(new HostPopup(parent)), MESSAGE, null, false);
    }
}
