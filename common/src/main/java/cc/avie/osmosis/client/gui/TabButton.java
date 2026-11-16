package cc.avie.osmosis.client.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A tab at the top of the sidebar, drawn with the panel sprites instead of the vanilla button.
 */
final class TabButton extends AbstractButton {
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_TEXT_COLOR = 0xFFA0A0A0;

    private final BooleanSupplier selected;
    private final Runnable onPress;

    TabButton(int x, int y, int width, int height, Component message, BooleanSupplier selected, Runnable onPress) {
        super(x, y, width, height, message);
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
        Sprites.draw(graphics, selected ? Sprites.TAB_SELECTED : Sprites.TAB, this.getX(), this.getY(), this.getWidth(),
                this.getHeight());
        int color = selected || this.isHovered() ? TEXT_COLOR : INACTIVE_TEXT_COLOR;
        graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2,
                this.getY() + (this.getHeight() - 8) / 2, color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
