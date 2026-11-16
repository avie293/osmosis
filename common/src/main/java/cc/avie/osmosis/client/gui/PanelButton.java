package cc.avie.osmosis.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * A button in the style of the Osmosis panels, showing either a label or an icon.
 *
 * <p>Size and icon size should both be odd or both be even, so the icon has the same margin on every side.
 */
public final class PanelButton extends AbstractButton {
    public static final int ICON_SIZE = 9;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_TEXT_COLOR = 0xFF808080;

    private final @Nullable Identifier icon;
    private final int iconSize;
    private final Runnable onPress;

    private PanelButton(int x, int y, int width, int height, Component message, @Nullable Identifier icon,
                        int iconSize, Runnable onPress) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.iconSize = iconSize;
        this.onPress = onPress;
    }

    public static PanelButton text(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new PanelButton(x, y, width, height, label, null, 0, onPress);
    }

    /**
     * @param label shown as the tooltip and read by the narrator
     */
    public static PanelButton icon(int x, int y, int size, Identifier icon, Component label, Runnable onPress) {
        return icon(x, y, size, icon, ICON_SIZE, label, onPress);
    }

    /**
     * @param label shown as the tooltip and read by the narrator
     */
    public static PanelButton icon(int x, int y, int size, Identifier icon, int iconSize, Component label,
                                   Runnable onPress) {
        PanelButton button = new PanelButton(x, y, size, size, label, icon, iconSize, onPress);
        button.setTooltip(Tooltip.create(label));
        return button;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        Identifier background = !this.active ? Sprites.BUTTON_DISABLED
                : this.isHoveredOrFocused() ? Sprites.BUTTON_HOVERED : Sprites.BUTTON;
        Sprites.draw(graphics, background, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        if (this.icon != null) {
            Sprites.draw(graphics, this.icon, this.getX() + (this.getWidth() - this.iconSize) / 2,
                    this.getY() + (this.getHeight() - this.iconSize) / 2, this.iconSize, this.iconSize);
        } else {
            graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2, this.active ? TEXT_COLOR : INACTIVE_TEXT_COLOR);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
