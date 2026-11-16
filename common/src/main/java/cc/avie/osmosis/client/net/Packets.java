package cc.avie.osmosis.client.net;

import cc.avie.osmosis.client.friend.ConnectionStatus;
import cc.avie.osmosis.client.friend.FriendProfile;
import io.netty.buffer.Unpooled;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;

/**
 * Packet ids and field helpers of the Osmosis protocol. The server side is {@code server/src/protocol.rs}, which
 * documents the framing.
 */
final class Packets {
    static final int PROTOCOL_VERSION = 5;
    static final int MAX_NAME_LENGTH = 16;
    static final int MAX_GROUP_NAME_LENGTH = 24;
    static final int MAX_WORLD_NAME_LENGTH = 64;
    static final int MAX_SERVER_ID_LENGTH = 64;
    static final int MAX_KEY_LENGTH = 64;
    static final int MAX_CIPHERTEXT_LENGTH = 4096;
    static final int HISTORY_PAGE_SIZE = 50;

    static final int HELLO = 0;
    static final int AUTHENTICATE = 1;
    static final int SEND_REQUEST = 2;
    static final int ACCEPT_REQUEST = 3;
    static final int DECLINE_REQUEST = 4;
    static final int CANCEL_REQUEST = 5;
    static final int REMOVE_FRIEND = 6;
    static final int PING = 7;
    static final int SEND_MESSAGE = 8;
    static final int REQUEST_HISTORY = 9;
    static final int MARK_READ = 10;
    static final int BLOCK = 11;
    static final int UNBLOCK = 12;
    static final int DELETE_CHAT = 13;
    static final int CREATE_GROUP = 14;
    static final int RENAME_GROUP = 15;
    static final int ADD_GROUP_MEMBER = 16;
    static final int REMOVE_GROUP_MEMBER = 17;
    static final int LEAVE_GROUP = 18;
    static final int SEND_GROUP_MESSAGE = 19;
    static final int REQUEST_GROUP_HISTORY = 20;
    static final int MARK_GROUP_READ = 21;
    static final int DELETE_GROUP_CHAT = 22;
    static final int START_HOSTING = 23;
    static final int STOP_HOSTING = 24;
    static final int INVITE = 25;
    static final int JOIN_WORLD = 26;

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
    static final int MESSAGE = 11;
    static final int HISTORY = 12;
    static final int FRIEND_KEY = 13;
    static final int BLOCKED = 14;
    static final int UNBLOCKED = 15;
    static final int GROUP_UPDATED = 16;
    static final int GROUP_REMOVED = 17;
    static final int GROUP_MESSAGE = 18;
    static final int GROUP_HISTORY = 19;
    static final int HOSTING = 20;
    static final int JOIN_TICKET = 21;
    static final int GUEST_JOINING = 22;

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
            "target_limit",
            "not_friends",
            "group_limit",
            "group_full",
            "not_group_owner",
            "invalid_group_name",
            "player_blocked",
            "not_invited",
            "not_hosting"
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

    static List<FriendProfile> readProfiles(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<FriendProfile> profiles = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            profiles.add(readProfile(buf));
        }
        return profiles;
    }

    static ConnectionStatus disconnectStatus(int reason) {
        return reason < DISCONNECT_REASONS.length ? DISCONNECT_REASONS[reason] : ConnectionStatus.UNREACHABLE;
    }

    static String errorKey(int code) {
        return "osmosis.error." + (code < ERROR_KEYS.length ? ERROR_KEYS[code] : "unknown");
    }
}
