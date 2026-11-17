package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import cc.avie.osmosis.client.friend.HostedWorld;
import cc.avie.osmosis.client.gui.popup.MenuPopup;
import cc.avie.osmosis.client.gui.popup.TextInputPopup;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * The social screen: a chat tab with every conversation next to the open one, and a friends tab with friends,
 * requests and blocked players side by side.
 */
public final class SocialScreen extends Screen {
    static final int TEXT_COLOR = 0xFFE6E6EA;
    static final int MUTED_COLOR = 0xFF8C8C96;
    static final int ACCENT_COLOR = 0xFF5A9BF0;
    private static final int ONLINE_COLOR = 0xFF55FF55;
    private static final int DIVIDER_COLOR = 0xFF34343C;

    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int HEADER_HEIGHT = 25;
    private static final int TAB_ROW_HEIGHT = 20;
    private static final int CHAT_HEADER_HEIGHT = 30;
    private static final int AVATAR_SIZE = 24;
    private static final int INPUT_ROW_HEIGHT = 26;
    private static final int ICON_BUTTON_SIZE = 17;
    private static final int BACK_BUTTON_SIZE = 19;
    private static final int SEARCH_WIDTH = 120;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int PREVIEW_LENGTH = 40;

    private final @Nullable Screen parent;
    private final Runnable refreshListener = this::refresh;
    private boolean friendsTab;
    private boolean announcements;
    private boolean searching;
    private String search = "";
    private @Nullable ChatTarget selected;
    private String draft = "";
    private @Nullable TextTab friendsTabButton;
    private @Nullable ConversationList conversations;
    private @Nullable MessageList messages;
    private @Nullable AnnouncementList announcementList;
    private @Nullable PeopleList friendColumn;
    private @Nullable PeopleList requestColumn;
    private @Nullable PeopleList blockedColumn;
    private @Nullable PanelButton sendButton;
    private @Nullable EditBox input;

    public SocialScreen(@Nullable Screen parent) {
        super(Component.translatable("osmosis.social"));
        this.parent = parent;
    }

    /**
     * @return whether this chat is open right now, so incoming messages count as read
     */
    public static boolean isViewing(ChatTarget target) {
        return Minecraft.getInstance().gui.screen() instanceof SocialScreen screen && !screen.friendsTab
                && target.equals(screen.selected);
    }

    /**
     * @return the start of the message for a notification, prefixed with the sender in groups
     */
    public static Component preview(ChatMessage message, @Nullable GroupInfo group) {
        String text = message.text();
        if (text == null) {
            return Component.translatable("osmosis.chat.undecryptable");
        }
        if (text.length() > PREVIEW_LENGTH) {
            text = text.substring(0, PREVIEW_LENGTH) + "...";
        }
        if (group != null) {
            for (FriendProfile member : group.members()) {
                if (member.id().equals(message.sender())) {
                    return Component.literal(member.name() + ": " + text);
                }
            }
        }
        return Component.literal(text);
    }

    @Nullable ChatTarget selected() {
        return this.announcements ? null : this.selected;
    }

    boolean showsAnnouncements() {
        return this.announcements;
    }

    boolean isConnected() {
        return OsmosisClient.friends().status() == ConnectionStatus.CONNECTED;
    }

    @Override
    public void added() {
        super.added();
        OsmosisClient.friends().addListener(this.refreshListener);
        OsmosisClient.chats().addListener(this.refreshListener);
    }

    @Override
    public void removed() {
        OsmosisClient.friends().removeListener(this.refreshListener);
        OsmosisClient.chats().removeListener(this.refreshListener);
        super.removed();
    }

    // Panel bounds, so the screen keeps some of the world or menu visible around it.
    private int panelLeft() {
        return this.marginX() + BACK_BUTTON_SIZE + 6;
    }

    private int panelRight() {
        return this.width - this.marginX();
    }

    private int panelTop() {
        return Math.max(8, this.height / 10);
    }

    private int panelBottom() {
        return this.height - this.panelTop();
    }

