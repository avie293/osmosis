package cc.avie.osmosis.client.gui;

import java.util.function.BooleanSupplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;

/**
 * A tab shown as plain text, highlighted in the accent color while selected.
 */
final class TextTab extends AbstractButton {
    private final BooleanSupplier selected;
    private final Runnable onPress;

    TextTab(int x, int y, int height, Component message, BooleanSupplier selected, Runnable onPress) {
        super(x, y, Minecraft.getInstance().font.width(message), height, message);
        this.selected = selected;
        this.onPress = onPress;
    }

    @Override
    public void setMessage(Component message) {
        super.setMessage(message);
        this.setWidth(Minecraft.getInstance().font.width(message));
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        int color = this.selected.getAsBoolean() ? SocialScreen.ACCENT_COLOR
                : this.isHoveredOrFocused() ? SocialScreen.TEXT_COLOR : SocialScreen.MUTED_COLOR;
        graphics.text(Minecraft.getInstance().font, this.getMessage(), this.getX(), this.getY() + (this.getHeight() - 8) / 2,
                color);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
