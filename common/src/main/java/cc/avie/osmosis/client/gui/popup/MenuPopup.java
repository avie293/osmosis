package cc.avie.osmosis.client.gui.popup;

import cc.avie.osmosis.client.gui.MenuItem;
import cc.avie.osmosis.client.gui.Sprites;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Dropdown with actions for a friend or group, opened from the chat header or by right-clicking the sidebar.
 */
public final class MenuPopup extends OsmosisPopup {
    private static final int ROW_HEIGHT = 16;
    private static final int ICON_SIZE = 9;
    private static final int TEXT_COLOR = 0xFFFFFFFF;

    private final List<MenuItem> items;
    private final int anchorX;
    private final int anchorY;

    public MenuPopup(Screen parent, List<MenuItem> items, int anchorX, int anchorY) {
        super(parent, Component.empty());
        this.items = items;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
    }

    @Override
    protected void init() {
        super.init();
        int width = 0;
        for (MenuItem item : this.items) {
            width = Math.max(width, this.font.width(item.label()));
        }
        this.popupWidth = width + ICON_SIZE + 3 * 6;
        this.popupHeight = this.items.size() * ROW_HEIGHT + 8;
        this.left = Math.min(this.anchorX, this.width - this.popupWidth - 2);
        this.top = Math.min(this.anchorY, this.height - this.popupHeight - 2);
    }

    private int rowAt(double mouseX, double mouseY) {
        if (mouseX < this.left || mouseX >= this.left + this.popupWidth || mouseY < this.top + 4) {
            return -1;
        }
        int row = (int) (mouseY - this.top - 4) / ROW_HEIGHT;
        return row < this.items.size() ? row : -1;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int hovered = this.rowAt(mouseX, mouseY);
        for (int i = 0; i < this.items.size(); i++) {
            MenuItem item = this.items.get(i);
            int y = this.top + 4 + i * ROW_HEIGHT;
            if (i == hovered) {
                Sprites.draw(graphics, Sprites.ROW_HOVERED, this.left + 2, y, this.popupWidth - 4, ROW_HEIGHT);
            }
            Sprites.draw(graphics, item.icon(), this.left + 6, y + (ROW_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, ICON_SIZE);
            graphics.text(this.font, item.label(), this.left + 6 + ICON_SIZE + 6, y + 4, TEXT_COLOR);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int row = this.rowAt(event.x(), event.y());
        this.onClose();
        if (row >= 0) {
            this.items.get(row).action().run();
        }
        return true;
    }
}
