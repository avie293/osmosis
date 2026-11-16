package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.chat.ChatTarget;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import cc.avie.osmosis.client.gui.popup.MenuPopup;
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
 * The friends screen: a sidebar with groups, friends and requests on the left and the selected chat on the right,
 * between a bar at the top and one at the bottom.
 */
public final class OsmosisScreen extends Screen {
    private static final Component TITLE = Component.translatable("osmosis.name");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int BAR_HEIGHT = 24;
    private static final int TAB_HEIGHT = 18;
    /** Space between the sidebar edge and the tabs and the panel below them. */
    private static final int SIDEBAR_MARGIN = 4;
    /** Space between the tab panel's frame and what is inside it. */
    private static final int PANEL_INSET = 5;
    private static final int ICON_BUTTON_SIZE = 19;
    /** Just tall enough for the text, leaving 5 pixels above and below in the bar. */
    private static final int BACK_BUTTON_HEIGHT = 14;
    private static final int CHAT_HEADER_HEIGHT = 30;
    private static final int INPUT_ROW_HEIGHT = 26;
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int PREVIEW_LENGTH = 40;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFFA0A0A0;
    private static final int ONLINE_COLOR = 0xFF55FF55;

    private final @Nullable Screen parent;
    private final Runnable refreshListener = this::refresh;
    private boolean requestsTab;
    private @Nullable ChatTarget selected;
    private String draft = "";
    private String addName = "";
    private @Nullable TabButton requestsTabButton;
    private @Nullable SidebarList sidebar;
    private @Nullable MessageList messages;
    private @Nullable PanelButton addButton;
    private @Nullable PanelButton sendButton;
    private @Nullable EditBox input;