    private int marginX() {
        return Math.max(8, this.width / 16);
    }

    private int contentTop() {
        return this.panelTop() + HEADER_HEIGHT + TAB_ROW_HEIGHT + 1;
    }

    private int listWidth() {
        return Math.max(150, (this.panelRight() - this.panelLeft()) * 3 / 10);
    }

    @Override
    protected void init() {
        int left = this.panelLeft();
        int right = this.panelRight();
        int top = this.panelTop();

        this.addRenderableWidget(PanelButton.icon(this.marginX(), top, BACK_BUTTON_SIZE, Sprites.sprite("icon/back"),
                CommonComponents.GUI_BACK, this::onClose));

        int iconY = top + (HEADER_HEIGHT - ICON_BUTTON_SIZE) / 2;
        int iconX = right - 6 - ICON_BUTTON_SIZE;
        PanelButton searchButton = this.addRenderableWidget(PanelButton.icon(iconX, iconY, ICON_BUTTON_SIZE,
                Sprites.sprite("social/search"), Component.translatable("osmosis.search"), this::toggleSearch));
        if (this.searching) {
            searchButton.setStyle(PanelButton.Style.ACCENT);
        }
        iconX -= ICON_BUTTON_SIZE + 3;
        this.addRenderableWidget(PanelButton.icon(iconX, iconY, ICON_BUTTON_SIZE, Sprites.sprite("social/create_group"),
                Component.translatable("osmosis.group.create"), () -> ChatActions.openCreateGroup(this)));
        iconX -= ICON_BUTTON_SIZE + 3;
        this.addRenderableWidget(PanelButton.icon(iconX, iconY, ICON_BUTTON_SIZE, Sprites.sprite("social/add_friend"),
                Component.translatable("osmosis.friends.add"), this::openAddFriend));
        if (this.searching) {
            EditBox searchBox = new EditBox(this.font, iconX - 6 - SEARCH_WIDTH, iconY, SEARCH_WIDTH, ICON_BUTTON_SIZE,
                    Component.translatable("osmosis.search"));
            searchBox.setHint(Component.translatable("osmosis.search"));
            searchBox.setValue(this.search);
            searchBox.setResponder(value -> {
                this.search = value.strip().toLowerCase(Locale.ROOT);
                this.refresh();
            });
            this.addRenderableWidget(searchBox);
            this.setInitialFocus(searchBox);
        }

        int tabY = top + HEADER_HEIGHT;
        TextTab chatTab = this.addRenderableWidget(new TextTab(left + 8, tabY, TAB_ROW_HEIGHT,
                Component.translatable("osmosis.social.chat"), () -> !this.friendsTab, () -> this.switchTab(false)));
        this.friendsTabButton = this.addRenderableWidget(new TextTab(chatTab.getRight() + 12, tabY, TAB_ROW_HEIGHT,
                this.friendsTabLabel(), () -> this.friendsTab, () -> this.switchTab(true)));

        this.conversations = null;
        this.messages = null;
        this.announcementList = null;
        this.friendColumn = null;
        this.requestColumn = null;
        this.blockedColumn = null;
        this.input = null;
        this.sendButton = null;
        if (this.friendsTab) {
            this.initFriendsTab();
        } else {
            this.initChatTab();
        }
        this.refresh();
    }

