package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.chat.ChatLog;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.friend.FriendProfile;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

final class ChatMessageList extends ContainerObjectSelectionList<ChatMessageList.Entry> {
    private static final int MAX_ROW_WIDTH = 310;
    private static final int LINE_HEIGHT = 10;
    private static final int TEXT_ROW_HEIGHT = 20;
    private static final int OWN_NAME_COLOR = 0xFF55FFFF;
    private static final int FRIEND_NAME_COLOR = 0xFFFFFF55;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FriendProfile friend;
    private final Runnable loadOlder;

    ChatMessageList(Minecraft minecraft, int width, int height, int y, FriendProfile friend, Runnable loadOlder) {
        super(minecraft, width, height, y, TEXT_ROW_HEIGHT);
        this.friend = friend;
        this.loadOlder = loadOlder;
    }

    @Override
    public int getRowWidth() {
        return Math.min(MAX_ROW_WIDTH, this.width - 20);
    }

    void rebuild(ChatLog.Conversation conversation) {
        // Measured from the bottom, so new messages keep the view at the end and older ones do not move it.
        double fromBottom = this.maxScrollAmount() - this.scrollAmount();
        this.clearEntries();
        if (!conversation.isLoaded()) {
            this.addEntry(new TextEntry(Component.translatable("osmosis.chat.loading")));
        } else if (conversation.messages().isEmpty()) {
            this.addEntry(new TextEntry(Component.translatable("osmosis.chat.empty")));
        } else {
            if (conversation.hasMore()) {
                this.addEntry(new LoadOlderEntry(conversation.isLoading()), 24);
            }
            int textWidth = this.getRowWidth() - 8;
            for (ChatMessage message : conversation.messages()) {
                MessageEntry entry = new MessageEntry(message, textWidth);
                this.addEntry(entry, entry.height());
            }
        }
        this.setScrollAmount(this.maxScrollAmount() - fromBottom);
    }

    private static String formatTime(long time) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        boolean today = dateTime.toLocalDate().equals(LocalDate.now());
        return (today ? TIME : DATE_TIME).format(dateTime);
    }

    abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }

    private final class TextEntry extends Entry {
        private final Component text;

        private TextEntry(Component text) {
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.centeredText(ChatMessageList.this.minecraft.font, this.text,
                    this.getContentX() + this.getContentWidth() / 2,
                    this.getContentY() + (this.getContentHeight() - 9) / 2, MUTED_COLOR);
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

    private final class LoadOlderEntry extends Entry {
        private final Button button;

        private LoadOlderEntry(boolean loading) {
            this.button = Button.builder(Component.translatable("osmosis.chat.load_older"),
                    button -> ChatMessageList.this.loadOlder.run()).width(160).build();
            this.button.active = !loading;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.button.setPosition(this.getContentX() + (this.getContentWidth() - this.button.getWidth()) / 2,
                    this.getContentY() + (this.getContentHeight() - this.button.getHeight()) / 2);
            this.button.extractRenderState(graphics, mouseX, mouseY, a);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of(this.button);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of(this.button);
        }
    }

    private final class MessageEntry extends Entry {
        private final ChatMessage message;
        private final Component sender;
        private final Component time;
        private final List<FormattedCharSequence> lines;

        private MessageEntry(ChatMessage message, int textWidth) {
            this.message = message;
            this.sender = message.outgoing() ? Component.translatable("osmosis.chat.you")
                    : Component.literal(ChatMessageList.this.friend.name());
            this.time = Component.literal(formatTime(message.time()));
            Component text = message.text() != null ? Component.literal(message.text())
                    : Component.translatable("osmosis.chat.undecryptable").withStyle(style -> style.withItalic(true));
            this.lines = ChatMessageList.this.minecraft.font.split(text, textWidth);
        }

        private int height() {
            return 14 + this.lines.size() * LINE_HEIGHT;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ChatMessageList.this.minecraft.font;
            int x = this.getContentX() + 4;
            int y = this.getContentY() + 2;
            graphics.text(font, this.sender, x, y, this.message.outgoing() ? OWN_NAME_COLOR : FRIEND_NAME_COLOR);
            graphics.text(font, this.time, this.getContentRight() - 4 - font.width(this.time), y, MUTED_COLOR);
            int color = this.message.text() != null ? TEXT_COLOR : MUTED_COLOR;
            for (int i = 0; i < this.lines.size(); i++) {
                graphics.text(font, this.lines.get(i), x, y + 11 + i * LINE_HEIGHT, color);
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
