package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Small icon button that opens the friend list, with a badge for requests and unread messages like the vanilla
 * friends button.
 */
public final class OsmosisButton extends SpriteIconButton.CenteredIcon {
    public static final int SIZE = 20;
    private static final Component MESSAGE = Component.translatable("osmosis.button");
    private static final WidgetSprites SPRITES =
            new WidgetSprites(Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, "icon/friends"));
    private static final Identifier[] BADGE_SPRITES = {
            Identifier.withDefaultNamespace("notification/1"),
            Identifier.withDefaultNamespace("notification/2"),
            Identifier.withDefaultNamespace("notification/3"),
            Identifier.withDefaultNamespace("notification/4"),
            Identifier.withDefaultNamespace("notification/5"),
            Identifier.withDefaultNamespace("notification/more")
    };

    public OsmosisButton(Screen parent) {
        super(SIZE, SIZE, MESSAGE, 15, 15, 0, 0, SPRITES,
                button -> Minecraft.getInstance().gui.setScreen(new OsmosisScreen(parent)), MESSAGE, null, false);
    }

    @Override
    public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractContents(graphics, mouseX, mouseY, a);
        int pending = OsmosisClient.friends().incoming().size() + OsmosisClient.friends().totalUnread();
        if (pending > 0) {
            Identifier badge = BADGE_SPRITES[Math.min(pending, BADGE_SPRITES.length) - 1];
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, badge, this.getX() + this.getWidth() - 5, this.getY() - 3,
                    8, 8, this.alpha);
        }
    }
}
