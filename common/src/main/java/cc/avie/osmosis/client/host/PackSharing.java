package cc.avie.osmosis.client.host;

import cc.avie.osmosis.Osmosis;
import cc.avie.osmosis.client.OsmosisClient;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Resource packs a host shares with the guests of their world.
 *
 * <p>The host picks zip packs from their resource pack folder per world. Guests download them through the relay's
 * tunnel into a cache keyed by hash, and Minecraft applies them like the packs a server sends. Only touched on the
 * render thread.
 */
public final class PackSharing {
    private static final String SHARED_FILE = "osmosis-packs.txt";
    private static final int MAX_SHARED_PACKS = 20;

    /** The host's announced packs by hash, so uploads only ever send these files. */
    private final Map<String, Path> hosted = new HashMap<>();
    /** Bumped whenever the hosted packs change, so an older hashing run that finishes late is dropped. */
    private int hostedVersion;

    private final Map<String, SharedPack> downloading = new HashMap<>();
    private final List<Path> toApply = new ArrayList<>();
    /** The connection to the host's world the packs are for. */
    private @Nullable ServerData joining;

    /**
     * @return the zip packs in the resource pack folder, by file name
     */
    public static List<Path> availablePacks() {
        List<Path> packs = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(Minecraft.getInstance().getResourcePackDirectory(),
                "*.zip")) {
            for (Path file : files) {
                if (Files.isRegularFile(file)) {
                    packs.add(file);
                }
            }
        } catch (IOException e) {
            Osmosis.LOGGER.warn("Could not list the resource packs", e);
        }
        packs.sort(Comparator.comparing(file -> file.getFileName().toString(), String.CASE_INSENSITIVE_ORDER));
        return packs;
    }

    /**
     * @return the file names of the packs shared for a world
     */
    public static Set<String> sharedPacks(Path worldFolder) {
        Path file = worldFolder.resolve(SHARED_FILE);
        Set<String> names = new LinkedHashSet<>();
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    if (!line.isBlank()) {
                        names.add(line.strip());
                    }
                }
            }
        } catch (IOException e) {
            Osmosis.LOGGER.warn("Could not read {}", file, e);
        }
        return names;
    }

    public static boolean canShareMore(Set<String> shared) {
        return shared.size() < MAX_SHARED_PACKS;
    }

    /**
     * Saves which packs a world shares, and announces them right away if that world is being hosted.
     */
    public void setSharedPacks(Path worldFolder, UUID worldId, Set<String> names) {
        Path file = worldFolder.resolve(SHARED_FILE);
        try {
            if (names.isEmpty()) {
                Files.deleteIfExists(file);
            } else {
                Files.write(file, names, StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Osmosis.LOGGER.warn("Could not save the shared packs to {}", file, e);
        }
        if (worldId.equals(OsmosisClient.host().worldId())) {
            this.announce(worldFolder);
        }
    }

    /**
     * Hashes the world's shared packs off the render thread and tells the relay about them.
     */
    void announce(Path worldFolder) {
        int version = ++this.hostedVersion;
        Path packFolder = Minecraft.getInstance().getResourcePackDirectory();
        Set<String> names = sharedPacks(worldFolder);
        CompletableFuture.supplyAsync(() -> {
            Map<String, Path> files = new HashMap<>();
            List<SharedPack> packs = new ArrayList<>();
            for (String name : names) {
                Path file = packFolder.resolve(name);
                // Names come from a file in the world folder, so they may only point at packs in the pack folder.
                if (!file.normalize().getParent().equals(packFolder.normalize()) || !Files.isRegularFile(file)) {
                    continue;
                }
                try {
                    SharedPack pack = new SharedPack(sha1(file), displayName(file), Files.size(file));
                    packs.add(pack);
                    files.put(pack.hex(), file);
                } catch (IOException e) {
                    Osmosis.LOGGER.warn("Could not read the resource pack {}", file, e);
                }
            }
            return Map.entry(files, packs);
        }, Util.ioPool()).thenAcceptAsync(result -> {
            if (version != this.hostedVersion || !OsmosisClient.host().isHosting()) {
                return;
            }
            this.hosted.clear();
            this.hosted.putAll(result.getKey());
            OsmosisClient.connection().setHostPacks(result.getValue());
        }, Minecraft.getInstance());
    }

    void stopHosting() {
        this.hostedVersion++;
        this.hosted.clear();
    }

    public static String displayName(Path file) {
        String name = file.getFileName().toString();
        return name.endsWith(".zip") ? name.substring(0, name.length() - 4) : name;
    }

    private static byte[] sha1(Path file) throws IOException {
        try (DigestInputStream in = new DigestInputStream(Files.newInputStream(file),
                MessageDigest.getInstance("SHA-1"))) {
            in.transferTo(OutputStream.nullOutputStream());
            return in.getMessageDigest().digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public void onUpload(byte[] hash, byte[] ticket, int tunnelPort) {
        Path file = this.hosted.get(HexFormat.of().formatHex(hash));
        if (file == null) {
            return;
        }
        OsmosisClient.connection().sendPack(ticket, tunnelPort, file).exceptionally(error -> {
            Osmosis.LOGGER.warn("Could not send the resource pack {}", file, error);
            return null;
        });
    }

    /**
     * Called when joining a world, with the packs its host shares. Cached packs apply right away, the others once they
     * downloaded.
     */
    /** Forgets the packs of the previous join, before the packs of a new one arrive. */
    void reset() {
        this.joining = null;
        this.downloading.clear();
        this.toApply.clear();
    }

    void joining(ServerData server) {
        this.joining = server;
    }

    public void onHostPacks(UUID host, List<SharedPack> packs) {
        for (SharedPack pack : packs) {
            Path cached = cacheFile(pack);
            if (Files.isRegularFile(cached)) {
                this.toApply.add(cached);
            } else {
                this.downloading.put(pack.hex(), pack);
                OsmosisClient.connection().requestPack(host, pack.hash());
            }
        }
    }

    public void onDownload(byte[] hash, byte[] ticket, int tunnelPort) {
        SharedPack pack = this.downloading.get(HexFormat.of().formatHex(hash));
        if (pack == null) {
            return;
        }
        Path target = cacheFile(pack);
        OsmosisClient.connection().receivePack(ticket, tunnelPort, target, pack).whenCompleteAsync((ignored, error) -> {
            if (this.downloading.remove(pack.hex()) == null) {
                return;
            }
            if (error != null) {
                Osmosis.LOGGER.warn("Could not download the shared resource pack {}", pack.name(), error);
                return;
            }
            this.toApply.add(target);
        }, Minecraft.getInstance());
    }

    private static Path cacheFile(SharedPack pack) {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("osmosis").resolve("packs")
                .resolve(pack.hex() + ".zip");
    }

    /**
     * Applies downloaded packs once the player is in the host's world. Leaving the world removes them again, as
     * Minecraft does with every server pack.
     */
    void tick() {
        Minecraft minecraft = Minecraft.getInstance();
        if (this.toApply.isEmpty() || minecraft.getConnection() == null) {
            return;
        }
        if (minecraft.getCurrentServer() != this.joining) {
            this.toApply.clear();
            return;
        }
        for (Path pack : this.toApply) {
            minecraft.getDownloadedPackSource().pushLocalPack(UUID.nameUUIDFromBytes(
                    pack.getFileName().toString().getBytes(StandardCharsets.UTF_8)), pack);
        }
        this.toApply.clear();
        minecraft.getDownloadedPackSource().allowServerPacks();
    }
}
