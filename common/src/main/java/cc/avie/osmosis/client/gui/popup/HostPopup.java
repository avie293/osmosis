package cc.avie.osmosis.client.gui.popup;

import cc.avie.osmosis.client.OsmosisClient;
import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.gui.PanelButton;
import cc.avie.osmosis.client.host.WorldHost;
import java.util.Set;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * Starts and stops hosting the current world and invites friends to it.
 */
public final class HostPopup extends OsmosisPopup {
    private static final int WIDTH = 220;
    private static final int TEXT_COLOR = 0xFFD2D2EE;

    private int statusY;

    public HostPopup(Screen parent) {
        super(parent, Component.translatable("osmosis.host.title"));
    }

    @Override
    protected void init() {
        super.init();
        WorldHost host = OsmosisClient.host();
        boolean connected = OsmosisClient.friends().status() == ConnectionStatus.CONNECTED;
        int buttonWidth = WIDTH - 2 * PADDING;
        this.statusY = this.layoutPopup(WIDTH, PADDING + 14 + 12 + 3 * 24 + PADDING - 4);
        int y = this.statusY + 12;

        PanelButton toggle = PanelButton.text(this.left + PADDING, y, buttonWidth, 20,
                Component.translatable(host.isHosting() ? "osmosis.host.stop" : "osmosis.host.start"), () -> {
                    if (host.isHosting()) {
                        host.stop();
                    } else {
                        host.start();
                    }
                    this.rebuildWidgets();
                });
        toggle.active = connected && (host.isHosting() || host.canHost());
        this.addRenderableWidget(toggle);

        PanelButton invite = PanelButton.text(this.left + PADDING, y + 24, buttonWidth, 20,
                Component.translatable("osmosis.host.invite"), this::openInvite);
        invite.active = connected && host.isHosting();
        this.addRenderableWidget(invite);

        this.addRenderableWidget(PanelButton.text(this.left + PADDING, y + 48, buttonWidth, 20,
                CommonComponents.GUI_DONE, this::onClose));
    }

    private void openInvite() {
        this.minecraft.gui.setScreen(new MemberPickerPopup(this, Component.translatable("osmosis.host.invite"),
                MemberPickerPopup.candidates(OsmosisClient.friends().friends(), Set.of()), Integer.MAX_VALUE, false,
                (name, picked) -> picked.forEach(OsmosisClient.connection()::invite)));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        String world = OsmosisClient.host().world();
        Component status = world != null ? Component.translatable("osmosis.host.hosting", world)
                : Component.translatable("osmosis.host.not_hosting");
        graphics.centeredText(this.font, status, this.left + WIDTH / 2, this.statusY, TEXT_COLOR);
    }
}
