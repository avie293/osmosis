package cc.avie.osmosis.client.gui.popup;

import java.util.function.Consumer;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Asks for a single line of text, like a new group name.
 */
public final class TextInputPopup extends OsmosisPopup {
    private static final int WIDTH = 220;

    private final String initialValue;
    private final int maxLength;
    private final Consumer<String> onDone;
    private @Nullable EditBox input;

    public TextInputPopup(Screen parent, Component title, String initialValue, int maxLength, Consumer<String> onDone) {
        super(parent, title);
        this.initialValue = initialValue;
        this.maxLength = maxLength;
        this.onDone = onDone;
    }

    @Override
    protected void init() {
        super.init();
        int y = this.layoutPopup(WIDTH, PADDING + 14 + 20 + 6 + 20 + PADDING);
        this.input = new EditBox(this.font, this.left + PADDING, y, WIDTH - 2 * PADDING, 20, this.title);
        this.input.setMaxLength(this.maxLength);
        this.input.setValue(this.initialValue);
        this.addRenderableWidget(this.input);
        int buttonWidth = (WIDTH - 2 * PADDING - 4) / 2;
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
                .bounds(this.left + PADDING, y + 26, buttonWidth, 20)
                .build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> this.submit())
                .bounds(this.left + PADDING + buttonWidth + 4, y + 26, buttonWidth, 20)
                .build());
        this.setInitialFocus(this.input);
    }

    private void submit() {
        if (this.input == null) {
            return;
        }
        String value = this.input.getValue().strip();
        if (!value.isEmpty()) {
            this.onClose();
            this.onDone.accept(value);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation()) {
            this.submit();
            return true;
        }
        return super.keyPressed(event);
    }
}
