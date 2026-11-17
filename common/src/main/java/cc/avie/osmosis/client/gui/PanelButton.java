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
 * <p>Icon buttons should have an odd size, so the 9x9 icon has the same margin on every side.
 */
public final class PanelButton extends AbstractButton {
    public static final int ICON_SIZE = 9;
    /** A light lavender that matches the panel frames better than plain white. */
    private static final int TEXT_COLOR = 0xFFD2D2EE;
    private static final int HOVERED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int INACTIVE_TEXT_COLOR = 0xFF707088;

    private final @Nullable Identifier icon;
    private final Runnable onPress;
    private Style style = Style.NORMAL;

    /** The background of a button that is not disabled or hovered. */
    public enum Style {
        NORMAL(Sprites.BUTTON),
        /** For the main action of a window and for toggles that are switched on. */
        ACCENT(Sprites.BUTTON_ACCENT),
        POSITIVE(Sprites.BUTTON_POSITIVE),
        NEGATIVE(Sprites.BUTTON_NEGATIVE);

        private final Identifier sprite;

        Style(Identifier sprite) {
            this.sprite = sprite;
        }
    }

    private PanelButton(int x, int y, int width, int height, Component message, @Nullable Identifier icon,
                        Runnable onPress) {
        super(x, y, width, height, message);
        this.icon = icon;
        this.onPress = onPress;
    }

    public static PanelButton text(int x, int y, int width, int height, Component label, Runnable onPress) {
        return new PanelButton(x, y, width, height, label, null, onPress);
    }

    /**
     * @param label shown as the tooltip and read by the narrator
     */
    public static PanelButton icon(int x, int y, int size, Identifier icon, Component label, Runnable onPress) {
        PanelButton button = new PanelButton(x, y, size, size, label, icon, onPress);
        button.setTooltip(Tooltip.create(label));
        return button;
    }

    public PanelButton setStyle(Style style) {
        this.style = style;
        return this;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.onPress.run();
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        // Colored buttons keep their color on hover; only plain ones light up.
        Identifier background = !this.active ? Sprites.BUTTON_DISABLED
                : this.style != Style.NORMAL ? this.style.sprite
                : this.isHoveredOrFocused() ? Sprites.BUTTON_HOVERED : Sprites.BUTTON;
        Sprites.draw(graphics, background, this.getX(), this.getY(), this.getWidth(), this.getHeight());
        if (this.icon != null) {
            Sprites.draw(graphics, this.icon, this.getX() + (this.getWidth() - ICON_SIZE) / 2,
                    this.getY() + (this.getHeight() - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);
        } else {
            int color = !this.active ? INACTIVE_TEXT_COLOR
                    : this.isHoveredOrFocused() ? HOVERED_TEXT_COLOR : TEXT_COLOR;
            graphics.centeredText(Minecraft.getInstance().font, this.getMessage(), this.getX() + this.getWidth() / 2,
                    this.getY() + (this.getHeight() - 8) / 2, color);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}
