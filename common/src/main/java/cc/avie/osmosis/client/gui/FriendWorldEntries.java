package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.HostedWorld;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.component.ResolvableProfile;

/**
 * The "Friends' worlds" section of the multiplayer server list: worlds friends host right now that we may join.
 */
public final class FriendWorldEntries {
    private static final Component HEADER = Component.translatable("osmosis.servers.header");
    private static final int ICON_SIZE = 32;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int MUTED_COLOR = 0xFF808080;
    private static final int HEADER_COLOR = 0xFFFFFF55;

    private FriendWorldEntries() {
    }

    /**
     * @return the vanilla entries followed by the friends' worlds section, if there are any
     */
    public static List<ServerSelectionList.Entry> append(Collection<ServerSelectionList.Entry> entries) {
        List<ServerSelectionList.Entry> result = new ArrayList<>(entries);
        List<HostedWorld> worlds = new ArrayList<>();
        for (HostedWorld world : OsmosisClient.friends().hostedWorlds()) {
            if (world.invited()) {
                worlds.add(world);
            }
        }
        if (!worlds.isEmpty()) {
            worlds.sort(Comparator.comparing((HostedWorld w) -> w.host().name(), String.CASE_INSENSITIVE_ORDER));
            result.add(new Header());
            for (HostedWorld world : worlds) {
                result.add(new WorldEntry(world));
            }
        }
        return result;
    }

    private static final class Header extends ServerSelectionList.Entry {
        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            graphics.centeredText(font, HEADER, this.getContentXMiddle(),
                    this.getContentY() + (this.getContentHeight() - 8) / 2, HEADER_COLOR);
        }

        @Override
        public Component getNarration() {
            return HEADER;
        }

        @Override
        protected boolean matches(ServerSelectionList.Entry other) {
            return other instanceof Header;
        }

        @Override
        public void join() {
        }
    }

    private static final class WorldEntry extends ServerSelectionList.Entry {
        private final HostedWorld world;
        private final ResolvableProfile face;
        private final Component hostedBy;

        private WorldEntry(HostedWorld world) {
            this.world = world;
            this.face = ResolvableProfile.createUnresolved(world.host().id());
            this.hostedBy = Component.translatable("osmosis.servers.hosted_by", world.host().name());
        }

        @Override
        public void extractContent(GuiGraphicsExtractor graphics, int mouseX, int mouseY, boolean hovered, float a) {
            Font font = Minecraft.getInstance().font;
            int x = this.getContentX();
            int y = this.getContentY();
            PlayerFaceExtractor.extractRenderState(graphics, this.face, x, y, ICON_SIZE);
            int textX = x + ICON_SIZE + 3;
            graphics.text(font, font.plainSubstrByWidth(this.world.world(), this.getContentRight() - textX), textX,
                    y + 1, TEXT_COLOR);
            graphics.text(font, this.hostedBy, textX, y + 12, MUTED_COLOR);
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (doubleClick) {
                this.join();
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean keyPressed(KeyEvent event) {
            if (event.isSelection()) {
                this.join();
                return true;
            }
            return super.keyPressed(event);
        }

        @Override
        public void join() {
            OsmosisClient.host().join(this.world);
        }

        @Override
        public Component getNarration() {
            return Component.translatable("narrator.select",
                    Component.literal(this.world.world()).append(", ").append(this.hostedBy));
        }

        @Override
        protected boolean matches(ServerSelectionList.Entry other) {
            return other instanceof WorldEntry entry && entry.world.host().id().equals(this.world.host().id());
        }
    }
}
