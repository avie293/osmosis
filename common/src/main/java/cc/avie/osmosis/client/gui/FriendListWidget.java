package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.FriendList;
import cc.avie.osmosis.client.friend.FriendProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;

final class FriendListWidget extends ContainerObjectSelectionList<FriendListWidget.Entry> {
    private static final int ROW_WIDTH = 220;
    private static final int PLAYER_ROW_HEIGHT = 28;
    private static final int HEADER_ROW_HEIGHT = 16;
    private static final int FACE_SIZE = 24;
    private static final int NAME_COLOR = 0xFFFFFFFF;
    private static final int HEADER_COLOR = 0xFFA0A0A0;
    private static final int ONLINE_COLOR = 0xFF55FF55;
    private static final int OFFLINE_COLOR = 0xFFA0A0A0;

    private static final Comparator<FriendProfile> BY_NAME = Comparator.comparing(FriendProfile::name,
            String.CASE_INSENSITIVE_ORDER);

    private final FriendsScreen screen;

    FriendListWidget(Minecraft minecraft, int width, int height, int y, FriendsScreen screen) {
        super(minecraft, width, height, y, PLAYER_ROW_HEIGHT);
        this.screen = screen;
    }

    @Override
    public int getRowWidth() {
        return ROW_WIDTH;
    }

    void rebuild(FriendList friends) {
        double scroll = this.scrollAmount();
        this.clearEntries();

        if (!friends.incoming().isEmpty() || !friends.outgoing().isEmpty()) {
            this.addEntry(new HeaderEntry(Component.translatable("osmosis.friends.requests")), HEADER_ROW_HEIGHT);
            for (FriendProfile profile : friends.incoming()) {
                this.addEntry(new PlayerEntry(profile, Relation.INCOMING));
            }
            for (FriendProfile profile : friends.outgoing()) {
                this.addEntry(new PlayerEntry(profile, Relation.OUTGOING));
            }
        }

        if (friends.friends().isEmpty()) {
            this.addEntry(new HeaderEntry(Component.translatable("osmosis.friends.empty")), HEADER_ROW_HEIGHT);
        } else {
            List<FriendProfile> sorted = new ArrayList<>(friends.friends());
            sorted.sort(Comparator.comparing((FriendProfile p) -> !friends.isOnline(p.id())).thenComparing(BY_NAME));
            long online = sorted.stream().filter(p -> friends.isOnline(p.id())).count();
            this.addEntry(new HeaderEntry(Component.translatable("osmosis.friends.section", online, sorted.size())),
                    HEADER_ROW_HEIGHT);
            for (FriendProfile profile : sorted) {
                this.addEntry(new PlayerEntry(profile, friends.isOnline(profile.id()) ? Relation.ONLINE
                        : Relation.OFFLINE));
            }
        }
        this.setScrollAmount(scroll);
    }

    private enum Relation {
        INCOMING("osmosis.friends.incoming", OFFLINE_COLOR),
        OUTGOING("osmosis.friends.outgoing", OFFLINE_COLOR),
        ONLINE("osmosis.friends.online", ONLINE_COLOR),
        OFFLINE("osmosis.friends.offline", OFFLINE_COLOR);

        private final Component status;
        private final int color;

        Relation(String key, int color) {
            this.status = Component.translatable(key);
            this.color = color;
        }
    }

    abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
    }

    private final class HeaderEntry extends Entry {
        private final Component text;

        private HeaderEntry(Component text) {
            this.text = text;
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            graphics.centeredText(FriendListWidget.this.minecraft.font, this.text,
                    this.getContentX() + this.getContentWidth() / 2,
                    this.getContentY() + (this.getContentHeight() - 9) / 2, HEADER_COLOR);
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

    private final class PlayerEntry extends Entry {
        private final FriendProfile profile;
        private final Relation relation;
        private final ResolvableProfile face;
        private final List<Button> buttons = new ArrayList<>(2);

        private PlayerEntry(FriendProfile profile, Relation relation) {
            this.profile = profile;
            this.relation = relation;
            this.face = ResolvableProfile.createUnresolved(profile.id());
            var connection = OsmosisClient.connection();
            switch (relation) {
                case INCOMING -> {
                    this.addButton("accept", () -> connection.acceptRequest(profile.id()));
                    this.addButton("decline", () -> connection.declineRequest(profile.id()));
                }
                case OUTGOING -> this.addButton("cancel", () -> connection.cancelRequest(profile.id()));
                case ONLINE, OFFLINE -> this.addButton("remove", this::confirmRemove);
            }
        }

        private void addButton(String action, Runnable onPress) {
            Component label = Component.translatable("osmosis.friends.action." + action);
            this.buttons.add(SpriteIconButton.builder(label, button -> onPress.run(), true)
                    .width(20)
                    .sprite(Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, "friends/" + action), 15, 15)
                    .withTootip()
                    .build());
        }

        private void confirmRemove() {
            Minecraft minecraft = FriendListWidget.this.minecraft;
            FriendsScreen screen = FriendListWidget.this.screen;
            minecraft.gui.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    OsmosisClient.connection().removeFriend(this.profile.id());
                }
                minecraft.gui.setScreen(screen);
            }, Component.translatable("osmosis.friends.remove.title", this.profile.name()),
                    Component.translatable("osmosis.friends.remove.message")));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            int x = this.getContentX();
            int y = this.getContentY();
            int faceY = y + (this.getContentHeight() - FACE_SIZE) / 2;
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x + 2, faceY, FACE_SIZE);

            var font = FriendListWidget.this.minecraft.font;
            int textX = x + FACE_SIZE + 8;
            graphics.text(font, this.profile.name(), textX, faceY + 2, NAME_COLOR);
            graphics.text(font, this.relation.status, textX, faceY + 14, this.relation.color);

            boolean connected = FriendsScreen.isConnected();
            int buttonX = this.getContentRight() - 2;
            for (int i = this.buttons.size() - 1; i >= 0; i--) {
                Button button = this.buttons.get(i);
                buttonX -= button.getWidth();
                button.active = connected;
                button.setPosition(buttonX, y + (this.getContentHeight() - button.getHeight()) / 2);
                button.extractRenderState(graphics, mouseX, mouseY, a);
                buttonX -= 4;
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
