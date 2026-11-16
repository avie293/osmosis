package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.chat.Announcement;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * The chat tab's list: announcements pinned on top, then every friend and group, most recent activity first.
 */
final class ConversationList extends PanelList<ConversationList.Row> {
    private static final int ROW_HEIGHT = 32;
    private static final int ICON_SIZE = 24;
    private static final int BADGE_COLOR = 0xFFE03030;

    private final SocialScreen screen;

    ConversationList(Minecraft minecraft, int x, int y, int width, int height, SocialScreen screen) {
        super(minecraft, x, y, width, height, ROW_HEIGHT);
        this.screen = screen;
    }

    @Override
    public int getRowWidth() {
        return this.width - 6;
    }

    void rebuild(FriendList friends, String filter) {
        double scroll = this.scrollAmount();
        this.clearEntries();
        List<Announcement> announcements = friends.announcements();
        if (!announcements.isEmpty() && matches(Component.translatable("osmosis.announcements").getString(), filter)) {
            Announcement latest = announcements.getLast();
            this.addEntry(new Row(null, Component.translatable("osmosis.announcements"), null, Sprites.ANNOUNCEMENTS,
                    latest.time(), Component.literal(latest.text()), null, 0));
        }

        List<Row> rows = new ArrayList<>();
        for (FriendProfile friend : friends.friends()) {
            if (matches(friend.name(), filter)) {
                ChatTarget target = ChatTarget.direct(friend.id());
                rows.add(this.chatRow(friends, target, friend.name(), friend.id(), null));
            }
        }
        for (GroupInfo group : friends.groups()) {
            if (matches(group.name(), filter)) {
                rows.add(this.chatRow(friends, ChatTarget.group(group.id()), group.name(), null, group));
            }
        }
        rows.sort(Comparator.comparingLong((Row row) -> -row.time).thenComparing(row -> row.name.getString(),
                String.CASE_INSENSITIVE_ORDER));
        rows.forEach(this::addEntry);
        if (this.children().isEmpty()) {
            this.addEntry(new Row(null, Component.translatable(filter.isEmpty() ? "osmosis.sidebar.no_friends"
                    : "osmosis.search.no_results"), null, null, 0, null, null, 0));
        }
        this.setScrollAmount(scroll);
    }

    private Row chatRow(FriendList friends, ChatTarget target, String name, @Nullable UUID player,
                        @Nullable GroupInfo group) {
        ChatMessage last = friends.lastMessage(target);
        return new Row(target, Component.literal(name), player, player == null ? Sprites.GROUP : null,
                last != null ? last.time() : 0, last != null ? this.preview(friends, last, group) : null,
                last != null && last.kind() == ChatMessage.INVITE ? Sprites.sprite("menu/invite") : null,
                friends.unread(target));
    }

    private Component preview(FriendList friends, ChatMessage message, @Nullable GroupInfo group) {
        if (message.text() == null) {
            return Component.translatable("osmosis.chat.undecryptable");
        }
        Component text = message.kind() == ChatMessage.INVITE ? Component.literal(message.inviteWorldName())
                : Component.literal(message.text().replace('\n', ' '));
        if (message.outgoing()) {
            return Component.translatable("osmosis.chat.preview.you", text);
        }
        if (group != null) {
            for (FriendProfile member : group.members()) {
                if (member.id().equals(message.sender())) {
                    return Component.translatable("osmosis.chat.preview.member", member.name(), text);
                }
            }
        }
        return text;
    }

    private static boolean matches(String name, String filter) {
        return filter.isEmpty() || name.toLowerCase(Locale.ROOT).contains(filter);
    }

    final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final @Nullable ChatTarget target;
        private final Component name;
        private final @Nullable ResolvableProfile face;
        private final @Nullable Identifier icon;
        private final long time;
        private final @Nullable Component preview;
        private final @Nullable Identifier previewIcon;
        private final int unread;

        private Row(@Nullable ChatTarget target, Component name, @Nullable UUID player,
                    @Nullable Identifier icon, long time, @Nullable Component preview,
                    @Nullable Identifier previewIcon, int unread) {
            this.target = target;
            this.name = name;
            this.face = player != null ? ResolvableProfile.createUnresolved(player) : null;
            this.icon = icon;
            this.time = time;
            this.preview = preview;
            this.previewIcon = previewIcon;
            this.unread = unread;
        }

        /** Rows without a target and without an icon are hints, like an empty list. */
        private boolean isHint() {
            return this.target == null && this.icon == null;
        }

        private boolean isSelected() {
            return this.icon == Sprites.ANNOUNCEMENTS ? ConversationList.this.screen.showsAnnouncements()
                    : this.target != null && this.target.equals(ConversationList.this.screen.selected());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = ConversationList.this.minecraft.font;
            int x = this.getContentX();
            int y = this.getContentY();
            if (this.isHint()) {
                graphics.centeredText(font, this.name, this.getContentXMiddle(), y + 4, SocialScreen.MUTED_COLOR);
                return;
            }
            if (this.isSelected()) {
                Sprites.draw(graphics, Sprites.ROW_SELECTED, x, y, this.getContentWidth(), this.getContentHeight());
            } else if (hovered) {
                Sprites.draw(graphics, Sprites.ROW_HOVERED, x, y, this.getContentWidth(), this.getContentHeight());
            }
            int iconY = y + (this.getContentHeight() - ICON_SIZE) / 2;
            if (this.face != null) {
                PlayerFaceExtractor.extractRenderState(graphics, this.face, x + 4, iconY, ICON_SIZE);
            } else if (this.icon != null) {
                Sprites.draw(graphics, this.icon, x + 4, iconY, ICON_SIZE, ICON_SIZE);
            }
            int textX = x + 4 + ICON_SIZE + 6;
            int right = this.getContentRight() - 4;
            if (this.time > 0) {
                String date = TimeText.chatListDate(this.time);
                graphics.text(font, date, right - font.width(date), y + 3, SocialScreen.MUTED_COLOR);
                right -= font.width(date) + 6;
            }
            graphics.text(font, font.plainSubstrByWidth(this.name.getString(), right - textX), textX, y + 3,
                    SocialScreen.TEXT_COLOR);

            int previewRight = this.getContentRight() - 4;
            if (this.unread > 0) {
                String count = this.unread > 9 ? "9+" : Integer.toString(this.unread);
                int badgeWidth = Math.max(10, font.width(count) + 4);
                graphics.fill(previewRight - badgeWidth, y + 14, previewRight, y + 24, BADGE_COLOR);
                graphics.centeredText(font, count, previewRight - badgeWidth / 2, y + 15, SocialScreen.TEXT_COLOR);
                previewRight -= badgeWidth + 4;
            }
            if (this.preview != null) {
                int previewX = textX;
                if (this.previewIcon != null) {
                    Sprites.draw(graphics, this.previewIcon, previewX, y + 15, 9, 9);
                    previewX += 12;
                }
                graphics.text(font, font.split(this.preview, previewRight - previewX).getFirst(), previewX, y + 15,
                        SocialScreen.MUTED_COLOR);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (this.isHint()) {
                return false;
            }
            if (this.target == null) {
                ConversationList.this.screen.openAnnouncements();
            } else if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                ConversationList.this.screen.openMenu(this.target, (int) event.x(), (int) event.y());
            } else {
                ConversationList.this.screen.openChat(this.target);
            }
            return true;
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
