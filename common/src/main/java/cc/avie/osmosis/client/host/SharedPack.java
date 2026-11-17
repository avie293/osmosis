package cc.avie.osmosis.client.host;

import java.util.HexFormat;

/**
 * A resource pack a host shares with the guests of their world.
 *
 * @param hash the SHA-1 of the pack's zip file
 */
public record SharedPack(byte[] hash, String name, long size) {
    public String hex() {
        return HexFormat.of().formatHex(this.hash);
    }
}
