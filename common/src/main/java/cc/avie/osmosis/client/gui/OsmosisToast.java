package cc.avie.osmosis.client.gui;

import cc.avie.osmosis.Osmosis;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.component.ResolvableProfile;
import org.jspecify.annotations.Nullable;

/**
 * Notification in the top right corner, showing the face of the player it is about if there is one.
 */
public final class OsmosisToast implements Toast {
    public static final Component TITLE = Component.translatable("osmosis.name");
    private static final Identifier BACKGROUND_SPRITE = Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, "toast");
    private static final Identifier ICON_SPRITE = Identifier.fromNamespaceAndPath(Osmosis.MOD_ID, "icon/friends");
    private static final long DISPLAY_TIME_MS = 5000L;
    private static final int TITLE_COLOR = 0xFFFFFF00;
    private static final int MESSAGE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_X = 30;

    private final Component title;
    private final @Nullable Component message;
    private final @Nullable ResolvableProfile face;
    private final int width;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;

    private OsmosisToast(Component title, @Nullable Component message, @Nullable UUID player) {
        this.title = title;
        this.message = message;
        this.face = player != null ? ResolvableProfile.createUnresolved(player) : null;
        Font font = Minecraft.getInstance().font;
        int textWidth = Math.max(font.width(title), message != null ? font.width(message) : 0);
        this.width = Math.max(Toast.DEFAULT_WIDTH, TEXT_X + textWidth + 8);
    }

    public static void show(Component title, @Nullable Component message, @Nullable UUID player) {
        Minecraft.getInstance().gui.toastManager().addToast(new OsmosisToast(title, message, player));
    }

    @Override
    public int width() {
        return this.width;
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (fullyVisibleForMs >= DISPLAY_TIME_MS * manager.getNotificationDisplayTimeMultiplier()) {
            this.visibility = Toast.Visibility.HIDE;
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE, 0, 0, this.width(), this.height());
        if (this.face != null) {
            PlayerFaceExtractor.extractRenderState(graphics, this.face, 8, 8, 16);
        } else {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, ICON_SPRITE, 8, 8, 16, 16);
        }
        if (this.message == null) {
            graphics.text(font, this.title, TEXT_X, 12, TITLE_COLOR, false);
        } else {
            graphics.text(font, this.title, TEXT_X, 7, TITLE_COLOR, false);
            graphics.text(font, this.message, TEXT_X, 18, MESSAGE_COLOR, false);
        }
    }
}
