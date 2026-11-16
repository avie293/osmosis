package cc.avie.osmosis.client.chat;

import cc.avie.osmosis.Osmosis;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import org.jspecify.annotations.Nullable;

/**
 * End-to-end encryption for chat messages.
 *
 * <p>Each account has an X25519 key pair that never leaves this computer; the server only ever sees the public half.
 * Two friends derive the same AES-256-GCM key from their key pairs with HKDF-SHA256, salted with both UUIDs. Every
 * message gets a random nonce, and the sender, the recipient and the group, if any, are authenticated along with it,
 * so a message cannot be passed off as coming from someone else or be moved into another chat. Group messages are
 * encrypted once per member this way, the sender included. Plaintext is compressed before it is encrypted.
 *
 * <p>Safe to use from any thread.
 */
public final class ChatCrypto {
    public static final int NONCE_LENGTH = 12;
    /** Limit for decompressed messages, well above the longest message the chat screen accepts. */
    private static final int MAX_PLAINTEXT_BYTES = 4096;
    private static final int FORMAT_VERSION = 1;
    /** Lowest bit of the first plaintext byte; the bits above it hold the message kind. */
    private static final int DEFLATED = 1;
    private static final byte[] KDF_INFO = "osmosis chat v1".getBytes(StandardCharsets.US_ASCII);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UUID self;
    private final KeyPair identity;
    private final Map<UUID, byte[]> friendKeys = new ConcurrentHashMap<>();
    private final Map<UUID, SecretKey> sharedKeys = new ConcurrentHashMap<>();

    private ChatCrypto(UUID self, KeyPair identity) {
        this.self = self;
        this.identity = identity;
        // Group messages include a copy for the sender, encrypted with the key shared with themselves.
        this.friendKeys.put(self, identity.getPublic().getEncoded());
    }

