package cc.avie.osmosis.client.chat;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A decrypted chat message.
 *
 * @param time milliseconds since the epoch, assigned by the server
 * @param text {@code null} if the message could not be decrypted
 */
public record ChatMessage(UUID sender, boolean outgoing, long time, @Nullable String text) {
}
