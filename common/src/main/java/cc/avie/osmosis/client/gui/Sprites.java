package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * GUI sprites of the friends screen. Panels are nine-sliced, so they stretch to any size.
 */
public final class Sprites {
    public static final Identifier BAR = sprite("panel/bar");
    public static final Identifier SIDEBAR = sprite("panel/sidebar");
    public static final Identifier CHAT = sprite("panel/chat");
    public static final Identifier POPUP = sprite("panel/popup");
    public static final Identifier SELECTED = sprite("panel/selected");
    public static final Identifier HOVERED = sprite("panel/hovered");
    public static final Identifier BUTTON = sprite("button/normal");
    public static final Identifier BUTTON_HOVERED = sprite("button/hovered");
    public static final Identifier BUTTON_DISABLED = sprite("button/disabled");
    public static final Identifier TAB = sprite("panel/tab");
    public static final Identifier TAB_SELECTED = sprite("panel/tab_selected");
    public static final Identifier TAB_CONTENT = sprite("panel/tab_content");
    public static final Identifier BUBBLE_OUTGOING = sprite("bubble/outgoing");
    public static final Identifier BUBBLE_INCOMING = sprite("bubble/incoming");
    public static final Identifier GROUP = sprite("icon/group");

    private Sprites() {
    }

    public static Identifier sprite(String path) {
        return Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, path);
    }

    public static void draw(GuiGraphicsExtractor graphics, Identifier sprite, int x, int y, int width, int height) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, y, width, height);
    }
}