    /**
     * Loads the key pair of an account, or creates and saves a new one. Losing it makes the stored history
     * unreadable, so a broken file is only replaced after logging why.
     */
    public static ChatCrypto loadOrCreate(Path file, UUID self) {
        if (Files.exists(file)) {
            try {
                return new ChatCrypto(self, read(file));
            } catch (IOException | GeneralSecurityException e) {
                Osmosis.LOGGER.error("Could not read the chat key {}, creating a new one", file, e);
            }
        }
        try {
            KeyPair identity = KeyPairGenerator.getInstance("X25519").generateKeyPair();
            write(file, identity);
            return new ChatCrypto(self, identity);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("X25519 is not available", e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save the chat key to " + file, e);
        }
    }

    private static KeyPair read(Path file) throws IOException, GeneralSecurityException {
        try (DataInputStream in = new DataInputStream(Files.newInputStream(file))) {
            if (in.readUnsignedByte() != FORMAT_VERSION) {
                throw new IOException("Unknown key file version");
            }
            byte[] privateKey = in.readNBytes(in.readUnsignedShort());
            byte[] publicKey = in.readNBytes(in.readUnsignedShort());
            KeyFactory factory = KeyFactory.getInstance("X25519");
            return new KeyPair(factory.generatePublic(new X509EncodedKeySpec(publicKey)),
                    factory.generatePrivate(new PKCS8EncodedKeySpec(privateKey)));
        }
    }

    private static void write(Path file, KeyPair identity) throws IOException {
        Files.createDirectories(file.getParent());
        try (DataOutputStream out = new DataOutputStream(Files.newOutputStream(file))) {
            byte[] privateKey = identity.getPrivate().getEncoded();
            byte[] publicKey = identity.getPublic().getEncoded();
            out.writeByte(FORMAT_VERSION);
            out.writeShort(privateKey.length);
            out.write(privateKey);
            out.writeShort(publicKey.length);
            out.write(publicKey);
        }
    }

    /**
     * @return the public key in X.509 encoding, as sent to the server
     */
    public byte[] publicKey() {
        return this.identity.getPublic().getEncoded();
    }

    public void setFriendKey(UUID friend, byte[] key) {
        if (key.length == 0 || friend.equals(this.self)) {
            return;
        }
        byte[] previous = this.friendKeys.put(friend, key);
        if (previous != null && !Arrays.equals(previous, key)) {
            this.sharedKeys.remove(friend);
        }
    }

    /**
     * A decrypted message.
     *
     * @param kind what the text is, see {@link ChatMessage#TEXT} and {@link ChatMessage#INVITE}
     */
    public record Plaintext(int kind, String text) {
    }

    /**
     * @param group the group the message belongs to, or 0 for a direct message
     * @param kind what the text is, see {@link ChatMessage#TEXT} and {@link ChatMessage#INVITE}
     * @return the nonce followed by the ciphertext, or {@code null} if the recipient's key is unknown or unusable
     */
    public byte @Nullable [] encrypt(UUID recipient, long group, int kind, String text) {
        SecretKey key = this.sharedKey(recipient);
        if (key == null) {
            return null;
        }
        byte[] nonce = new byte[NONCE_LENGTH];
        RANDOM.nextBytes(nonce);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData(this.self, recipient, group));
            byte[] ciphertext = cipher.doFinal(encode(kind, text.getBytes(StandardCharsets.UTF_8)));
            return ByteBuffer.allocate(NONCE_LENGTH + ciphertext.length).put(nonce).put(ciphertext).array();
        } catch (GeneralSecurityException e) {
            Osmosis.LOGGER.error("Could not encrypt a chat message", e);
            return null;
        }
    }

    /**
     * @return the message, or {@code null} if it cannot be decrypted, for example after a key change
     */
    public @Nullable Plaintext decrypt(UUID sender, UUID recipient, long group, byte[] nonce, byte[] ciphertext) {
        SecretKey key = this.sharedKey(sender.equals(this.self) ? recipient : sender);
        if (key == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, nonce));
            cipher.updateAAD(associatedData(sender, recipient, group));
            return decode(cipher.doFinal(ciphertext));
        } catch (GeneralSecurityException | DataFormatException e) {
            return null;
        }
    }

    private @Nullable SecretKey sharedKey(UUID friend) {
        SecretKey cached = this.sharedKeys.get(friend);
        if (cached != null) {
            return cached;
        }
        byte[] friendKey = this.friendKeys.get(friend);
        if (friendKey == null) {
            return null;
        }
        try {
            PublicKey publicKey = KeyFactory.getInstance("X25519").generatePublic(new X509EncodedKeySpec(friendKey));
            KeyAgreement agreement = KeyAgreement.getInstance("X25519");
            agreement.init(this.identity.getPrivate());
            agreement.doPhase(publicKey, true);
            byte[] secret = agreement.generateSecret();
            try {
                SecretKey key = KDF.getInstance("HKDF-SHA256").deriveKey("AES", HKDFParameterSpec.ofExtract()
                        .addIKM(secret)
                        .addSalt(this.salt(friend))
                        .thenExpand(KDF_INFO, 32));
                this.sharedKeys.put(friend, key);
                return key;
            } finally {
                Arrays.fill(secret, (byte) 0);
            }
        } catch (GeneralSecurityException e) {
            Osmosis.LOGGER.warn("Unusable chat key for {}", friend, e);
            return null;
        }
    }

    /** Both UUIDs in a fixed order, so both friends derive the same key. */
    private byte[] salt(UUID friend) {
        boolean selfFirst = this.self.compareTo(friend) < 0;
        return associatedData(selfFirst ? this.self : friend, selfFirst ? friend : this.self, 0);
    }

    /** Direct messages leave out the group, which keeps them compatible with messages sent before groups existed. */
    private static byte[] associatedData(UUID sender, UUID recipient, long group) {
        ByteBuffer buffer = ByteBuffer.allocate(group == 0 ? 32 : 40)
                .putLong(sender.getMostSignificantBits()).putLong(sender.getLeastSignificantBits())
                .putLong(recipient.getMostSignificantBits()).putLong(recipient.getLeastSignificantBits());
        if (group != 0) {
            buffer.putLong(group);
        }
        return buffer.array();
    }

    /**
     * Prefixes the data with a byte holding the kind and whether the rest is deflated, which it is unless that would
     * make it larger. Messages from before kinds existed start with 0 or 1, which reads as text.
     */
    private static byte[] encode(int kind, byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION, true);
        try {
            deflater.setInput(data);
            deflater.finish();
            byte[] buffer = new byte[data.length + 1];
            buffer[0] = (byte) (kind << 1 | DEFLATED);
            int length = deflater.deflate(buffer, 1, data.length);
            if (deflater.finished() && length < data.length) {
                return Arrays.copyOf(buffer, length + 1);
            }
        } finally {
            deflater.end();
        }
        byte[] plain = new byte[data.length + 1];
        plain[0] = (byte) (kind << 1);
        System.arraycopy(data, 0, plain, 1, data.length);
        return plain;
    }

    private static Plaintext decode(byte[] data) throws DataFormatException {
        if (data.length == 0) {
            throw new DataFormatException("Empty message");
        }
        int kind = (data[0] & 0xFF) >> 1;
        byte[] text = (data[0] & DEFLATED) != 0 ? inflate(data) : Arrays.copyOfRange(data, 1, data.length);
        return new Plaintext(kind, new String(text, StandardCharsets.UTF_8));
    }

    private static byte[] inflate(byte[] data) throws DataFormatException {
        Inflater inflater = new Inflater(true);
        try {
            inflater.setInput(data, 1, data.length - 1);
            ByteArrayOutputStream out = new ByteArrayOutputStream(data.length * 3);
            byte[] buffer = new byte[512];
            while (!inflater.finished()) {
                int length = inflater.inflate(buffer);
                if (length == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new DataFormatException("Truncated message");
                }
                out.write(buffer, 0, length);
                if (out.size() > MAX_PLAINTEXT_BYTES) {
                    throw new DataFormatException("Message too large");
                }
            }
            return out.toByteArray();
        } finally {
            inflater.end();
        }
    }
}
