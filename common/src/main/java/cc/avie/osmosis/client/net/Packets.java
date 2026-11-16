package cc.avie.osmosis.client.net;

import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendProfile;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet ids and field helpers of the Osmosis protocol. The server side is {@code server/src/protocol.rs}, which
 * documents the framing.
 */
final class Packets {
    static final int PROTOCOL_VERSION = 1;
    static final int MAX_NAME_LENGTH = 16;
    static final int MAX_SERVER_ID_LENGTH = 64;

    static final int HELLO = 0;
    static final int AUTHENTICATE = 1;
    static final int SEND_REQUEST = 2;
    static final int ACCEPT_REQUEST = 3;
    static final int DECLINE_REQUEST = 4;
    static final int CANCEL_REQUEST = 5;
    static final int REMOVE_FRIEND = 6;
    static final int PING = 7;

    static final int CHALLENGE = 0;
    static final int WELCOME = 1;
    static final int DISCONNECT = 2;
    static final int FRIEND_ADDED = 3;
    static final int FRIEND_REMOVED = 4;
    static final int REQUEST_RECEIVED = 5;
    static final int REQUEST_SENT = 6;
    static final int REQUEST_REMOVED = 7;
    static final int PRESENCE = 8;
    static final int ERROR = 9;
    static final int PONG = 10;

    /** Indexed by the server's {@code DisconnectReason}. */
    private static final ConnectionStatus[] DISCONNECT_REASONS = {
            ConnectionStatus.OUTDATED_CLIENT,
            ConnectionStatus.OUTDATED_SERVER,
            ConnectionStatus.AUTHENTICATION_FAILED,
            ConnectionStatus.LOGGED_IN_ELSEWHERE,
            ConnectionStatus.UNREACHABLE,
            ConnectionStatus.UNREACHABLE
    };

    /** Indexed by the server's {@code ErrorCode}, used as {@code osmosis.error.<key>}. */
    private static final String[] ERROR_KEYS = {
            "player_not_found",
            "cannot_add_self",
            "already_friends",
            "already_requested",
            "friend_limit",
            "request_limit",
            "target_limit"
    };

    private Packets() {
    }

    static FriendlyByteBuf create(int id) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeByte(id);
        return buf;
    }

    static FriendProfile readProfile(FriendlyByteBuf buf) {
        return new FriendProfile(buf.readUUID(), buf.readUtf(MAX_NAME_LENGTH));
    }

    static ConnectionStatus disconnectStatus(int reason) {
        return reason < DISCONNECT_REASONS.length ? DISCONNECT_REASONS[reason] : ConnectionStatus.UNREACHABLE;
    }

    static String errorKey(int code) {
        return "osmosis.error." + (code < ERROR_KEYS.length ? ERROR_KEYS[code] : "unknown");
    }
}
