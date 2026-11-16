package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.chat.Announcement;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * Announcements from the server operator, oldest first, shown as cards.
 */
final class AnnouncementList extends PanelList<AnnouncementList.Card> {
    private static final int PADDING = 6;
    private static final int LINE_HEIGHT = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("MMM dd, HH:mm", Locale.ROOT);

    AnnouncementList(Minecraft minecraft, int x, int y, int width, int height) {
        super(minecraft, x, y, width, height, 20);
    }

    void rebuild(List<Announcement> announcements) {
        this.clearEntries();
        int textWidth = Math.min(this.getRowWidth(), 320) - 2 * PADDING;
        for (Announcement announcement : announcements) {
            Card card = new Card(announcement, textWidth);
            this.addEntry(card, card.height());
        }
        this.setScrollAmount(this.maxScrollAmount());
    }

    final class Card extends ContainerObjectSelectionList.Entry<Card> {
        private final String date;
        private final List<FormattedCharSequence> lines;
        private final int width;

        private Card(Announcement announcement, int textWidth) {
            Font font = AnnouncementList.this.minecraft.font;
            this.date = DATE.format(Instant.ofEpochMilli(announcement.time()).atZone(ZoneId.systemDefault()));
            this.lines = font.split(Component.literal(announcement.text()), textWidth);
            this.width = textWidth + 2 * PADDING;
        }

        /** The card plus the list's 2 pixels of padding on each side and 4 pixels of spacing. */
        private int height() {
            return this.cardHeight() + 8;
        }

        private int cardHeight() {
            return 2 * PADDING + (this.lines.size() + 1) * LINE_HEIGHT;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = AnnouncementList.this.minecraft.font;
            int x = this.getContentX() + 2;
            int y = this.getContentY() + 2;
            Sprites.draw(graphics, Sprites.BUBBLE_INCOMING, x, y, this.width, this.cardHeight());
            graphics.text(font, this.date, x + PADDING, y + PADDING, SocialScreen.MUTED_COLOR);
            for (int i = 0; i < this.lines.size(); i++) {
                graphics.text(font, this.lines.get(i), x + PADDING, y + PADDING + (i + 1) * LINE_HEIGHT,
                        SocialScreen.TEXT_COLOR);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }
}