    private void initChatTab() {
        int left = this.panelLeft();
        int top = this.contentTop();
        int bottom = this.panelBottom();
        int listWidth = this.listWidth();
        this.conversations = this.addRenderableWidget(new ConversationList(this.minecraft, left, top, listWidth,
                bottom - top, this));

        int paneLeft = left + listWidth + 1;
        int paneWidth = this.panelRight() - paneLeft;
        if (this.announcements) {
            this.announcementList = this.addRenderableWidget(new AnnouncementList(this.minecraft, paneLeft,
                    top + CHAT_HEADER_HEIGHT, paneWidth, bottom - top - CHAT_HEADER_HEIGHT));
            return;
        }
        if (this.selected == null) {
            return;
        }
        int menuX = this.panelRight() - 6 - ICON_BUTTON_SIZE;
        int menuY = top + (CHAT_HEADER_HEIGHT - ICON_BUTTON_SIZE) / 2;
        this.addRenderableWidget(PanelButton.icon(menuX, menuY, ICON_BUTTON_SIZE, Sprites.sprite("friends/menu"),
                Component.translatable("osmosis.menu"),
                () -> this.openMenu(this.selected, menuX, menuY + ICON_BUTTON_SIZE)));

        int listTop = top + CHAT_HEADER_HEIGHT;
        int listBottom = bottom - INPUT_ROW_HEIGHT;
        this.messages = this.addRenderableWidget(new MessageList(this.minecraft, paneLeft, listTop, paneWidth,
                listBottom - listTop, this::loadOlder));

        this.input = new EditBox(this.font, paneLeft + 6, listBottom + 3, paneWidth - 76, 20,
                Component.translatable("osmosis.chat.hint"));
        this.input.setHint(Component.translatable("osmosis.chat.hint"));
        this.input.setMaxLength(MAX_MESSAGE_LENGTH);
        this.input.setValue(this.draft);
        this.input.setResponder(value -> {
            this.draft = value;
            this.updateButtons();
        });
        this.addRenderableWidget(this.input);
        this.sendButton = this.addRenderableWidget(PanelButton.text(this.panelRight() - 66, listBottom + 3, 60, 20,
                Component.translatable("osmosis.chat.send"), this::send));
        if (!this.searching) {
            this.setInitialFocus(this.input);
        }
        this.loadOlder();
        this.markRead();
    }

    private void initFriendsTab() {
        int left = this.panelLeft();
        int top = this.contentTop() + 18;
        int bottom = this.panelBottom();
        int columnWidth = (this.panelRight() - left) / 3;
        this.friendColumn = this.addRenderableWidget(new PeopleList(this.minecraft, left, top, columnWidth,
                bottom - top, this));
        this.requestColumn = this.addRenderableWidget(new PeopleList(this.minecraft, left + columnWidth + 1, top,
                columnWidth - 1, bottom - top, this));
        this.blockedColumn = this.addRenderableWidget(new PeopleList(this.minecraft, left + 2 * columnWidth + 1, top,
                this.panelRight() - left - 2 * columnWidth - 1, bottom - top, this));
    }

    private Component friendsTabLabel() {
        int incoming = OsmosisClient.friends().incoming().size();
        return incoming > 0 ? Component.translatable("osmosis.social.friends_count", incoming)
                : Component.translatable("osmosis.social.friends");
    }

    private void switchTab(boolean friends) {
        if (this.friendsTab != friends) {
            this.friendsTab = friends;
            this.rebuildWidgets();
        }
    }

    private void toggleSearch() {
        this.searching = !this.searching;
        if (!this.searching) {
            this.search = "";
        }
        this.rebuildWidgets();
    }

    private void openAddFriend() {
        this.minecraft.gui.setScreen(new TextInputPopup(this, Component.translatable("osmosis.friends.add"), "", 16,
                this::sendRequest));
    }

    private void sendRequest(String name) {
        if (!VALID_NAME.matcher(name).matches()) {
            OsmosisToast.show(OsmosisToast.TITLE, Component.translatable("osmosis.error.player_not_found"), null);
            return;
        }
        Component problem = OsmosisClient.friends().describeRelation(name);
        if (problem != null) {
            OsmosisToast.show(OsmosisToast.TITLE, problem, null);
            return;
        }
        OsmosisClient.connection().sendRequest(name);
    }

    void openChat(ChatTarget target) {
        if (this.friendsTab || this.announcements || !target.equals(this.selected)) {
            this.friendsTab = false;
            this.announcements = false;
            this.selected = target;
            this.draft = "";
            this.rebuildWidgets();
        }
    }

    void openAnnouncements() {
        if (!this.announcements) {
            this.announcements = true;
            this.rebuildWidgets();
        }
    }

