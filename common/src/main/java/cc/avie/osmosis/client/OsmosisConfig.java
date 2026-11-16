package cc.avie.osmosis.client;

import cc.avie.osmosis.Osmosis;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client settings, stored as {@code config/osmosis.properties}.
 */
public record OsmosisConfig(String serverHost, int serverPort, boolean onlineToasts) {
    private static final String FILE_NAME = "osmosis.properties";
    private static final OsmosisConfig DEFAULTS = new OsmosisConfig("localhost", 25580, true);

    public static OsmosisConfig load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (Files.notExists(file)) {
            DEFAULTS.save(file);
            return DEFAULTS;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            Osmosis.LOGGER.warn("Could not read {}, using the default settings", file, e);
            return DEFAULTS;
        }

        String host = properties.getProperty("server_host", DEFAULTS.serverHost).trim();
        int port = parsePort(properties.getProperty("server_port"));
        boolean onlineToasts = !"false".equalsIgnoreCase(properties.getProperty("online_toasts", "true").trim());
        return new OsmosisConfig(host.isEmpty() ? DEFAULTS.serverHost : host, port, onlineToasts);
    }

    private static int parsePort(String value) {
        if (value != null) {
            try {
                int port = Integer.parseInt(value.trim());
                if (port > 0 && port <= 0xFFFF) {
                    return port;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        return DEFAULTS.serverPort;
    }

    private void save(Path file) {
        String content = """
                # Osmosis client settings

                # Address of the Osmosis server
                server_host=%s
                server_port=%d

                # Show a notification when a friend comes online
                online_toasts=%b
                """.formatted(this.serverHost, this.serverPort, this.onlineToasts);
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            Osmosis.LOGGER.warn("Could not write {}", file, e);
        }
    }
}
