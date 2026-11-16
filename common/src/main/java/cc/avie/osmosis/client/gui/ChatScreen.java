package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.chat.ChatMessage;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.layouts.SpacerElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Conversation with one friend.
 */
public final class ChatScreen extends Screen {
    private static final Component INPUT_HINT = Component.translatable("osmosis.chat.hint");
    private static final Component SEND = Component.translatable("osmosis.chat.send");
    private static final int MAX_MESSAGE_LENGTH = 256;
    private static final int PREVIEW_LENGTH = 40;
    private static final int STATUS_COLOR = 0xFFA0A0A0;
    private static final int ROW_WIDTH = 310;

    private final Screen parent;
    private final FriendProfile friend;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 40, 33);
    private final Runnable refreshListener = this::refresh;
    private @Nullable SpacerElement statusLine;
    private @Nullable ChatMessageList list;
    private @Nullable EditBox input;
    private @Nullable Button sendButton;

    public ChatScreen(Screen parent, FriendProfile friend) {
        super(Component.literal(friend.name()));
        this.parent = parent;
        this.friend = friend;
    }

    /**
     * @return whether the chat with this friend is open, so incoming messages count as read
     */
    public static boolean isViewing(UUID friend) {
        return Minecraft.getInstance().gui.screen() instanceof ChatScreen screen && screen.friend.id().equals(friend);
    }

    /**
     * @return the start of the message, short enough for a notification
     */
    public static Component preview(ChatMessage message) {
        String text = message.text();
        if (text == null) {
            return Component.translatable("osmosis.chat.undecryptable");
        }
        return Component.literal(text.length() > PREVIEW_LENGTH ? text.substring(0, PREVIEW_LENGTH) + "..." : text);
    }

    @Override
    public void added() {
        super.added();
        OsmosisClient.chats().addListener(this.refreshListener);
        OsmosisClient.friends().addListener(this.refreshListener);
        this.markRead();
    }

    @Override
    public void removed() {
        OsmosisClient.chats().removeListener(this.refreshListener);
        OsmosisClient.friends().removeListener(this.refreshListener);
        super.removed();
    }

    @Override
    protected void init() {
        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(4));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(this.title, this.font));
        this.statusLine = header.addChild(new SpacerElement(ROW_WIDTH, this.font.lineHeight));

        this.list = this.layout.addToContents(new ChatMessageList(this.minecraft, this.width,
                this.layout.getContentHeight(), this.layout.getHeaderHeight(), this.friend, this::loadOlder));

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(4));
        this.input = footer.addChild(new EditBox(this.font, ROW_WIDTH - 108, 20, INPUT_HINT));
        this.input.setHint(INPUT_HINT);
        this.input.setMaxLength(MAX_MESSAGE_LENGTH);
        this.input.setResponder(value -> this.updateSendButton());
        this.sendButton = footer.addChild(Button.builder(SEND, button -> this.send()).width(50).build());
        footer.addChild(Button.builder(CommonComponents.GUI_BACK, button -> this.onClose()).width(50).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        this.loadOlder();
        this.refresh();
        this.setInitialFocus(this.input);
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        if (this.list != null) {
            this.list.updateSize(this.width, this.layout);
        }
    }

    private void refresh() {
        if (this.list != null) {
            this.list.rebuild(OsmosisClient.chats().get(this.friend.id()));
        }
        this.updateSendButton();
    }

    private void loadOlder() {
        long before = OsmosisClient.chats().get(this.friend.id()).startLoading();
        if (before >= 0) {
            OsmosisClient.connection().requestHistory(this.friend.id(), before);
        }
    }

    private void markRead() {
        if (OsmosisClient.friends().unread(this.friend.id()) > 0) {
            OsmosisClient.connection().markRead(this.friend.id());
            OsmosisClient.friends().clearUnread(this.friend.id());
        }
    }

    private void updateSendButton() {
        if (this.sendButton != null && this.input != null) {
            boolean isFriend = OsmosisClient.friends().friend(this.friend.id()) != null;
            this.sendButton.active = FriendsScreen.isConnected() && isFriend && !this.input.getValue().isBlank();
        }
    }

    private void send() {
        if (this.input == null || this.sendButton == null || !this.sendButton.active) {
            return;
        }
        if (OsmosisClient.connection().sendMessage(this.friend.id(), this.input.getValue().strip())) {
            this.input.setValue("");
        } else {
            OsmosisToast.show(OsmosisToast.TITLE, Component.translatable("osmosis.chat.no_key", this.friend.name()),
                    this.friend.id());
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.input != null && this.input.isFocused() && event.isConfirmation()) {
            this.send();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.statusLine != null) {
            graphics.centeredText(this.font, this.describeStatus(), this.width / 2, this.statusLine.getY(),
                    STATUS_COLOR);
        }
    }

    private Component describeStatus() {
        FriendList friends = OsmosisClient.friends();
        if (friends.friend(this.friend.id()) == null) {
            return Component.translatable("osmosis.chat.not_friends");
        }
        return Component.translatable(friends.isOnline(this.friend.id()) ? "osmosis.friends.online"
                : "osmosis.friends.offline");
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
