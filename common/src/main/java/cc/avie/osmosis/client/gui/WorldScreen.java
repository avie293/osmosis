package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendProfile;
import cc.avie.osmosis.client.gui.popup.MemberPickerPopup;
import cc.avie.osmosis.client.gui.popup.OsmosisPopup;
import cc.avie.osmosis.client.host.WorldHost;
import cc.avie.osmosis.client.host.WorldInfo;
import cc.avie.osmosis.client.host.WorldSettings;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.jspecify.annotations.Nullable;

/**
 * The window for one world: who may join it, its settings, and starting or stopping hosting.
 */
public final class WorldScreen extends OsmosisPopup {
    private static final int HEADER_HEIGHT = 24;
    private static final int NAV_WIDTH = 92;
    private static final int CLOSE_SIZE = 15;
    private static final int ROW_HEIGHT = 26;
    private static final int CONTROL_WIDTH = 110;
    private static final int ONLINE_COLOR = 0xFF55FF55;
    private static final int OFFLINE_COLOR = 0xFFE05050;
    private static final GameType[] GAME_TYPES = {GameType.SURVIVAL, GameType.CREATIVE, GameType.ADVENTURE,
            GameType.SPECTATOR};

    private enum Tab {
        MEMBERS("osmosis.world.members"),
        GENERAL("osmosis.world.general");

        private final Component label;

        Tab(String key) {
            this.label = Component.translatable(key);
        }
    }

    private final WorldInfo world;
    private final Runnable refreshListener = this::rebuildWidgets;
    private Tab tab = Tab.MEMBERS;
    private WorldSettings settings;
    private String name;

    public WorldScreen(Screen parent, WorldInfo world) {
        super(parent, Component.empty());
        this.world = world;
        this.settings = world.settings();
        this.name = world.name();
    }

