package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendList;
import java.util.regex.Pattern;
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

public final class FriendsScreen extends Screen {
    private static final Component TITLE = Component.translatable("osmosis.friends.title");
    private static final Component NAME_HINT = Component.translatable("osmosis.friends.name_hint");
    private static final Component ADD = Component.translatable("osmosis.friends.add");
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final int STATUS_COLOR = 0xFFA0A0A0;
    private static final int ROW_WIDTH = 200;

    private final @Nullable Screen parent;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 68, 33);
    private final Runnable refreshListener = this::refresh;
    private @Nullable SpacerElement statusLine;
    private @Nullable EditBox nameBox;
    private @Nullable Button addButton;
    private @Nullable FriendListWidget list;

    public FriendsScreen(@Nullable Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    @Override
    public void added() {
        super.added();
        OsmosisClient.friends().addListener(this.refreshListener);
    }

    @Override
    public void removed() {
        OsmosisClient.friends().removeListener(this.refreshListener);
        super.removed();
    }

    @Override
    protected void init() {
        LinearLayout header = this.layout.addToHeader(LinearLayout.vertical().spacing(5));
        header.defaultCellSetting().alignHorizontallyCenter();
        header.addChild(new StringWidget(TITLE, this.font));
        this.statusLine = header.addChild(new SpacerElement(ROW_WIDTH, this.font.lineHeight));

        LinearLayout addRow = header.addChild(LinearLayout.horizontal().spacing(4));
        this.nameBox = addRow.addChild(new EditBox(this.font, ROW_WIDTH - 54, 20, NAME_HINT));
        this.nameBox.setHint(NAME_HINT);
        this.nameBox.setMaxLength(16);
        this.nameBox.setResponder(value -> this.updateAddButton());
        this.addButton = addRow.addChild(Button.builder(ADD, button -> this.sendRequest()).width(50).build());

        this.list = this.layout.addToContents(new FriendListWidget(this.minecraft, this.width,
                this.layout.getContentHeight(), this.layout.getHeaderHeight(), this));
        this.layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose())
                .width(ROW_WIDTH)
                .build());
        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
        this.refresh();
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
            this.list.rebuild(OsmosisClient.friends());
        }
        this.updateAddButton();
    }

    private void updateAddButton() {
        if (this.addButton != null && this.nameBox != null) {
            this.addButton.active = isConnected() && VALID_NAME.matcher(this.nameBox.getValue().trim()).matches();
        }
    }

    static boolean isConnected() {
        return OsmosisClient.friends().status() == ConnectionStatus.CONNECTED;
    }

    private void sendRequest() {
        if (this.nameBox == null || this.addButton == null || !this.addButton.active) {
            return;
        }
        String name = this.nameBox.getValue().trim();
        Component problem = OsmosisClient.friends().describeRelation(name);
        if (problem != null) {
            OsmosisToast.show(OsmosisToast.TITLE, problem, null);
            return;
        }
        OsmosisClient.connection().sendRequest(name);
        this.nameBox.setValue("");
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.nameBox != null && this.nameBox.isFocused() && event.isConfirmation()) {
            this.sendRequest();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        if (this.statusLine != null) {
            graphics.centeredText(this.font, describeStatus(OsmosisClient.friends()), this.width / 2,
                    this.statusLine.getY(), STATUS_COLOR);
        }
    }

    private static Component describeStatus(FriendList friends) {
        if (friends.status() == ConnectionStatus.CONNECTED && friends.self() != null) {
            return Component.translatable("osmosis.status.connected", friends.self().name());
        }
        return friends.status().describe();
    }

    @Override
    public void onClose() {
        this.minecraft.gui.setScreen(this.parent);
    }
}
