package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.friend.HostedWorld;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * One column of the friends tab: friends, requests or blocked players.
 */
final class PeopleList extends PanelList<PeopleList.Row> {
    private static final int ROW_HEIGHT = 32;
    private static final int FACE_SIZE = 24;
    private static final int BUTTON_SIZE = 15;
    private static final int ONLINE_COLOR = 0xFF55FF55;
    private static final int HOSTING_COLOR = 0xFF55FFFF;

    private final SocialScreen screen;

    PeopleList(Minecraft minecraft, int x, int y, int width, int height, SocialScreen screen) {
        super(minecraft, x, y, width, height, ROW_HEIGHT);
        this.screen = screen;
    }

    @Override
    public int getRowWidth() {
        return this.width - 12;
    }

    void showFriends(FriendList friends, String filter) {
        this.clearEntries();
        List<FriendProfile> sorted = filtered(friends.friends(), filter);
        sorted.sort(Comparator.comparing((FriendProfile p) -> !friends.isOnline(p.id()))
                .thenComparing(FriendProfile::name, String.CASE_INSENSITIVE_ORDER));
        for (FriendProfile friend : sorted) {
            HostedWorld world = friends.hostedWorld(friend.id());
            boolean online = friends.isOnline(friend.id());
            Component status = world != null ? Component.translatable("osmosis.friends.hosting", world.world())
                    : online ? Component.translatable("osmosis.friends.online")
                    : TimeText.lastOnline(friends.lastSeen(friend.id()));
            int color = world != null ? HOSTING_COLOR : online ? ONLINE_COLOR : SocialScreen.MUTED_COLOR;
            // The menu opens below its button, which only knows its position once it is drawn.
            PanelButton[] menu = new PanelButton[1];
            menu[0] = this.button("friends/menu", "osmosis.menu",
                    () -> this.screen.openFriendMenu(friend, menu[0].getX(), menu[0].getBottom()));
            this.addEntry(new Row(friend, status, color, List.of(menu[0])));
        }
        this.addHintIfEmpty(friends.friends().isEmpty() ? "osmosis.sidebar.no_friends" : "osmosis.search.no_results");
    }

    void showRequests(FriendList friends, String filter) {
        this.clearEntries();
        for (FriendProfile profile : filtered(friends.incoming(), filter)) {
            this.addEntry(new Row(profile, Component.translatable("osmosis.friends.incoming"), SocialScreen.MUTED_COLOR,
                    List.of(this.button("friends/accept", "osmosis.friends.action.accept",
                                    () -> OsmosisClient.connection().acceptRequest(profile.id())),
                            this.button("friends/decline", "osmosis.friends.action.decline",
                                    () -> OsmosisClient.connection().declineRequest(profile.id())))));
        }
        for (FriendProfile profile : filtered(friends.outgoing(), filter)) {
            this.addEntry(new Row(profile, Component.translatable("osmosis.friends.outgoing"), SocialScreen.MUTED_COLOR,
                    List.of(this.button("friends/cancel", "osmosis.friends.action.cancel",
                            () -> OsmosisClient.connection().cancelRequest(profile.id())))));
        }
        this.addHintIfEmpty("osmosis.friends.no_requests");
    }

    void showBlocked(FriendList friends, String filter) {
        this.clearEntries();
        for (FriendProfile profile : filtered(friends.blocked(), filter)) {
            this.addEntry(new Row(profile, Component.translatable("osmosis.friends.blocked"), SocialScreen.MUTED_COLOR,
                    List.of(this.button("friends/unblock", "osmosis.friends.action.unblock",
                            () -> OsmosisClient.connection().unblock(profile.id())))));
        }
        this.addHintIfEmpty("osmosis.friends.no_blocked");
    }

    private void addHintIfEmpty(String key) {
        if (this.children().isEmpty()) {
            this.addEntry(new Row(null, Component.translatable(key), SocialScreen.MUTED_COLOR, List.of()));
        }
    }

    private static List<FriendProfile> filtered(Iterable<FriendProfile> profiles, String filter) {
        List<FriendProfile> result = new ArrayList<>();
        for (FriendProfile profile : profiles) {
            if (filter.isEmpty() || profile.name().toLowerCase(Locale.ROOT).contains(filter)) {
                result.add(profile);
            }
        }
        return result;
    }

    private PanelButton button(String sprite, String labelKey, Runnable action) {
        PanelButton button = PanelButton.icon(0, 0, BUTTON_SIZE, Sprites.sprite(sprite),
                Component.translatable(labelKey), action);
        button.active = this.screen.isConnected();
        return button;
    }

    /** A player with a status line and buttons on the right, or a hint when there is no player. */
    final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final @Nullable FriendProfile profile;
        private final @Nullable ResolvableProfile face;
        private final Component text;
        private final int color;
        private final List<PanelButton> buttons;

        private Row(@Nullable FriendProfile profile, Component text, int color, List<PanelButton> buttons) {
            this.profile = profile;
            this.face = profile != null ? ResolvableProfile.createUnresolved(profile.id()) : null;
            this.text = text;
            this.color = color;
            this.buttons = buttons;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = PeopleList.this.minecraft.font;
            int x = this.getContentX();
            int y = this.getContentY();
            if (this.profile == null) {
                graphics.centeredText(font, this.text, this.getContentXMiddle(), y + 4, this.color);
                return;
            }
            Sprites.draw(graphics, hovered ? Sprites.ROW_HOVERED : Sprites.ROW, x, y, this.getContentWidth(),
                    this.getContentHeight());
            int faceY = y + (this.getContentHeight() - FACE_SIZE) / 2;
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x + 4, faceY, FACE_SIZE);
            int textX = x + 4 + FACE_SIZE + 6;
            int right = this.getContentRight() - 4 - this.buttons.size() * (BUTTON_SIZE + 2);
            graphics.text(font, font.plainSubstrByWidth(this.profile.name(), right - textX), textX, y + 3,
                    SocialScreen.TEXT_COLOR);
            graphics.text(font, font.split(this.text, right - textX).getFirst(), textX, y + 15, this.color);
            int buttonX = this.getContentRight() - 4;
            for (int i = this.buttons.size() - 1; i >= 0; i--) {
                PanelButton button = this.buttons.get(i);
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