    void openMenu(@Nullable ChatTarget target, int x, int y) {
        if (target != null && this.isConnected()) {
            this.minecraft.gui.setScreen(new MenuPopup(this, ChatActions.forTarget(this, target), x, y));
        }
    }

    void openFriendMenu(FriendProfile friend, int x, int y) {
        if (!this.isConnected()) {
            return;
        }
        ChatTarget target = ChatTarget.direct(friend.id());
        List<MenuItem> items = new ArrayList<>();
        items.add(new MenuItem(Sprites.sprite("menu/message"), Component.translatable("osmosis.menu.message"),
                () -> this.openChat(target)));
        items.addAll(ChatActions.forTarget(this, target));
        this.minecraft.gui.setScreen(new MenuPopup(this, items, x, y));
    }

    void deleteChat(ChatTarget target) {
        OsmosisClient.connection().deleteChat(target);
        OsmosisClient.chats().deleteMessages(target);
        OsmosisClient.friends().clearUnread(target);
        OsmosisClient.friends().setLastMessage(target, null);
    }

    private void refresh() {
        FriendList friends = OsmosisClient.friends();
        if (this.selected != null && !this.exists(this.selected)) {
            this.selected = null;
            this.rebuildWidgets();
            return;
        }
        if (this.friendsTabButton != null) {
            this.friendsTabButton.setMessage(this.friendsTabLabel());
        }
        if (this.conversations != null) {
            this.conversations.rebuild(friends, this.search);
        }
        if (this.messages != null && this.selected != null) {
            GroupInfo group = this.selected.isGroup() ? friends.group(this.selected.group()) : null;
            this.messages.rebuild(OsmosisClient.chats().get(this.selected), group);
        }
        if (this.announcementList != null) {
            this.announcementList.rebuild(friends.announcements());
        }
        if (this.friendColumn != null && this.requestColumn != null && this.blockedColumn != null) {
            this.friendColumn.showFriends(friends, this.search);
            this.requestColumn.showRequests(friends, this.search);
            this.blockedColumn.showBlocked(friends, this.search);
        }
        this.updateButtons();
    }

    private boolean exists(ChatTarget target) {
        FriendList friends = OsmosisClient.friends();
        return target.isGroup() ? friends.group(target.group()) != null : friends.friend(target.friend()) != null;
    }

    private void updateButtons() {
        if (this.sendButton != null) {
            this.sendButton.active = this.isConnected() && !this.draft.isBlank();
        }
    }

    private void loadOlder() {
        if (this.selected != null) {
            long before = OsmosisClient.chats().get(this.selected).startLoading();
            if (before >= 0) {
                OsmosisClient.connection().requestHistory(this.selected, before);
            }
        }
    }

    private void markRead() {
        if (this.selected != null && OsmosisClient.friends().unread(this.selected) > 0) {
            OsmosisClient.connection().markRead(this.selected);
            OsmosisClient.friends().clearUnread(this.selected);
        }
    }

