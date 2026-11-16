package cc.avie.osmosis.client.gui.popup;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.GroupInfo;
import cc.avie.osmosis.client.gui.PanelButton;
import cc.avie.osmosis.client.gui.PanelList;
import cc.avie.osmosis.client.gui.Sprites;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The members of a group. The owner can remove members from here.
 */
public final class GroupMembersPopup extends OsmosisPopup {
    private static final int WIDTH = 220;
    private static final int LIST_HEIGHT = 140;
    private static final int ROW_HEIGHT = 22;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int OWNER_COLOR = 0xFFFFFF55;

    private final long groupId;
    private final Runnable refreshListener = this::rebuildWidgets;

    public GroupMembersPopup(Screen parent, GroupInfo group) {
        super(parent, Component.translatable("osmosis.group.members", group.name()));
        this.groupId = group.id();
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
        super.init();
        GroupInfo group = OsmosisClient.friends().group(this.groupId);
        if (group == null) {
            this.onClose();
            return;
        }
        int y = this.layoutPopup(WIDTH, PADDING + 14 + LIST_HEIGHT + 6 + 20 + PADDING);
        MemberList list = new MemberList(this.minecraft, this.left + 2, y, WIDTH - 4, LIST_HEIGHT);
        FriendProfile self = OsmosisClient.friends().self();
        boolean canRemove = self != null && group.isOwner(self.id());
        for (FriendProfile member : group.members()) {
            list.addRow(new MemberRow(group, member, canRemove && !member.id().equals(self.id())));
        }
        this.addRenderableWidget(list);
        this.addRenderableWidget(PanelButton.text(this.left + PADDING, y + LIST_HEIGHT + 6, WIDTH - 2 * PADDING, 20,
                CommonComponents.GUI_DONE, this::onClose));
    }

    private static final class MemberList extends PanelList<MemberRow> {
        private MemberList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, x, y, width, height, ROW_HEIGHT);
        }

        private void addRow(MemberRow row) {
            this.addEntry(row);
        }
    }

    private final class MemberRow extends ContainerObjectSelectionList.Entry<MemberRow> {
        private final FriendProfile member;
        private final boolean owner;
        private final ResolvableProfile face;
        private final List<PanelButton> buttons;

        private MemberRow(GroupInfo group, FriendProfile member, boolean removable) {
            this.member = member;
            this.owner = group.isOwner(member.id());
            this.face = ResolvableProfile.createUnresolved(member.id());
            this.buttons = removable ? List.of(removeButton(group.id(), member.id())) : List.of();
        }

        private static PanelButton removeButton(long group, UUID member) {
            return PanelButton.icon(0, 0, 15, Sprites.sprite("menu/remove"),
                    Component.translatable("osmosis.group.remove_member"),
                    () -> OsmosisClient.connection().removeGroupMember(group, member));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX() + 4;
            int y = this.getContentY() + (this.getContentHeight() - 16) / 2;
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x, y, 16);
            graphics.text(GroupMembersPopup.this.font, Component.literal(this.member.name()), x + 22, y + 4,
                    TEXT_COLOR);
            if (this.owner) {
                Component tag = Component.translatable("osmosis.group.owner");
                graphics.text(GroupMembersPopup.this.font, tag,
                        this.getContentRight() - 4 - GroupMembersPopup.this.font.width(tag), y + 4, OWNER_COLOR);
            }
            for (PanelButton button : this.buttons) {
                int buttonY = this.getContentY() + (this.getContentHeight() - button.getHeight()) / 2;
                button.setPosition(this.getContentRight() - button.getWidth() - 2, buttonY);
                button.extractRenderState(graphics, mouseX, mouseY, a);
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