    /**
     * @return the window for the world that is open right now
     */
    public static WorldScreen forCurrentWorld(Screen parent) {
        IntegratedServer server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) {
            throw new IllegalStateException("No singleplayer world is open");
        }
        return new WorldScreen(parent, WorldInfo.of(server));
    }

    private boolean isHosted() {
        WorldHost host = OsmosisClient.host();
        return host.isHosting() && this.world.worldId().equals(host.worldId());
    }

    private boolean isConnected() {
        return OsmosisClient.friends().status() == ConnectionStatus.CONNECTED;
    }

    @Override
    public void added() {
        super.added();
        OsmosisClient.friends().addListener(this.refreshListener);
        OsmosisClient.connection().requestWorldMembers(this.world.worldId());
    }

    @Override
    public void removed() {
        OsmosisClient.friends().removeListener(this.refreshListener);
        super.removed();
    }

    @Override
    protected void init() {
        super.init();
        int width = Math.min(this.width - 40, 400);
        int height = Math.min(this.height - 40, 250);
        this.layoutPopup(width, height);
        this.addRenderableWidget(PanelButton.icon(this.left + width - 6 - CLOSE_SIZE,
                this.top + (HEADER_HEIGHT - CLOSE_SIZE) / 2, CLOSE_SIZE, Sprites.sprite("icon/close"),
                Component.translatable("gui.cancel"), this::onClose));

        int navY = this.top + HEADER_HEIGHT + 6;
        for (Tab each : Tab.values()) {
            this.addRenderableWidget(new TextTab(this.left + 10, navY, 14, each.label, () -> this.tab == each, () -> {
                this.tab = each;
                this.rebuildWidgets();
            }));
            navY += 16;
        }

        if (this.tab == Tab.MEMBERS) {
            this.initMembers();
        } else {
            this.initGeneral();
        }
    }

    private int contentLeft() {
        return this.left + NAV_WIDTH + 8;
    }

    private int contentRight() {
        return this.left + this.popupWidth - 8;
    }

    private int contentTop() {
        return this.top + HEADER_HEIGHT + 6;
    }

    private int contentBottom() {
        return this.top + this.popupHeight - 8;
    }

    private void initMembers() {
        int left = this.contentLeft();
        int right = this.contentRight();
        int buttonY = this.contentBottom() - 20;
        int buttonWidth = (right - left - 6) / 2;
        boolean hosted = this.isHosted();
        PanelButton hosting = PanelButton.text(left, buttonY, buttonWidth, 20,
                Component.translatable(hosted ? "osmosis.host.stop" : "osmosis.host.start"), this::toggleHosting)
                .setStyle(hosted ? PanelButton.Style.NEGATIVE : PanelButton.Style.POSITIVE);
        hosting.active = this.isConnected();
        this.addRenderableWidget(hosting);
        PanelButton add = PanelButton.text(left + buttonWidth + 6, buttonY, right - left - buttonWidth - 6, 20,
                Component.translatable("osmosis.world.add_members"), this::openAddMembers)
                .setStyle(PanelButton.Style.ACCENT);
        add.active = this.isConnected();
        this.addRenderableWidget(add);

        int listTop = this.contentTop() + 14;
        MemberList list = this.addRenderableWidget(new MemberList(this.minecraft, left - 2, listTop,
                right - left + 4, buttonY - 6 - listTop));
        FriendProfile self = OsmosisClient.friends().self();
        if (self != null) {
            list.add(new MemberRow(self, true));
        }
        List<FriendProfile> members = OsmosisClient.friends().worldMembers(this.world.worldId());
        if (members != null) {
            for (FriendProfile member : members) {
                list.add(new MemberRow(member, false));
            }
        }
    }

    private void initGeneral() {
        int controlX = this.contentRight() - 6 - CONTROL_WIDTH;
        int y = this.contentTop();
        int controlY = y + (ROW_HEIGHT - 18) / 2;

        // Renaming changes the save on disk, which only works while the world is closed.
        EditBox nameBox = new EditBox(this.font, controlX, controlY, CONTROL_WIDTH, 18,
                Component.translatable("osmosis.world.name"));
        nameBox.setMaxLength(64);
        nameBox.setValue(this.name);
        nameBox.setEditable(!this.world.loaded());
        nameBox.setResponder(value -> this.name = value);
        this.addRenderableWidget(nameBox);

        y += ROW_HEIGHT + 2;
        this.addRenderableWidget(this.cycleButton(controlX, y, this.settings.gameType().getShortDisplayName(), () ->
                this.changeSettings(new WorldSettings(next(GAME_TYPES, this.settings.gameType()),
                        this.settings.difficulty(), this.settings.cheats()))));
        y += ROW_HEIGHT + 2;
        this.addRenderableWidget(this.cycleButton(controlX, y, this.settings.difficulty().getDisplayName(), () ->
                this.changeSettings(new WorldSettings(this.settings.gameType(),
                        next(Difficulty.values(), this.settings.difficulty()), this.settings.cheats()))));
        y += ROW_HEIGHT + 2;
        PanelButton cheats = this.cycleButton(controlX, y,
                Component.translatable(this.settings.cheats() ? "options.on" : "options.off"), () ->
                        this.changeSettings(new WorldSettings(this.settings.gameType(), this.settings.difficulty(),
                                !this.settings.cheats())));
        if (this.settings.cheats()) {
            cheats.setStyle(PanelButton.Style.ACCENT);
        }
        this.addRenderableWidget(cheats);
    }

    private PanelButton cycleButton(int x, int rowY, Component label, Runnable onPress) {
        return PanelButton.text(x, rowY + (ROW_HEIGHT - 18) / 2, CONTROL_WIDTH, 18, label, onPress);
    }

    private static <T> T next(T[] values, T current) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) {
                return values[(i + 1) % values.length];
            }
        }
        return values[0];
    }

    /** Applies right away to the open world, otherwise once hosting starts. */
    private void changeSettings(WorldSettings settings) {
        this.settings = settings;
        IntegratedServer server = this.minecraft.getSingleplayerServer();
        if (this.world.loaded() && server != null) {
            settings.applyTo(server);
        }
        this.rebuildWidgets();
    }

    private void toggleHosting() {
        WorldHost host = OsmosisClient.host();
        if (this.isHosted()) {
            host.stop();
            this.rebuildWidgets();
            return;
        }
        if (this.world.loaded()) {
            host.start();
            this.rebuildWidgets();
            return;
        }
        this.renameIfChanged();
        host.hostAfterLoading(this.world.levelId(), this.settings, () -> this.minecraft.gui.setScreen(this));
    }

    private void renameIfChanged() {
        String name = this.name.strip();
        if (name.isEmpty() || name.equals(this.world.name())) {
            return;
        }
        LevelStorageSource source = this.minecraft.getLevelSource();
        try (LevelStorageSource.LevelStorageAccess access = source.createAccess(this.world.levelId())) {
            access.renameLevel(name);
        } catch (Exception e) {
            Osmosis.LOGGER.error("Could not rename world {}", this.world.levelId(), e);
        }
    }

    private void openAddMembers() {
        Set<UUID> excluded = new HashSet<>();
        List<FriendProfile> members = OsmosisClient.friends().worldMembers(this.world.worldId());
        if (members != null) {
            members.forEach(member -> excluded.add(member.id()));
        }
        this.minecraft.gui.setScreen(new MemberPickerPopup(this, Component.translatable("osmosis.world.add_members"),
                MemberPickerPopup.candidates(OsmosisClient.friends().friends(), excluded), Integer.MAX_VALUE, false,
                (name, picked) -> picked.forEach(id -> OsmosisClient.connection().addWorldMember(this.world.worldId(),
                        id))));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        Sprites.draw(graphics, Sprites.HEADER, this.left + 1, this.top + 1, this.popupWidth - 2, HEADER_HEIGHT - 1);
        graphics.text(this.font, this.font.plainSubstrByWidth(this.world.name(), this.popupWidth - 40), this.left + 10,
                this.top + (HEADER_HEIGHT - 8) / 2, SocialScreen.TEXT_COLOR);
        graphics.fill(this.left + NAV_WIDTH, this.top + HEADER_HEIGHT, this.left + NAV_WIDTH + 1,
                this.top + this.popupHeight - 1, 0xFF34343C);

        int infoY = this.top + this.popupHeight - 8 - 3 * 22;
        boolean hosted = this.isHosted();
        this.info(graphics, infoY, "osmosis.world.status", Component.translatable(hosted ? "osmosis.world.online"
                : "osmosis.world.offline"), hosted ? ONLINE_COLOR : OFFLINE_COLOR);
        FriendProfile self = OsmosisClient.friends().self();
        this.info(graphics, infoY + 22, "osmosis.world.host", Component.literal(hosted && self != null ? self.name()
                : "n/a"), SocialScreen.TEXT_COLOR);
        this.info(graphics, infoY + 44, "osmosis.world.version", Component.literal(this.world.version()),
                SocialScreen.TEXT_COLOR);

        if (this.tab == Tab.MEMBERS) {
            this.sectionTitle(graphics, "osmosis.world.members_list", this.contentTop());
        } else {
            String[] labels = {"osmosis.world.name", "osmosis.world.game_mode", "osmosis.world.difficulty",
                    "osmosis.world.cheats"};
            int y = this.contentTop();
            for (String label : labels) {
                Sprites.draw(graphics, Sprites.ROW, this.contentLeft(), y, this.contentRight() - this.contentLeft(),
                        ROW_HEIGHT);
                graphics.text(this.font, Component.translatable(label), this.contentLeft() + 8,
                        y + (ROW_HEIGHT - 8) / 2, SocialScreen.TEXT_COLOR);
                y += ROW_HEIGHT + 2;
            }
        }
    }

    private void info(GuiGraphicsExtractor graphics, int y, String label, Component value, int color) {
        graphics.text(this.font, Component.translatable(label), this.left + 10, y, SocialScreen.MUTED_COLOR);
        graphics.text(this.font, value, this.left + 10, y + 10, color);
    }

    private void sectionTitle(GuiGraphicsExtractor graphics, String key, int y) {
        Component title = Component.translatable(key);
        graphics.text(this.font, title, this.contentLeft(), y, SocialScreen.MUTED_COLOR);
        int lineX = this.contentLeft() + this.font.width(title) + 4;
        graphics.fill(lineX, y + 4, this.contentRight(), y + 5, 0xFF34343C);
    }

    private static final class MemberList extends PanelList<MemberRow> {
        private MemberList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, x, y, width, height, 24);
        }

        @Override
        public int getRowWidth() {
            return this.width - 4;
        }

        private void add(MemberRow row) {
            this.addEntry(row);
        }
    }

    private final class MemberRow extends ContainerObjectSelectionList.Entry<MemberRow> {
        private final FriendProfile member;
        private final boolean owner;
        private final ResolvableProfile face;
        private final @Nullable PanelButton removeButton;

        private MemberRow(FriendProfile member, boolean owner) {
            this.member = member;
            this.owner = owner;
            this.face = ResolvableProfile.createUnresolved(member.id());
            this.removeButton = owner ? null : PanelButton.icon(0, 0, 15, Sprites.sprite("menu/remove"),
                    Component.translatable("osmosis.world.remove_member"), () -> OsmosisClient.connection()
                            .removeWorldMember(WorldScreen.this.world.worldId(), member.id()));
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = WorldScreen.this.font;
            int x = this.getContentX();
            int y = this.getContentY();
            Sprites.draw(graphics, Sprites.ROW, x, y, this.getContentWidth(), this.getContentHeight());
            int faceY = y + (this.getContentHeight() - 12) / 2;
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x + 5, faceY, 12);
            graphics.text(font, this.member.name(), x + 23, y + (this.getContentHeight() - 8) / 2,
                    SocialScreen.TEXT_COLOR);
            if (this.owner) {
                Sprites.draw(graphics, Sprites.sprite("icon/crown"), x + 23 + font.width(this.member.name()) + 4,
                        y + (this.getContentHeight() - 9) / 2, 9, 9);
            }
            if (this.removeButton != null) {
                // Only shown on hover, but always placed so a click lands where the button appears.
                this.removeButton.setPosition(this.getContentRight() - 3 - 15, y + (this.getContentHeight() - 15) / 2);
                this.removeButton.visible = hovered;
                this.removeButton.extractRenderState(graphics, mouseX, mouseY, a);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return this.removeButton != null ? List.of(this.removeButton) : List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return this.removeButton != null ? List.of(this.removeButton) : List.of();
        }
    }
}
