package util;

import java.security.*;
import java.util.HexFormat;

/**
 * Salted SHA-256 password hashing (hex output).
 * A per-user random 8-byte salt defeats rainbow-table attacks.
 */
public final class PasswordUtil {
    private static final SecureRandom RNG = new SecureRandom();

    private PasswordUtil() {}

    public static String newSalt() {
        byte[] s = new byte[8];
        RNG.nextBytes(s);
        return HexFormat.of().formatHex(s);
    }

    public static String hash(String password, String saltHex) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(HexFormat.of().parseHex(saltHex));
            return HexFormat.of().formatHex(md.digest(password.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256 is guaranteed by the JVM
        }
    }

    public static boolean verify(String password, String saltHex, String expectedHash) {
        return MessageDigest.isEqual(
                hash(password, saltHex).getBytes(), expectedHash.getBytes());
    }
}
