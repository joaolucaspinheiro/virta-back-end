package br.com.virta.backend.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses the official test vectors of RFC 6238 (secret "12345678901234567890", SHA-1). */
class TotpServiceTest {

    private static final byte[] RFC_KEY = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String RFC_SECRET_BASE32 = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    void matchesTheRfc6238TestVectors() {
        TotpService totp = new TotpService();
        // RFC values have 8 digits; ours are the last 6.
        assertThat(totp.codeAt(RFC_KEY, 59 / 30)).isEqualTo("287082");          // 94287082
        assertThat(totp.codeAt(RFC_KEY, 1111111109L / 30)).isEqualTo("081804"); // 07081804
        assertThat(totp.codeAt(RFC_KEY, 1234567890L / 30)).isEqualTo("005924"); // 89005924
    }

    @Test
    void base32RoundTrip() {
        assertThat(TotpService.base32Encode(RFC_KEY)).isEqualTo(RFC_SECRET_BASE32);
        assertThat(TotpService.base32Decode(RFC_SECRET_BASE32)).isEqualTo(RFC_KEY);
    }

    @Test
    void acceptsCurrentAndNeighbourCodesButNotOlderOnes() {
        TotpService totp = at(1234567890L);
        assertThat(totp.verify(RFC_SECRET_BASE32, "005924")).isTrue();                              // now
        assertThat(totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, 1234567890L / 30 - 1))).isTrue(); // 30 s ago
        assertThat(totp.verify(RFC_SECRET_BASE32, totp.codeAt(RFC_KEY, 1234567890L / 30 - 2))).isFalse(); // 60 s ago
    }

    @Test
    void rejectsMalformedCodes() {
        TotpService totp = at(1234567890L);
        assertThat(totp.verify(RFC_SECRET_BASE32, "12345")).isFalse();
        assertThat(totp.verify(RFC_SECRET_BASE32, "abcdef")).isFalse();
        assertThat(totp.verify(RFC_SECRET_BASE32, null)).isFalse();
    }

    @Test
    void generatesA160BitSecret() {
        String secret = new TotpService().generateSecret();
        assertThat(secret).hasSize(32).matches("[A-Z2-7]+");
    }

    private static TotpService at(long epochSecond) {
        return new TotpService(Clock.fixed(Instant.ofEpochSecond(epochSecond), ZoneOffset.UTC));
    }
}