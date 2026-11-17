package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.gui.popup.OsmosisPopup;
import cc.avie.osmosis.client.host.WorldInfo;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;

/**
 * Picks the singleplayer world to host, from the title screen.
 */
public final class WorldSelectScreen extends OsmosisPopup {
    private static final int WIDTH = 240;
    private static final int ROW_HEIGHT = 40;
    private static final int ICON_SIZE = 32;
    private static final int CLOSE_SIZE = 15;

    private final List<FaviconTexture> icons = new ArrayList<>();
    private @Nullable List<LevelSummary> levels;
    private @Nullable LevelSummary selected;
    private String filter = "";
    private @Nullable WorldList list;
    private @Nullable PanelButton nextButton;

    public WorldSelectScreen(Screen parent) {
        super(parent, Component.empty());
    }

    @Override
    public void added() {
        super.added();
        if (this.levels == null) {
            this.loadLevels();
        }
    }

    private void loadLevels() {
        try {
            var candidates = this.minecraft.getLevelSource().findLevelCandidates();
            this.minecraft.getLevelSource().loadLevelSummaries(candidates).thenAcceptAsync(levels -> {
                this.levels = levels;
                this.fillList();
            }, this.minecraft);
        } catch (LevelStorageException e) {
            Osmosis.LOGGER.error("Could not list the worlds", e);
            this.levels = List.of();
        }
    }

    @Override
    protected void init() {
        super.init();
        int height = Math.min(this.height - 40, 300);
        int y = this.layoutPopup(WIDTH, height);
        int inner = WIDTH - 2 * PADDING;
        this.addRenderableWidget(PanelButton.icon(this.left + WIDTH - PADDING - CLOSE_SIZE, this.top + PADDING - 4,
                CLOSE_SIZE, Sprites.sprite("icon/close"), Component.translatable("gui.cancel"), this::onClose));

        EditBox search = new EditBox(this.font, this.left + PADDING, y, inner, 18,
                Component.translatable("osmosis.search"));
        search.setHint(Component.translatable("osmosis.search"));
        search.setValue(this.filter);
        search.setResponder(value -> {
            this.filter = value.strip().toLowerCase(Locale.ROOT);
            this.fillList();
        });
        this.addRenderableWidget(search);

        int listTop = y + 24;
        int listBottom = this.top + height - PADDING - 26;
        this.list = this.addRenderableWidget(new WorldList(this.minecraft, this.left + 2, listTop, WIDTH - 4,
                listBottom - listTop));
        this.nextButton = this.addRenderableWidget(PanelButton.text(this.left + PADDING, listBottom + 6, inner, 20,
                Component.translatable("osmosis.host.next"), this::next).setStyle(PanelButton.Style.ACCENT));
        this.fillList();
    }

    private void fillList() {
        if (this.list == null) {
            return;
        }
        this.list.replace();
        if (this.nextButton != null) {
            this.nextButton.active = this.selected != null;
        }
    }

    private void next() {
        if (this.selected != null) {
            this.minecraft.gui.setScreen(new WorldScreen(this, WorldInfo.of(this.selected)));
        }
    }

    @Override
    public void removed() {
        this.icons.forEach(FaviconTexture::close);
        this.icons.clear();
        super.removed();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        graphics.text(this.font, Component.translatable("osmosis.host.select"), this.left + PADDING, this.top + PADDING,
                SocialScreen.TEXT_COLOR);
    }

    private final class WorldList extends PanelList<Row> {
        private WorldList(Minecraft minecraft, int x, int y, int width, int height) {
            super(minecraft, x, y, width, height, ROW_HEIGHT);
        }

