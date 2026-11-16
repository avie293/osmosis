package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * The list on the left: groups and friends on the first tab, requests and blocked players on the second.
 */
final class SidebarList extends PanelList<SidebarList.Entry> {
    // Rows lose 2 pixels of padding on each side, so these leave 24 and 16 pixels for the content.
    private static final int ROW_HEIGHT = 28;
    private static final int SECTION_HEIGHT = 20;
    private static final int FACE_SIZE = 16;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int SECTION_COLOR = 0xFFFFFF55;
    private static final int ONLINE_COLOR = 0xFF55FF55;
    private static final int OFFLINE_COLOR = 0xFF606060;
    private static final int BADGE_COLOR = 0xFFE03030;

    private final OsmosisScreen screen;

    SidebarList(Minecraft minecraft, int x, int y, int width, int height, OsmosisScreen screen) {
        super(minecraft, x, y, width, height, ROW_HEIGHT);
        this.screen = screen;
    }

    @Override
    public int getRowWidth() {
        return this.width - 8;
    }

    void showFriends(FriendList friends) {
        this.clearEntries();
        this.addSection("osmosis.sidebar.groups", this.iconButton("add", "osmosis.group.create",
                () -> ChatActions.openCreateGroup(this.screen)));
        List<GroupInfo> groups = new ArrayList<>(friends.groups());
        groups.sort(Comparator.comparing(GroupInfo::name, String.CASE_INSENSITIVE_ORDER));
        for (GroupInfo group : groups) {
            this.addEntry(new ChatRow(ChatTarget.group(group.id()), group.name(), null,
                    Component.translatable("osmosis.sidebar.members", group.members().size()), MUTED_COLOR));
        }
        if (groups.isEmpty()) {
            this.addHint("osmosis.sidebar.no_groups");
        }

        this.addSection("osmosis.sidebar.friends", null);
        List<FriendProfile> sorted = new ArrayList<>(friends.friends());
        sorted.sort(Comparator.comparing((FriendProfile p) -> !friends.isOnline(p.id()))
                .thenComparing(FriendProfile::name, String.CASE_INSENSITIVE_ORDER));
        for (FriendProfile friend : sorted) {
            boolean online = friends.isOnline(friend.id());
            this.addEntry(new ChatRow(ChatTarget.direct(friend.id()), friend.name(), friend.id(),
                    Component.translatable(online ? "osmosis.friends.online" : "osmosis.friends.offline"),
                    online ? ONLINE_COLOR : MUTED_COLOR));
        }
        if (sorted.isEmpty()) {
            this.addHint("osmosis.sidebar.no_friends");
        }
    }

    void showRequests(FriendList friends) {
        this.clearEntries();
        this.addSection("osmosis.sidebar.incoming", null);
        for (FriendProfile profile : friends.incoming()) {
            this.addEntry(new PlayerRow(profile, List.of(
                    this.iconButton("accept", "osmosis.friends.action.accept",
                            () -> OsmosisClient.connection().acceptRequest(profile.id())),
                    this.iconButton("decline", "osmosis.friends.action.decline",
                            () -> OsmosisClient.connection().declineRequest(profile.id())))));
        }
        if (friends.incoming().isEmpty()) {
            this.addHint("osmosis.sidebar.none");
        }

        this.addSection("osmosis.sidebar.outgoing", null);
        for (FriendProfile profile : friends.outgoing()) {
            this.addEntry(new PlayerRow(profile, List.of(this.iconButton("cancel", "osmosis.friends.action.cancel",
                    () -> OsmosisClient.connection().cancelRequest(profile.id())))));
        }
        if (friends.outgoing().isEmpty()) {
            this.addHint("osmosis.sidebar.none");
        }

        this.addSection("osmosis.sidebar.blocked", null);
        for (FriendProfile profile : friends.blocked()) {
            this.addEntry(new PlayerRow(profile, List.of(this.iconButton("unblock", "osmosis.friends.action.unblock",
                    () -> OsmosisClient.connection().unblock(profile.id())))));
        }
        if (friends.blocked().isEmpty()) {
            this.addHint("osmosis.sidebar.none");
        }
    }

    private void addSection(String key, @Nullable Button button) {
        this.addEntry(new SectionRow(Component.translatable(key), button), SECTION_HEIGHT);
    }

    private void addHint(String key) {
        this.addEntry(new HintRow(Component.translatable(key)), SECTION_HEIGHT);
    }

