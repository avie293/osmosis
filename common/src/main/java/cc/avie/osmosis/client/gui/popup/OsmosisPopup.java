package cc.avie.osmosis.client.gui.popup;

import cc.avie.osmosis.client.gui.Sprites;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A small window over the friends screen, which stays visible but dimmed behind it.
 */
public abstract class OsmosisPopup extends Screen {
    protected static final int PADDING = 8;
    private static final int DIM_COLOR = 0x88000000;
    private static final int TITLE_COLOR = 0xFFFFFF55;

    protected final Screen parent;
    protected int left;
    protected int top;
    protected int popupWidth;
    protected int popupHeight;

    protected OsmosisPopup(Screen parent, Component title) {
        super(title);
        this.parent = parent;
    }

    /** Centers a popup of the given size and returns the y where content below the title starts. */
    protected int layoutPopup(int width, int height) {
        this.popupWidth = width;
        this.popupHeight = height;
        this.left = (this.width - width) / 2;
        this.top = (this.height - height) / 2;
        return this.top + PADDING + 14;
    }

    @Override
    protected void init() {
        this.parent.init(this.width, this.height);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        this.parent.extractBackground(graphics, -1, -1, a);
        graphics.nextStratum();
        this.parent.extractRenderState(graphics, -1, -1, a);
        graphics.nextStratum();
        graphics.fill(0, 0, this.width, this.height, DIM_COLOR);
        Sprites.draw(graphics, Sprites.POPUP, this.left, this.top, this.popupWidth, this.popupHeight);
        if (!this.title.getString().isEmpty()) {
            graphics.centeredText(this.font, this.title, this.left + this.popupWidth / 2, this.top + PADDING,
                    TITLE_COLOR);
        }
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return this.parent.isPauseScreen();
    }
}
