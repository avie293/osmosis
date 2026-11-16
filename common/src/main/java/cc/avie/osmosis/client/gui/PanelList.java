package cc.avie.osmosis.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;

/**
 * A scrolling list drawn on top of an Osmosis panel, without the vanilla list background and separators.
 */
public abstract class PanelList<E extends ContainerObjectSelectionList.Entry<E>>
        extends ContainerObjectSelectionList<E> {
    protected PanelList(Minecraft minecraft, int x, int y, int width, int height, int itemHeight) {
        super(minecraft, width, height, y, itemHeight);
        this.setX(x);
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    @Override
    protected void extractListBackground(GuiGraphicsExtractor graphics) {
    }

    @Override
    protected void extractListSeparators(GuiGraphicsExtractor graphics) {
    }
}
