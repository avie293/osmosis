package cc.avie.osmosis.client.gui.popup;

import cc.avie.osmosis.client.gui.PanelButton;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Asks before something that cannot be undone.
 */
public final class ConfirmPopup extends OsmosisPopup {
    private static final int WIDTH = 220;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final Component message;
    private final Component confirmLabel;
    private final Runnable onConfirm;
    private List<FormattedCharSequence> lines = List.of();
    private int textTop;

    public ConfirmPopup(Screen parent, Component title, Component message, Component confirmLabel, Runnable onConfirm) {
        super(parent, title);
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        super.init();
        this.lines = this.font.split(this.message, WIDTH - 2 * PADDING);
        int height = PADDING + 14 + this.lines.size() * 10 + 8 + 20 + PADDING;
        this.textTop = this.layoutPopup(WIDTH, height);
        int buttonY = this.textTop + this.lines.size() * 10 + 8;
        int buttonWidth = (WIDTH - 2 * PADDING - 4) / 2;
        this.addRenderableWidget(PanelButton.text(this.left + PADDING, buttonY, buttonWidth, 20,
                CommonComponents.GUI_CANCEL, this::onClose));
        this.addRenderableWidget(PanelButton.text(this.left + PADDING + buttonWidth + 4, buttonY, buttonWidth, 20,
                this.confirmLabel, () -> {
                    this.onClose();
                    this.onConfirm.run();
                }));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        for (int i = 0; i < this.lines.size(); i++) {
            graphics.centeredText(this.font, this.lines.get(i), this.left + WIDTH / 2, this.textTop + i * 10,
                    TEXT_COLOR);
        }
    }
}