        private void replace() {
            WorldSelectScreen screen = WorldSelectScreen.this;
            screen.icons.forEach(FaviconTexture::close);
            screen.icons.clear();
            this.clearEntries();
            this.addEntry(new CreateRow(), 28);
            if (screen.levels == null) {
                return;
            }
            List<LevelSummary> levels = new ArrayList<>();
            for (LevelSummary level : screen.levels) {
                if (!level.isDisabled() && (screen.filter.isEmpty()
                        || level.getLevelName().toLowerCase(Locale.ROOT).contains(screen.filter))) {
                    levels.add(level);
                }
            }
            levels.sort(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed());
            for (LevelSummary level : levels) {
                this.addEntry(new WorldRow(level));
            }
        }
    }

    private abstract static class Row extends ContainerObjectSelectionList.Entry<Row> {
        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    private final class CreateRow extends Row {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Sprites.draw(graphics, hovered ? Sprites.BUTTON_HOVERED : Sprites.BUTTON, this.getContentX(),
                    this.getContentY(), this.getContentWidth(), this.getContentHeight());
            graphics.centeredText(WorldSelectScreen.this.font, Component.translatable("osmosis.host.create_world"),
                    this.getContentXMiddle(), this.getContentY() + (this.getContentHeight() - 8) / 2,
                    SocialScreen.TEXT_COLOR);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            WorldSelectScreen screen = WorldSelectScreen.this;
            CreateWorldScreen.openFresh(screen.minecraft, () -> screen.minecraft.gui.setScreen(screen));
            return true;
        }
    }

    private final class WorldRow extends Row {
        private final LevelSummary level;
        private final FaviconTexture icon;
        private final boolean hasIcon;
        private final Component info;

        private WorldRow(LevelSummary level) {
            this.level = level;
            this.icon = FaviconTexture.forWorld(WorldSelectScreen.this.minecraft.getTextureManager(), level.getLevelId());
            WorldSelectScreen.this.icons.add(this.icon);
            this.hasIcon = this.loadIcon(level.getIcon());
            this.info = Component.translatable("osmosis.host.world_info", level.levelVersion().minecraftVersionName(),
                    TimeText.ago(level.getLastPlayed()));
        }

        private boolean loadIcon(@Nullable Path file) {
            if (file == null || !Files.isRegularFile(file)) {
                return false;
            }
            try (InputStream stream = Files.newInputStream(file)) {
                this.icon.upload(NativeImage.read(stream));
                return true;
            } catch (Exception e) {
                Osmosis.LOGGER.debug("Invalid icon for world {}", this.level.getLevelId(), e);
                return false;
            }
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = WorldSelectScreen.this.font;
            int x = this.getContentX();
            int y = this.getContentY();
            boolean selected = this.level == WorldSelectScreen.this.selected;
            Sprites.draw(graphics, selected ? Sprites.ROW_SELECTED : hovered ? Sprites.ROW_HOVERED : Sprites.ROW, x, y,
                    this.getContentWidth(), this.getContentHeight());
            int iconY = y + (this.getContentHeight() - ICON_SIZE) / 2;
            if (this.hasIcon) {
                graphics.blit(RenderPipelines.GUI_TEXTURED, this.icon.textureLocation(), x + 2, iconY, 0.0F, 0.0F,
                        ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
            } else {
                Sprites.draw(graphics, Sprites.sprite("icon/world"), x + 2, iconY, ICON_SIZE, ICON_SIZE);
            }
            int textX = x + 2 + ICON_SIZE + 6;
            int textWidth = this.getContentRight() - 4 - textX;
            graphics.text(font, font.plainSubstrByWidth(this.level.getLevelName(), textWidth), textX, y + 7,
                    SocialScreen.TEXT_COLOR);
            graphics.text(font, font.split(this.info, textWidth).getFirst(), textX, y + 19, SocialScreen.MUTED_COLOR);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            WorldSelectScreen screen = WorldSelectScreen.this;
            screen.selected = this.level;
            if (screen.nextButton != null) {
                screen.nextButton.active = true;
            }
            if (doubleClick) {
                screen.next();
            }
            return true;
        }
    }
}
