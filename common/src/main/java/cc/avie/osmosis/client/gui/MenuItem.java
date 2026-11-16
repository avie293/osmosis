package cc.avie.osmosis.client.gui;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * One entry of a dropdown menu.
 */
public record MenuItem(Identifier icon, Component label, Runnable action) {
}
