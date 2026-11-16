package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatLog;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import cc.avie.osmosis.client.friend.HostedWorld;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

/**
 * Messages of one conversation as bubbles: outgoing on the right in a lighter bubble, incoming on the left in a
 * darker one.
 */
final class MessageList extends PanelList<MessageList.Entry> {
    private static final int LINE_HEIGHT = 10;
    private static final int BUBBLE_PADDING = 5;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int SENDER_COLOR = 0xFFFFFF55;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final Runnable loadOlder;

    MessageList(Minecraft minecraft, int x, int y, int width, int height, Runnable loadOlder) {
        super(minecraft, x, y, width, height, 20);
        this.loadOlder = loadOlder;
    }

    void rebuild(ChatLog.Conversation conversation, @Nullable GroupInfo group) {
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
            int maxBubbleWidth = this.getRowWidth() * 2 / 3;
            for (ChatMessage message : conversation.messages()) {
                if (message.kind() == ChatMessage.INVITE && message.inviteWorldId() != null) {
                    InviteEntry entry = new InviteEntry(message, senderName(group, message.sender()));
                    this.addEntry(entry, entry.height());
                    continue;
                }
                Component sender = group != null && !message.outgoing() ? senderName(group, message.sender()) : null;
                BubbleEntry entry = new BubbleEntry(message, sender, maxBubbleWidth);
                this.addEntry(entry, entry.height());
            }
        }
        this.setScrollAmount(this.maxScrollAmount() - fromBottom);
    }

    private static Component senderName(@Nullable GroupInfo group, UUID sender) {
        if (group != null) {
            for (FriendProfile member : group.members()) {
                if (member.id().equals(sender)) {
                    return Component.literal(member.name());
                }
            }
        }
        FriendProfile friend = OsmosisClient.friends().friend(sender);
        return friend != null ? Component.literal(friend.name()) : Component.translatable("osmosis.chat.former_member");
    }

    private static String formatTime(long time) {
        ZonedDateTime dateTime = Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault());
        boolean today = dateTime.toLocalDate().equals(LocalDate.now());
        return (today ? TIME : DATE_TIME).format(dateTime);
    }

    abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private final class TextEntry extends Entry {
        private final Component text;

        private TextEntry(Component text) {
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.centeredText(MessageList.this.minecraft.font, this.text,
                    this.getContentX() + this.getContentWidth() / 2,
                    this.getContentY() + (this.getContentHeight() - 8) / 2, MUTED_COLOR);
        }
    }

    private final class LoadOlderEntry extends Entry {
        private final PanelButton button;

        private LoadOlderEntry(boolean loading) {
            this.button = PanelButton.text(0, 0, 140, 20, Component.translatable("osmosis.chat.load_older"),
                    MessageList.this.loadOlder);
            this.button.active = !loading;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            this.button.setPosition(this.getContentX() + (this.getContentWidth() - this.button.getWidth()) / 2,
                    this.getContentY());
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

    /** An invite to a hosted world, with a button to join it while the host still has it open. */
    private final class InviteEntry extends Entry {
        private static final int WIDTH = 150;
        private static final int BUTTON_HEIGHT = 16;

        private final ChatMessage message;
        private final Component title;
        private final Component world;
        private final Component time;
        private final @Nullable PanelButton joinButton;

        private InviteEntry(ChatMessage message, Component sender) {
            this.message = message;
            this.title = message.outgoing() ? Component.translatable("osmosis.chat.invite.sent")
                    : Component.translatable("osmosis.chat.invite.received", sender);
            this.world = Component.literal(message.inviteWorldName());
            this.time = Component.literal(formatTime(message.time()));
            if (message.outgoing()) {
                this.joinButton = null;
            } else {
                HostedWorld hosted = OsmosisClient.friends().hostedWorld(message.sender());
                boolean joinable = hosted != null && hosted.invited() && hosted.id().equals(message.inviteWorldId());
                this.joinButton = PanelButton.text(0, 0, 50, BUTTON_HEIGHT, Component.translatable("osmosis.chat.join"),
                        () -> {
                            if (hosted != null) {
                                OsmosisClient.host().join(hosted);
                            }
                        });
                this.joinButton.active = joinable;
            }
        }

        private int bubbleHeight() {
            return 2 * BUBBLE_PADDING + 2 * LINE_HEIGHT - 2 + (this.joinButton != null ? BUTTON_HEIGHT + 4 : 0);
        }

        private int height() {
            return this.bubbleHeight() + 6;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = MessageList.this.minecraft.font;
            boolean outgoing = this.message.outgoing();
            int x = outgoing ? this.getContentRight() - WIDTH - 2 : this.getContentX() + 2;
            int y = this.getContentY();
            Sprites.draw(graphics, Sprites.INVITE, x, y, WIDTH, this.bubbleHeight());
            int textX = x + BUBBLE_PADDING;
            int textY = y + BUBBLE_PADDING;
            graphics.text(font, this.title, textX, textY, SENDER_COLOR);
            graphics.text(font, this.time, x + WIDTH - BUBBLE_PADDING - font.width(this.time), textY, MUTED_COLOR);
            graphics.text(font, font.split(this.world, WIDTH - 2 * BUBBLE_PADDING).getFirst(), textX,
                    textY + LINE_HEIGHT, TEXT_COLOR);
            if (this.joinButton != null) {
                this.joinButton.setPosition(x + WIDTH - BUBBLE_PADDING - this.joinButton.getWidth(),
                        textY + 2 * LINE_HEIGHT + 2);
                this.joinButton.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.joinButton != null ? List.of(this.joinButton) : List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.joinButton != null ? List.of(this.joinButton) : List.of();
        }
    }

    private final class BubbleEntry extends Entry {
        private final boolean outgoing;
        private final @Nullable Component sender;
        private final Component time;
        private final List<FormattedCharSequence> lines;
        private final int textColor;
        private final int bubbleWidth;

        private BubbleEntry(ChatMessage message, @Nullable Component sender, int maxWidth) {
            Font font = MessageList.this.minecraft.font;
            this.outgoing = message.outgoing();
            this.sender = sender;
            this.time = Component.literal(formatTime(message.time()));
            Component text = message.text() != null ? Component.literal(message.text())
                    : Component.translatable("osmosis.chat.undecryptable").withStyle(style -> style.withItalic(true));
            this.textColor = message.text() != null ? TEXT_COLOR : MUTED_COLOR;
            this.lines = font.split(text, maxWidth - 2 * BUBBLE_PADDING);
            int width = font.width(this.time);
            for (FormattedCharSequence line : this.lines) {
                width = Math.max(width, font.width(line));
            }
            if (sender != null) {
                width = Math.max(width, font.width(sender));
            }
            this.bubbleWidth = width + 2 * BUBBLE_PADDING;
        }

        private int bubbleHeight() {
            int lines = this.lines.size() + 1 + (this.sender != null ? 1 : 0);
            return lines * LINE_HEIGHT + 2 * BUBBLE_PADDING - 2;
        }

        /** The bubble plus the 2 pixels of padding on each side that every row gets, and 2 pixels of spacing. */
        private int height() {
            return this.bubbleHeight() + 6;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = MessageList.this.minecraft.font;
            int x = this.outgoing ? this.getContentRight() - this.bubbleWidth - 2 : this.getContentX() + 2;
            int y = this.getContentY();
            Sprites.draw(graphics, this.outgoing ? Sprites.BUBBLE_OUTGOING : Sprites.BUBBLE_INCOMING, x, y,
                    this.bubbleWidth, this.bubbleHeight());
            int textX = x + BUBBLE_PADDING;
            int textY = y + BUBBLE_PADDING;
            if (this.sender != null) {
                graphics.text(font, this.sender, textX, textY, SENDER_COLOR);
                textY += LINE_HEIGHT;
            }
            for (FormattedCharSequence line : this.lines) {
                graphics.text(font, line, textX, textY, this.textColor);
                textY += LINE_HEIGHT;
            }
            graphics.text(font, this.time, x + this.bubbleWidth - BUBBLE_PADDING - font.width(this.time), textY,
                    MUTED_COLOR);
        }
    }
}
