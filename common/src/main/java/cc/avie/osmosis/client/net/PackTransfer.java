package cc.avie.osmosis.client.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import net.minecraft.util.Util;

/**
 * Sends a shared resource pack from the host to a guest through the relay's tunnel port, using a ticket like a
 * world connection does. The stream is the plain zip file, ended by closing the connection.
 */
final class PackTransfer {
    private static final byte ROLE_HOST = 0;
    private static final byte ROLE_GUEST = 1;
    private static final int CONNECT_TIMEOUT_MILLIS = 5000;
    /** The guest waits this long for the host to start sending. */
    private static final int READ_TIMEOUT_MILLIS = 30000;

    private PackTransfer() {
    }

    static CompletableFuture<Void> send(InetAddress relay, int tunnelPort, byte[] ticket, Path pack) {
        return CompletableFuture.runAsync(() -> {
            try (Socket socket = open(relay, tunnelPort, ROLE_HOST, ticket);
                 OutputStream out = socket.getOutputStream()) {
                Files.copy(pack, out);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, Util.ioPool());
    }

    /**
     * Receives a pack into {@code target}, which only appears once the whole file arrived with the expected hash.
     */
    static CompletableFuture<Void> receive(InetAddress relay, int tunnelPort, byte[] ticket, Path target,
                                           byte[] sha1, long size) {
        return CompletableFuture.runAsync(() -> {
            Path partial = target.resolveSibling(target.getFileName() + ".part");
            try (Socket socket = open(relay, tunnelPort, ROLE_GUEST, ticket);
                 DigestInputStream in = new DigestInputStream(socket.getInputStream(),
                         MessageDigest.getInstance("SHA-1"))) {
                Files.createDirectories(target.getParent());
                long copied = copy(in, partial, size);
                if (copied != size || !Arrays.equals(in.getMessageDigest().digest(), sha1)) {
                    throw new IOException("The pack did not match what the host announced");
                }
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException | NoSuchAlgorithmException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    Files.deleteIfExists(partial);
                } catch (IOException ignored) {
                    // A leftover partial file is replaced by the next download of the same pack.
                }
            }
        }, Util.ioPool());
    }

    /**
     * Stops one byte after the announced size, which is enough to notice a host sending more than it said.
     */
    private static long copy(InputStream in, Path file, long size) throws IOException {
        byte[] buffer = new byte[8192];
        long copied = 0;
        try (OutputStream out = Files.newOutputStream(file)) {
            int read;
            while (copied <= size && (read = in.read(buffer, 0, (int) Math.min(buffer.length, size + 1 - copied))) > 0) {
                out.write(buffer, 0, read);
                copied += read;
            }
        }
        return copied;
    }

    private static Socket open(InetAddress relay, int tunnelPort, byte role, byte[] ticket) throws IOException {
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(relay, tunnelPort), CONNECT_TIMEOUT_MILLIS);
            socket.setSoTimeout(READ_TIMEOUT_MILLIS);
            OutputStream out = socket.getOutputStream();
            out.write(role);
            out.write(ticket);
            out.flush();
            return socket;
        } catch (IOException e) {
            socket.close();
            throw e;
        }
    }
}
