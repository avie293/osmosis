package cc.avie.osmosis.client.friend;

import net.minecraft.network.chat.Component;

public enum ConnectionStatus {
    CONNECTING("connecting", true),
    CONNECTED("connected", true),
    UNREACHABLE("unreachable", true),
    OUTDATED_CLIENT("outdated_client", false),
    OUTDATED_SERVER("outdated_server", false),
    AUTHENTICATION_FAILED("authentication_failed", false),
    LOGGED_IN_ELSEWHERE("logged_in_elsewhere", false);

    private final String key;
    private final boolean reconnects;

    ConnectionStatus(String key, boolean reconnects) {
        this.key = "osmosis.status." + key;
        this.reconnects = reconnects;
    }

    /**
     * @return whether the client keeps trying to reconnect in this state
     */
    public boolean reconnects() {
        return this.reconnects;
    }

    public Component describe() {
        return Component.translatable(this.key);
    }
}