    public OsmosisScreen(@Nullable Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    /**
     * @return whether this chat is open right now, so incoming messages count as read
     */
    public static boolean isViewing(ChatTarget target) {
        return Minecraft.getInstance().gui.screen() instanceof OsmosisScreen screen && target.equals(screen.selected);
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
        return this.selected;
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

    private int sidebarWidth() {
        return Math.max(120, this.width / 5);
    }

    /** The selected tab reaches 2 pixels into the panel below, covering its frame so both look like one piece. */
    private int tabPanelTop() {
        return BAR_HEIGHT + SIDEBAR_MARGIN + TAB_HEIGHT;
    }

    @Override
    protected void init() {
        int sidebarWidth = this.sidebarWidth();
        int contentTop = BAR_HEIGHT;
        int contentBottom = this.height - BAR_HEIGHT;

        int tabTop = contentTop + SIDEBAR_MARGIN;
        int tabWidth = (sidebarWidth - 2 * SIDEBAR_MARGIN - 2) / 2;
        this.addRenderableWidget(new TabButton(SIDEBAR_MARGIN, tabTop, tabWidth, TAB_HEIGHT,
                Component.translatable("osmosis.sidebar.tab.friends"), () -> !this.requestsTab,
                () -> this.switchTab(false)));
        this.requestsTabButton = this.addRenderableWidget(new TabButton(SIDEBAR_MARGIN + tabWidth + 2, tabTop,
                tabWidth, TAB_HEIGHT, this.requestsTabLabel(), () -> this.requestsTab, () -> this.switchTab(true)));

        int panelTop = this.tabPanelTop();
        int innerLeft = SIDEBAR_MARGIN + PANEL_INSET;
        int innerRight = sidebarWidth - SIDEBAR_MARGIN - PANEL_INSET;
        int listTop = panelTop + 3;
        if (this.requestsTab) {
            int boxTop = panelTop + PANEL_INSET;
            int buttonX = innerRight - ICON_BUTTON_SIZE;
            EditBox nameBox = new EditBox(this.font, innerLeft, boxTop, buttonX - 3 - innerLeft, ICON_BUTTON_SIZE,
                    Component.translatable("osmosis.friends.name_hint"));
            nameBox.setHint(Component.translatable("osmosis.friends.name_hint"));
            nameBox.setMaxLength(16);
            nameBox.setValue(this.addName);
            nameBox.setResponder(value -> {
                this.addName = value;
                this.updateButtons();
            });
            this.addRenderableWidget(nameBox);
            this.addButton = this.addRenderableWidget(PanelButton.icon(buttonX, boxTop, ICON_BUTTON_SIZE,
                    Sprites.sprite("friends/add"), Component.translatable("osmosis.friends.add"), this::sendRequest));
            listTop = boxTop + ICON_BUTTON_SIZE + 3;
        } else {
            this.addButton = null;
        }
        // The list sits just inside the panel's 2 pixel frame and scrolls under nothing else.
        int listLeft = SIDEBAR_MARGIN + 2;
        int listBottom = contentBottom - SIDEBAR_MARGIN - 2;
        this.sidebar = this.addRenderableWidget(new SidebarList(this.minecraft, listLeft, listTop,
                sidebarWidth - 2 * listLeft, listBottom - listTop, this));

        int backMargin = (BAR_HEIGHT - BACK_BUTTON_HEIGHT) / 2;
        int backWidth = this.font.width(CommonComponents.GUI_BACK) + 8;
        this.addRenderableWidget(PanelButton.text(backMargin, backMargin, backWidth, BACK_BUTTON_HEIGHT,
                CommonComponents.GUI_BACK, this::onClose));

        this.messages = null;
        this.input = null;
        this.sendButton = null;
        if (this.selected != null) {
            this.initChat(sidebarWidth, contentTop, contentBottom);
        }
        this.refresh();
    }

    private void initChat(int sidebarWidth, int contentTop, int contentBottom) {
        int paneWidth = this.width - sidebarWidth;
        int menuX = this.width - ICON_BUTTON_SIZE - 6;
        int menuY = contentTop + (CHAT_HEADER_HEIGHT - ICON_BUTTON_SIZE) / 2;
        this.addRenderableWidget(PanelButton.icon(menuX, menuY, ICON_BUTTON_SIZE, Sprites.sprite("friends/menu"),
                Component.translatable("osmosis.menu"),
                () -> this.openMenu(this.selected, menuX, menuY + ICON_BUTTON_SIZE)));

        int listTop = contentTop + CHAT_HEADER_HEIGHT;
        int listBottom = contentBottom - INPUT_ROW_HEIGHT;
        this.messages = this.addRenderableWidget(new MessageList(this.minecraft, sidebarWidth, listTop, paneWidth,
                listBottom - listTop, this::loadOlder));

        this.input = new EditBox(this.font, sidebarWidth + 6, listBottom + 3, paneWidth - 74, 20,
                Component.translatable("osmosis.chat.hint"));
        this.input.setHint(Component.translatable("osmosis.chat.hint"));
        this.input.setMaxLength(MAX_MESSAGE_LENGTH);
        this.input.setValue(this.draft);
        this.input.setResponder(value -> {
            this.draft = value;
            this.updateButtons();
        });
        this.addRenderableWidget(this.input);
        this.sendButton = this.addRenderableWidget(PanelButton.text(this.width - 64, listBottom + 3, 58, 20,
                Component.translatable("osmosis.chat.send"), this::send));
        this.setInitialFocus(this.input);
        this.loadOlder();
        this.markRead();
    }

    private Component requestsTabLabel() {
        int incoming = OsmosisClient.friends().incoming().size();
        return incoming > 0 ? Component.translatable("osmosis.sidebar.tab.requests_count", incoming)
                : Component.translatable("osmosis.sidebar.tab.requests");
    }

    private void switchTab(boolean requests) {
        if (this.requestsTab != requests) {
            this.requestsTab = requests;
            this.rebuildWidgets();
        }
    }

    void openChat(ChatTarget target) {
        if (!target.equals(this.selected)) {
            this.selected = target;
            this.draft = "";
            this.rebuildWidgets();
        }
    }

    void openMenu(@Nullable ChatTarget target, int x, int y) {
        if (target != null && this.isConnected()) {
            this.minecraft.gui.setScreen(new MenuPopup(this, ChatActions.forTarget(this, target), x, y));
        }
    }

    void deleteChat(ChatTarget target) {
        OsmosisClient.connection().deleteChat(target);
        OsmosisClient.chats().deleteMessages(target);
        OsmosisClient.friends().clearUnread(target);
    }

    private void refresh() {
        FriendList friends = OsmosisClient.friends();
        // The open chat disappears when the friendship or group membership ends.
        if (this.selected != null && !this.exists(this.selected)) {
            this.selected = null;
            this.rebuildWidgets();
            return;
        }
        if (this.requestsTabButton != null) {
            this.requestsTabButton.setMessage(this.requestsTabLabel());
        }
        if (this.sidebar != null) {
            if (this.requestsTab) {
                this.sidebar.showRequests(friends);
            } else {
                this.sidebar.showFriends(friends);
            }
        }
        if (this.messages != null && this.selected != null) {
            GroupInfo group = this.selected.isGroup() ? friends.group(this.selected.group()) : null;
            this.messages.rebuild(OsmosisClient.chats().get(this.selected), group);
        }
        this.updateButtons();
    }

    private boolean exists(ChatTarget target) {
        FriendList friends = OsmosisClient.friends();
        return target.isGroup() ? friends.group(target.group()) != null : friends.friend(target.friend()) != null;
    }

    private void updateButtons() {
        if (this.addButton != null) {
            this.addButton.active = this.isConnected() && VALID_NAME.matcher(this.addName.strip()).matches();
        }
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

    private void sendRequest() {
        if (this.addButton == null || !this.addButton.active) {
            return;
        }
        String name = this.addName.strip();
        Component problem = OsmosisClient.friends().describeRelation(name);
        if (problem != null) {
            OsmosisToast.show(OsmosisToast.TITLE, problem, null);
            return;
        }
        OsmosisClient.connection().sendRequest(name);
        this.addName = "";
        this.rebuildWidgets();
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
        if (event.isConfirmation() && this.getFocused() instanceof EditBox box) {
            if (box == this.input) {
                this.send();
            } else {
                this.sendRequest();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int sidebarWidth = this.sidebarWidth();
        Sprites.draw(graphics, Sprites.SIDEBAR, 0, BAR_HEIGHT, sidebarWidth, this.height - 2 * BAR_HEIGHT);
        int panelTop = this.tabPanelTop();
        Sprites.draw(graphics, Sprites.TAB_CONTENT, SIDEBAR_MARGIN, panelTop, sidebarWidth - 2 * SIDEBAR_MARGIN,
                this.height - BAR_HEIGHT - SIDEBAR_MARGIN - panelTop);
        Sprites.draw(graphics, Sprites.CHAT, sidebarWidth, BAR_HEIGHT, this.width - sidebarWidth,
                this.height - 2 * BAR_HEIGHT);
        Sprites.draw(graphics, Sprites.BAR, 0, 0, this.width, BAR_HEIGHT);
        Sprites.draw(graphics, Sprites.BAR, 0, this.height - BAR_HEIGHT, this.width, BAR_HEIGHT);
        if (this.selected != null) {
            Sprites.draw(graphics, Sprites.BAR, sidebarWidth, BAR_HEIGHT, this.width - sidebarWidth,
                    CHAT_HEADER_HEIGHT);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.centeredText(this.font, this.title, this.width / 2, (BAR_HEIGHT - 8) / 2, TEXT_COLOR);
        graphics.text(this.font, this.describeStatus(), 6, this.height - BAR_HEIGHT + 8, MUTED_COLOR);
        int sidebarWidth = this.sidebarWidth();
        if (this.selected == null) {
            graphics.centeredText(this.font, Component.translatable("osmosis.chat.select"),
                    sidebarWidth + (this.width - sidebarWidth) / 2, this.height / 2 - 4, MUTED_COLOR);
        } else {
            this.extractChatHeader(graphics, sidebarWidth);
        }
    }

    private void extractChatHeader(GuiGraphicsExtractor graphics, int sidebarWidth) {
        FriendList friends = OsmosisClient.friends();
        int x = sidebarWidth + 8;
        int y = BAR_HEIGHT + (CHAT_HEADER_HEIGHT - 20) / 2;
        if (this.selected.isGroup()) {
            GroupInfo group = friends.group(this.selected.group());
            if (group == null) {
                return;
            }
            Sprites.draw(graphics, Sprites.GROUP, x, y, 20, 20);
            graphics.text(this.font, group.name(), x + 26, y + 1, TEXT_COLOR);
            graphics.text(this.font, Component.translatable("osmosis.sidebar.members", group.members().size()),
                    x + 26, y + 11, MUTED_COLOR);
        } else {
            FriendProfile friend = friends.friend(this.selected.friend());
            if (friend == null) {
                return;
            }
            PlayerFaceExtractor.extractRenderState(graphics, ResolvableProfile.createUnresolved(friend.id()), x, y, 20);
            boolean online = friends.isOnline(friend.id());
            graphics.text(this.font, friend.name(), x + 26, y + 1, TEXT_COLOR);
            graphics.text(this.font, Component.translatable(online ? "osmosis.friends.online"
                    : "osmosis.friends.offline"), x + 26, y + 11, online ? ONLINE_COLOR : MUTED_COLOR);
        }
    }

    private Component describeStatus() {
        FriendList friends = OsmosisClient.friends();
        FriendProfile self = friends.self();
        if (friends.status() == ConnectionStatus.CONNECTED && self != null) {
            return Component.translatable("osmosis.status.connected", self.name());
        }
        return friends.status().describe();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