    private void send() {
        if (this.selected == null || this.sendButton == null || !this.sendButton.active || this.input == null) {
            return;
        }
        if (OsmosisClient.connection().sendMessage(this.selected, this.draft.strip())) {
            this.input.setValue("");
        } else {
            OsmosisToast.show(OsmosisToast.TITLE, Component.translatable("osmosis.chat.no_key"), null);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.isConfirmation() && this.input != null && this.getFocused() == this.input) {
            this.send();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int left = this.panelLeft();
        int right = this.panelRight();
        int top = this.panelTop();
        int bottom = this.panelBottom();
        Sprites.draw(graphics, Sprites.PANEL, left, top, right - left, bottom - top);
        Sprites.draw(graphics, Sprites.HEADER, left, top, right - left, HEADER_HEIGHT);
        int contentTop = this.contentTop();
        graphics.fill(left, contentTop - 1, right, contentTop, DIVIDER_COLOR);
        if (this.friendsTab) {
            int columnWidth = (right - left) / 3;
            graphics.fill(left + columnWidth, contentTop, left + columnWidth + 1, bottom, DIVIDER_COLOR);
            graphics.fill(left + 2 * columnWidth, contentTop, left + 2 * columnWidth + 1, bottom, DIVIDER_COLOR);
        } else {
            int divider = left + this.listWidth();
            graphics.fill(divider, contentTop, divider + 1, bottom, DIVIDER_COLOR);
            if (this.selected != null || this.announcements) {
                Sprites.draw(graphics, Sprites.HEADER, divider + 1, contentTop, right - divider - 1,
                        CHAT_HEADER_HEIGHT);
            }
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        int left = this.panelLeft();
        int top = this.panelTop();
        graphics.text(this.font, this.title, left + 8, top + (HEADER_HEIGHT - 8) / 2, TEXT_COLOR);
        graphics.text(this.font, this.describeStatus(), left + 8 + this.font.width(this.title) + 10,
                top + (HEADER_HEIGHT - 8) / 2, MUTED_COLOR);
        int contentTop = this.contentTop();
        if (this.friendsTab) {
            int columnWidth = (this.panelRight() - left) / 3;
            String[] titles = {"osmosis.friends.list", "osmosis.friends.requests", "osmosis.friends.blocked_list"};
            for (int i = 0; i < titles.length; i++) {
                int centerX = left + columnWidth * i + columnWidth / 2;
                graphics.centeredText(this.font, Component.translatable(titles[i]), centerX,
                        contentTop + 6, TEXT_COLOR);
            }
            return;
        }
        int paneLeft = left + this.listWidth() + 1;
        int paneRight = this.panelRight();
        if (this.announcements) {
            graphics.text(this.font, Component.translatable("osmosis.announcements"), paneLeft + 8,
                    contentTop + (CHAT_HEADER_HEIGHT - 8) / 2, TEXT_COLOR);
        } else if (this.selected == null) {
            graphics.centeredText(this.font, Component.translatable("osmosis.chat.select"),
                    (paneLeft + paneRight) / 2, (contentTop + this.panelBottom()) / 2 - 4, MUTED_COLOR);
        } else {
            this.extractChatHeader(graphics, paneLeft, contentTop);
        }
    }

    private void extractChatHeader(GuiGraphicsExtractor graphics, int x, int y) {
        FriendList friends = OsmosisClient.friends();
        int iconY = y + (CHAT_HEADER_HEIGHT - AVATAR_SIZE) / 2;
        Component name;
        Component status;
        int statusColor = MUTED_COLOR;
        if (this.selected.isGroup()) {
            GroupInfo group = friends.group(this.selected.group());
            if (group == null) {
                return;
            }
            Sprites.draw(graphics, Sprites.GROUP, x + 6, iconY, AVATAR_SIZE, AVATAR_SIZE);
            name = Component.literal(group.name());
            status = Component.translatable("osmosis.sidebar.members", group.members().size());
        } else {
            FriendProfile friend = friends.friend(this.selected.friend());
            if (friend == null) {
                return;
            }
            PlayerFaceExtractor.extractRenderState(graphics, ResolvableProfile.createUnresolved(friend.id()), x + 6,
                    iconY, AVATAR_SIZE);
            name = Component.literal(friend.name());
            HostedWorld world = friends.hostedWorld(friend.id());
            boolean online = friends.isOnline(friend.id());
            status = world != null ? Component.translatable("osmosis.friends.hosting", world.world())
                    : online ? Component.translatable("osmosis.friends.online")
                    : TimeText.lastOnline(friends.lastSeen(friend.id()));
            statusColor = online ? ONLINE_COLOR : MUTED_COLOR;
        }
        int textY = y + (CHAT_HEADER_HEIGHT - 8) / 2;
        int textX = x + 6 + AVATAR_SIZE + 6;
        graphics.text(this.font, name, textX, textY, TEXT_COLOR);
        graphics.text(this.font, status, textX + this.font.width(name) + 8, textY, statusColor);
    }

    private Component describeStatus() {
        FriendList friends = OsmosisClient.friends();
        return friends.status() == ConnectionStatus.CONNECTED ? Component.empty() : friends.status().describe();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