    private Button iconButton(String sprite, String labelKey, Runnable action) {
        Component label = Component.translatable(labelKey);
        Button button = SpriteIconButton.builder(label, b -> action.run(), true)
                .size(14, 14)
                .sprite(Sprites.sprite("friends/" + sprite), 9, 9)
                .withTootip()
                .build();
        button.active = this.screen.isConnected();
        return button;
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

    private final class SectionRow extends Entry {
        private final Component title;
        private final @Nullable Button button;

        private SectionRow(Component title, @Nullable Button button) {
            this.title = title;
            this.button = button;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.text(SidebarList.this.minecraft.font, this.title, this.getContentX() + 2,
                    this.getContentY() + (this.getContentHeight() - 8) / 2, SECTION_COLOR);
            if (this.button != null) {
                this.button.setPosition(this.getContentRight() - this.button.getWidth(),
                        this.getContentY() + (this.getContentHeight() - this.button.getHeight()) / 2);
                this.button.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.button != null ? List.of(this.button) : List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.button != null ? List.of(this.button) : List.of();
        }
    }

    private final class HintRow extends Entry {
        private final Component text;

        private HintRow(Component text) {
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.text(SidebarList.this.minecraft.font, this.text, this.getContentX() + 6,
                    this.getContentY() + (this.getContentHeight() - 8) / 2, MUTED_COLOR);
        }
    }

    /** A friend or group that opens its chat on click and its menu on right click. */
    private final class ChatRow extends Entry {
        private final ChatTarget target;
        private final String name;
        private final @Nullable ResolvableProfile face;
        private final Component status;
        private final int statusColor;
        private final int unread;

        private ChatRow(ChatTarget target, String name, @Nullable UUID player, Component status, int statusColor) {
            this.target = target;
            this.name = name;
            this.face = player != null ? ResolvableProfile.createUnresolved(player) : null;
            this.status = status;
            this.statusColor = statusColor;
            this.unread = OsmosisClient.friends().unread(target);
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX();
            int y = this.getContentY();
            if (this.target.equals(SidebarList.this.screen.selected())) {
                Sprites.draw(graphics, Sprites.SELECTED, x, y, this.getContentWidth(), this.getContentHeight());
            } else if (hovered) {
                Sprites.draw(graphics, Sprites.HOVERED, x, y, this.getContentWidth(), this.getContentHeight());
            }
            int iconX = x + 4;
            int iconY = y + (this.getContentHeight() - FACE_SIZE) / 2;
            if (this.face != null) {
                PlayerFaceExtractor.extractRenderState(graphics, this.face, iconX, iconY, FACE_SIZE);
                // Status dot in the bottom right corner of the face, with a dark outline.
                int dotX = iconX + FACE_SIZE - 5;
                int dotY = iconY + FACE_SIZE - 5;
                graphics.fill(dotX, dotY, dotX + 5, dotY + 5, 0xFF000000);
                graphics.fill(dotX + 1, dotY + 1, dotX + 4, dotY + 4,
                        this.statusColor == ONLINE_COLOR ? ONLINE_COLOR : OFFLINE_COLOR);
            } else {
                Sprites.draw(graphics, Sprites.GROUP, iconX, iconY, FACE_SIZE, FACE_SIZE);
            }
            Font font = SidebarList.this.minecraft.font;
            int textX = iconX + FACE_SIZE + 5;
            int right = this.getContentRight() - 4;
            if (this.unread > 0) {
                String count = this.unread > 9 ? "9+" : Integer.toString(this.unread);
                int badgeWidth = Math.max(10, font.width(count) + 4);
                int badgeY = y + (this.getContentHeight() - 10) / 2;
                graphics.fill(right - badgeWidth, badgeY, right, badgeY + 10, BADGE_COLOR);
                graphics.centeredText(font, count, right - badgeWidth / 2, badgeY + 1, TEXT_COLOR);
                right -= badgeWidth + 4;
            }
            graphics.text(font, font.plainSubstrByWidth(this.name, right - textX), textX, y + 3, TEXT_COLOR);
            graphics.text(font, this.status, textX, y + 13, this.statusColor);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (event.button() == InputConstants.MOUSE_BUTTON_RIGHT) {
                SidebarList.this.screen.openMenu(this.target, (int) event.x(), (int) event.y());
            } else {
                SidebarList.this.screen.openChat(this.target);
            }
            return true;
        }
    }

    /** A player on the requests tab with action buttons on the right. */
    private final class PlayerRow extends Entry {
        private final FriendProfile profile;
        private final ResolvableProfile face;
        private final List<Button> buttons;

        private PlayerRow(FriendProfile profile, List<Button> buttons) {
            this.profile = profile;
            this.face = ResolvableProfile.createUnresolved(profile.id());
            this.buttons = buttons;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX();
            int y = this.getContentY();
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x + 4,
                    y + (this.getContentHeight() - FACE_SIZE) / 2, FACE_SIZE);
            Font font = SidebarList.this.minecraft.font;
            int textX = x + 4 + FACE_SIZE + 5;
            int textWidth = this.getContentRight() - 4 - this.buttons.size() * 16 - textX;
            graphics.text(font, font.plainSubstrByWidth(this.profile.name(), textWidth), textX,
                    y + (this.getContentHeight() - 8) / 2, TEXT_COLOR);
            int buttonX = this.getContentRight() - 2;
            for (int i = this.buttons.size() - 1; i >= 0; i--) {
                Button button = this.buttons.get(i);
                buttonX -= button.getWidth();
                button.setPosition(buttonX, y + (this.getContentHeight() - button.getHeight()) / 2);
                button.extractRenderState(graphics, mouseX, mouseY, a);
                buttonX -= 2;
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.buttons;
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.buttons;
        }
    }
}
