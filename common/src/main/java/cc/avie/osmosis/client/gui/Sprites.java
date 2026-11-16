package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI sprites of the social screen. Panels are nine-sliced, so they stretch to any size.
 */
public final class Sprites {
    public static final Identifier PANEL = sprite("social/panel");
    public static final Identifier HEADER = sprite("social/header");
    public static final Identifier ROW = sprite("social/row");
    public static final Identifier ROW_HOVERED = sprite("social/row_hovered");
    public static final Identifier ROW_SELECTED = sprite("social/row_selected");
    public static final Identifier POPUP = sprite("panel/popup");
    public static final Identifier BUTTON = sprite("button/normal");
    public static final Identifier BUTTON_HOVERED = sprite("button/hovered");
    public static final Identifier BUTTON_DISABLED = sprite("button/disabled");
    public static final Identifier BUTTON_ACCENT = sprite("button/accent");
    public static final Identifier BUBBLE_OUTGOING = sprite("bubble/outgoing");
    public static final Identifier BUBBLE_INCOMING = sprite("bubble/incoming");
    public static final Identifier INVITE = sprite("bubble/invite");
    public static final Identifier GROUP = sprite("icon/group");
    public static final Identifier ANNOUNCEMENTS = sprite("icon/announcements");

    private Sprites() {
    }

    public static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, path);
    }

    public static void draw(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }
}
