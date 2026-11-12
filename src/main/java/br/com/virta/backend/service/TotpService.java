package br.com.virta.backend.service;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;

/**
 * TOTP (RFC 6238), the 6-digit code of authenticator apps (Google/Microsoft Authenticator).
 * App and server share a secret; both compute HMAC-SHA1(secret, current 30-second step)
 * and turn it into 6 digits, so they reach the same code without talking to each other.
 */
@Service
public class TotpService {

    private static final int PERIOD_SECONDS = 30;
    private static final int SECRET_BYTES = 20; // 160 bits, the size recommended for HMAC-SHA1
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private final SecureRandom random = new SecureRandom();
    private final Clock clock;

    public TotpService() {
        this(Clock.systemUTC());
    }

    /** Lets the tests freeze time. */
    TotpService(Clock clock) {
        this.clock = clock;
    }

    /** New random secret, in Base32 (the format authenticator apps read). */
    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    /** Content of the QR code: the app reads the secret, the account and the issuer from it. */
    public String otpauthUri(String secret, String email) {
        String label = URLEncoder.encode("Virta:" + email, StandardCharsets.UTF_8).replace("+", "%20");
        return "otpauth://totp/" + label + "?secret=" + secret
                + "&issuer=Virta&algorithm=SHA1&digits=6&period=" + PERIOD_SECONDS;
    }

    /** Accepts the current code and the neighbours (±30 s), in case the phone clock is a bit off. */
    public boolean verify(String secret, String code) {
        if (secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = base32Decode(secret);
        long step = clock.instant().getEpochSecond() / PERIOD_SECONDS;
        for (long offset = -1; offset <= 1; offset++) {
            byte[] expected = codeAt(key, step + offset).getBytes(StandardCharsets.UTF_8);
            // Constant-time comparison: the response time doesn't reveal how many digits matched.
            if (MessageDigest.isEqual(expected, code.getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    /** The 6-digit code for a 30-second step (RFC 4226 "dynamic truncation"). */
    String codeAt(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA1 is not available", e);
        }
    }

    static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder();
        int buffer = 0;
        int bits = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5) {
                out.append(BASE32.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) {
            out.append(BASE32.charAt((buffer << (5 - bits)) & 31));
        }
        return out.toString();
    }

    static byte[] base32Decode(String text) {
        String clean = text.replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bits = 0;
        for (char c : clean.toCharArray()) {
            int value = BASE32.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character: " + c);
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.write((buffer >> (bits - 8)) & 0xFF);
                bits -= 8;
            }
        }
        return out.toByteArray();
    }
}