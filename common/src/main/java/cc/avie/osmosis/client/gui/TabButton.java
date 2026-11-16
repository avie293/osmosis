package cc.avie.osmosis.client.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A tab at the top of the sidebar. The selected one is taller and reaches over the frame of the panel below it, so
 * it looks attached to that panel; the others sit on top of the frame.
 */
final class TabButton extends AbstractButton {
    /** How far the selected tab reaches into the panel below. */
    static final int OVERLAP = 2;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_TEXT_COLOR = 0xFFA0A0A0;

    private final BooleanSupplier selected;
    private final Runnable onPress;

    /**
     * @param height the height down to the top of the panel below
     */
    TabButton(int x, int y, int width, int height, Component message, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height + OVERLAP, message);
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        boolean selected = this.selected.getAsBoolean();
        int tabHeight = this.getHeight() - OVERLAP;
        // Unselected tabs are lowered and end on the panel's frame.
        int top = selected ? this.getY() : this.getY() + OVERLAP;
        int height = selected ? this.getHeight() : tabHeight - OVERLAP;
        Sprites.draw(graphics, selected ? Sprites.TAB_SELECTED : Sprites.TAB, this.getX(), top, this.getWidth(),
                height);
        int textTop = selected ? this.getY() : top;
        int textHeight = selected ? tabHeight : height;
        int color = selected || this.isHovered() ? TEXT_COLOR : INACTIVE_TEXT_COLOR;
        graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2,
                textTop + (textHeight - 8) / 2, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
